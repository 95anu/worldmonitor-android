"""
NASA FIRMS wildfire data.
Requires a free API key from: https://firms.modaps.eosdis.nasa.gov/api/
If key is not set, returns empty list.
Clusters nearby fire points into single events.
"""
import hashlib
import logging
import math
from datetime import datetime, timezone

import httpx

logger = logging.getLogger(__name__)

FIRMS_BASE = "https://firms.modaps.eosdis.nasa.gov/api/area/csv"
CLUSTER_RADIUS_DEG = 0.5  # ~55 km at equator


def _make_id(lat: float, lon: float, date: str) -> str:
    key = f"{lat:.2f}_{lon:.2f}_{date}"
    return "fire_" + hashlib.sha256(key.encode()).hexdigest()[:20]


def _frp_to_severity(frp: float) -> str:
    """Fire Radiative Power (MW) → severity."""
    if frp >= 1000:
        return "critical"
    if frp >= 300:
        return "high"
    if frp >= 50:
        return "medium"
    return "low"


def _cluster_fires(fires: list[dict]) -> list[dict]:
    """Simple greedy clustering — merge fires within CLUSTER_RADIUS_DEG."""
    clusters: list[dict] = []
    for fire in fires:
        merged = False
        for cluster in clusters:
            dlat = abs(fire["lat"] - cluster["lat"])
            dlon = abs(fire["lon"] - cluster["lon"])
            if dlat < CLUSTER_RADIUS_DEG and dlon < CLUSTER_RADIUS_DEG:
                # Update cluster centroid and max FRP
                cluster["count"] = cluster.get("count", 1) + 1
                cluster["frp"] = max(cluster["frp"], fire["frp"])
                merged = True
                break
        if not merged:
            clusters.append({**fire, "count": 1})
    return clusters


async def fetch_fires(api_key: str, days: int = 1) -> list[dict]:
    if not api_key:
        return []

    url = f"{FIRMS_BASE}/{api_key}/VIIRS_SNPP_NRT/world/{days}"
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            text = resp.text
    except Exception as exc:
        logger.warning("NASA FIRMS fetch failed: %s", exc)
        return []

    raw_fires: list[dict] = []
    lines = text.strip().splitlines()
    if len(lines) < 2:
        return []

    # CSV header: latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
    headers = [h.strip() for h in lines[0].split(",")]
    for line in lines[1:]:
        parts = line.split(",")
        if len(parts) < len(headers):
            continue
        row = dict(zip(headers, parts))
        try:
            lat = float(row.get("latitude", 0))
            lon = float(row.get("longitude", 0))
            frp = float(row.get("frp", 0))
            confidence = row.get("confidence", "n").strip().lower()
            acq_date = row.get("acq_date", "").strip()

            # Only use high-confidence detections
            if confidence not in ("high", "h", "nominal", "n"):
                if frp < 100:  # accept low confidence if high FRP
                    continue

            raw_fires.append({"lat": lat, "lon": lon, "frp": frp, "date": acq_date})
        except (ValueError, KeyError):
            continue

    clusters = _cluster_fires(raw_fires)

    events = []
    for c in clusters:
        count = c.get("count", 1)
        frp = c["frp"]
        events.append({
            "id": _make_id(c["lat"], c["lon"], c.get("date", "")),
            "type": "fire",
            "title": f"Wildfire cluster ({count} hotspot{'s' if count > 1 else ''})",
            "description": f"FRP: {frp:.0f} MW",
            "lat": c["lat"],
            "lon": c["lon"],
            "magnitude": frp,
            "severity": _frp_to_severity(frp),
            "occurred_at": datetime.now(timezone.utc),
            "country": None,
        })

    logger.info("NASA FIRMS returned %d fire clusters (from %d raw points)", len(events), len(raw_fires))
    return events
