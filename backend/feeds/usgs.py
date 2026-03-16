"""
USGS Earthquake data.
Free, no API key required.
Uses the GeoJSON summary feed: magnitude >= 4.5, past week.
"""
import hashlib
import logging
from datetime import datetime, timezone

import httpx

logger = logging.getLogger(__name__)

USGS_FEED = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_week.geojson"


def _magnitude_to_severity(mag: float) -> str:
    if mag >= 7.0:
        return "critical"
    if mag >= 6.0:
        return "high"
    if mag >= 5.0:
        return "medium"
    return "low"


def _make_id(usgs_id: str) -> str:
    return "usgs_" + hashlib.sha256(usgs_id.encode()).hexdigest()[:20]


async def fetch_earthquakes() -> list[dict]:
    try:
        async with httpx.AsyncClient(timeout=20.0) as client:
            resp = await client.get(USGS_FEED)
            resp.raise_for_status()
            data = resp.json()
    except Exception as exc:
        logger.warning("USGS fetch failed: %s", exc)
        return []

    events = []
    for feature in data.get("features", []):
        props = feature.get("properties", {})
        geom = feature.get("geometry", {})
        coords = geom.get("coordinates", [None, None, None])

        mag = props.get("mag")
        if mag is None:
            continue

        usgs_id = feature.get("id", "")
        title = props.get("title", f"M{mag} earthquake")
        place = props.get("place", "")

        lon = coords[0]
        lat = coords[1]

        ts_ms = props.get("time", 0)
        occurred_at = datetime.fromtimestamp(ts_ms / 1000, tz=timezone.utc) if ts_ms else datetime.now(timezone.utc)

        events.append({
            "id": _make_id(usgs_id),
            "type": "earthquake",
            "title": title,
            "description": f"Depth: {coords[2]:.1f} km" if coords[2] else None,
            "lat": lat,
            "lon": lon,
            "magnitude": float(mag),
            "severity": _magnitude_to_severity(float(mag)),
            "occurred_at": occurred_at,
            "country": None,  # geo-resolve separately if needed
        })

    logger.info("USGS returned %d earthquakes", len(events))
    return events
