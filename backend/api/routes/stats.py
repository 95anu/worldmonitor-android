import json
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from database import get_db

router = APIRouter(prefix="/api", tags=["stats"])


@router.get("/stats")
async def get_stats(db: Session = Depends(get_db)):
    now = datetime.now(timezone.utc)
    cutoff_24h = now - timedelta(hours=24)
    cutoff_7d = now - timedelta(days=7)

    total_24h = db.execute(
        text("SELECT COUNT(*) FROM articles WHERE fetched_at >= :c"),
        {"c": cutoff_24h},
    ).scalar() or 0

    sources_active = db.execute(
        text("SELECT COUNT(DISTINCT source_name) FROM articles WHERE fetched_at >= :c"),
        {"c": cutoff_24h},
    ).scalar() or 0

    earthquakes_7d = db.execute(
        text("SELECT COUNT(*) FROM events WHERE type='earthquake' AND occurred_at >= :c"),
        {"c": cutoff_7d},
    ).scalar() or 0

    fires_24h = db.execute(
        text("SELECT COUNT(*) FROM events WHERE type='fire' AND occurred_at >= :c"),
        {"c": cutoff_24h},
    ).scalar() or 0

    # Top 5 countries by article count in last 24h
    rows = db.execute(
        text("SELECT countries FROM articles WHERE fetched_at >= :c"),
        {"c": cutoff_24h},
    ).fetchall()

    country_counts: dict[str, int] = {}
    for (countries_json,) in rows:
        for code in json.loads(countries_json or "[]"):
            country_counts[code] = country_counts.get(code, 0) + 1

    top_countries = [
        {"code": k, "article_count": v}
        for k, v in sorted(country_counts.items(), key=lambda x: x[1], reverse=True)[:10]
    ]

    last_fetched = db.execute(
        text("SELECT MAX(fetched_at) FROM articles")
    ).scalar()

    # SQLite returns datetime as string; handle both str and datetime
    if last_fetched is None:
        last_updated = now.isoformat()
    elif hasattr(last_fetched, "isoformat"):
        last_updated = last_fetched.isoformat()
    else:
        last_updated = str(last_fetched)

    return {
        "total_articles_24h": total_24h,
        "sources_active": sources_active,
        "top_countries": top_countries,
        "earthquakes_7d": earthquakes_7d,
        "fires_24h": fires_24h,
        "last_updated": last_updated,
    }


@router.get("/health")
async def health_check():
    return {"status": "ok", "timestamp": datetime.now(timezone.utc).isoformat()}
