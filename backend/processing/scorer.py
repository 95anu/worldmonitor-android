"""
Compute per-country activity scores for the heatmap.
Score 0.0–1.0, updated every 5 minutes.
"""
import json
import logging
import math
from datetime import datetime, timedelta, timezone

from sqlalchemy import text
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)

HALF_LIFE_HOURS = 6.0  # exponential decay
EVENT_WEIGHTS = {"earthquake": 1.5, "fire": 0.8, "conflict": 1.2}
SEVERITY_MULTIPLIERS = {"critical": 3.0, "high": 2.0, "medium": 1.2, "low": 0.7}


def _decay_weight(published_at: datetime) -> float:
    """Exponential decay: weight = 2^(-age_hours / HALF_LIFE_HOURS)."""
    now = datetime.now(timezone.utc)
    if published_at.tzinfo is None:
        published_at = published_at.replace(tzinfo=timezone.utc)
    age_hours = max(0.0, (now - published_at).total_seconds() / 3600)
    return math.pow(2.0, -age_hours / HALF_LIFE_HOURS)


def compute_heatmap_scores(db: Session) -> dict[str, float]:
    """Return dict of ISO alpha-2 → normalized score (0.0–1.0)."""
    cutoff = datetime.now(timezone.utc) - timedelta(hours=48)

    # ── Articles contribution ─────────────────────────────────────────────
    rows = db.execute(
        text(
            "SELECT countries, importance_score, published_at "
            "FROM articles "
            "WHERE fetched_at >= :cutoff"
        ),
        {"cutoff": cutoff},
    ).fetchall()

    country_raw: dict[str, float] = {}
    for row in rows:
        countries = json.loads(row[0] or "[]")
        importance = row[1] or 0.5
        published_at = row[2]
        if isinstance(published_at, str):
            try:
                from dateutil import parser as dp
                published_at = dp.parse(published_at)
            except Exception:
                published_at = datetime.now(timezone.utc)

        w = _decay_weight(published_at) * importance
        for code in countries:
            country_raw[code] = country_raw.get(code, 0.0) + w

    # ── Events contribution ───────────────────────────────────────────────
    event_cutoff = datetime.now(timezone.utc) - timedelta(days=7)
    event_rows = db.execute(
        text(
            "SELECT country, type, severity, occurred_at "
            "FROM events "
            "WHERE occurred_at >= :cutoff AND country IS NOT NULL"
        ),
        {"cutoff": event_cutoff},
    ).fetchall()

    for row in event_rows:
        code, etype, severity, occurred_at = row
        if not code:
            continue
        if isinstance(occurred_at, str):
            try:
                from dateutil import parser as dp
                occurred_at = dp.parse(occurred_at)
            except Exception:
                occurred_at = datetime.now(timezone.utc)

        base_weight = EVENT_WEIGHTS.get(etype, 1.0)
        sev_mult = SEVERITY_MULTIPLIERS.get(severity or "low", 0.7)
        w = _decay_weight(occurred_at) * base_weight * sev_mult
        country_raw[code] = country_raw.get(code, 0.0) + w

    if not country_raw:
        return {}

    # ── Log-normalize to 0–1 ─────────────────────────────────────────────
    log_vals = {k: math.log1p(v) for k, v in country_raw.items()}
    max_val = max(log_vals.values(), default=1.0)
    if max_val == 0:
        return {k: 0.0 for k in log_vals}

    normalized = {k: round(v / max_val, 4) for k, v in log_vals.items()}
    return normalized


def compute_country_stats(db: Session, country_code: str) -> dict:
    """Detailed stats for a single country."""
    cutoff_24h = datetime.now(timezone.utc) - timedelta(hours=24)
    cutoff_7d = datetime.now(timezone.utc) - timedelta(days=7)

    articles_24h = db.execute(
        text(
            "SELECT COUNT(*) FROM articles "
            "WHERE countries LIKE :pattern AND fetched_at >= :cutoff"
        ),
        {"pattern": f'%"{country_code}"%', "cutoff": cutoff_24h},
    ).scalar() or 0

    events_7d = db.execute(
        text(
            "SELECT COUNT(*) FROM events "
            "WHERE country = :code AND occurred_at >= :cutoff"
        ),
        {"code": country_code, "cutoff": cutoff_7d},
    ).scalar() or 0

    all_scores = compute_heatmap_scores(db)
    score = all_scores.get(country_code, 0.0)

    return {
        "country_code": country_code,
        "score": score,
        "articles_24h": articles_24h,
        "events_7d": events_7d,
    }
