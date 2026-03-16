"""
GDELT 2.0 Doc API fetcher.
Free, no API key required.
Returns conflict/unrest events from the last 24h.
"""
import hashlib
import logging
from datetime import datetime, timezone

import httpx

logger = logging.getLogger(__name__)

GDELT_API = "https://api.gdeltproject.org/api/v2/doc/doc"

# Themes associated with conflict/unrest
CONFLICT_THEMES = "CRISISLEX_CRISISLEXREC,TAX_FNCACT_MILITARY,UNGP_PEACE_SECURITY"

_SEVERITY_MAP = {
    # Goldstein scale: -10 (most conflictual) to +10 (most cooperative)
    lambda g: g <= -7: "critical",
    lambda g: g <= -4: "high",
    lambda g: g <= -1: "medium",
    lambda g: g < 0: "low",
}


def _goldstein_to_severity(goldstein: float) -> str:
    if goldstein <= -7:
        return "critical"
    if goldstein <= -4:
        return "high"
    if goldstein <= -1:
        return "medium"
    return "low"


def _make_id(url: str) -> str:
    return "gdelt_" + hashlib.sha256(url.encode()).hexdigest()[:20]


async def fetch_gdelt_events(hours_back: int = 24) -> list[dict]:
    params = {
        "query": "conflict OR war OR attack OR military OR protest OR unrest",
        "mode": "artlist",
        "maxrecords": 50,
        "timespan": f"{hours_back}h",
        "sourcelang": "English",
        "format": "json",
    }
    try:
        async with httpx.AsyncClient(timeout=20.0) as client:
            resp = await client.get(GDELT_API, params=params)
            resp.raise_for_status()
            data = resp.json()
    except Exception as exc:
        logger.warning("GDELT fetch failed: %s", exc)
        return []

    events = []
    for article in data.get("articles", []):
        url = article.get("url", "")
        title = article.get("title", "")
        if not url or not title:
            continue

        # GDELT articles don't always have coordinates; skip those without
        seendate = article.get("seendate", "")
        try:
            occurred_at = datetime.strptime(seendate, "%Y%m%dT%H%M%SZ").replace(tzinfo=timezone.utc)
        except Exception:
            occurred_at = datetime.now(timezone.utc)

        # Use GDELT tone as proxy for severity
        tone = article.get("tone", 0.0)
        try:
            tone_val = float(tone)
        except (TypeError, ValueError):
            tone_val = 0.0

        # Negative tone = conflict/negative news
        if tone_val < -2:
            severity = "critical" if tone_val < -10 else "high" if tone_val < -5 else "medium"
        else:
            severity = "low"

        country = article.get("sourcecountry", "")[:2].upper() or None

        events.append({
            "id": _make_id(url),
            "type": "conflict",
            "title": title[:200],
            "description": article.get("socialimage", None),  # reuse field
            "lat": None,
            "lon": None,
            "magnitude": None,
            "severity": severity,
            "occurred_at": occurred_at,
            "country": country or None,
        })

    logger.info("GDELT returned %d conflict items", len(events))
    return events
