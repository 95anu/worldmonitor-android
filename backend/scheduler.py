"""
APScheduler background jobs.
Manages periodic fetching and score computation.
"""
import asyncio
import json
import logging
from datetime import datetime, timedelta, timezone

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy.orm import Session

from config import settings
from database import Article, EventRecord, FeedStatus, SessionLocal
from feeds.gdelt import fetch_gdelt_events
from feeds.nasa_firms import fetch_fires
from feeds.rss_fetcher import fetch_all_feeds
from feeds.usgs import fetch_earthquakes
from processing.geo_mapper import extract_countries
from processing.importance import score_article
from processing.scorer import compute_heatmap_scores

logger = logging.getLogger(__name__)
scheduler = AsyncIOScheduler(timezone="UTC")


# ── RSS Feed Job ─────────────────────────────────────────────────────────────

async def job_fetch_feeds() -> None:
    logger.info("Starting RSS feed fetch...")
    articles = await fetch_all_feeds()
    db: Session = SessionLocal()
    new_count = 0
    breaking: list[dict] = []
    try:
        for a in articles:
            if db.query(Article).filter(Article.id == a["id"]).first():
                continue  # already stored

            # Geo-map: check feed source country + extract from title+summary
            text_to_scan = (a.get("title") or "") + " " + (a.get("summary") or "")
            countries = extract_countries(text_to_scan)
            if not countries and a.get("source_country"):
                countries = [a["source_country"]]

            importance = score_article(
                a.get("title", ""),
                a.get("source_name", ""),
                a.get("reliability_weight"),
            )

            article = Article(
                id=a["id"],
                title=a["title"],
                summary=a.get("summary"),
                url=a["url"],
                source_name=a.get("source_name"),
                source_country=a.get("source_country"),
                image_url=a.get("image_url"),
                published_at=a.get("published_at"),
                countries=json.dumps(countries),
                categories=json.dumps(a.get("categories", [])),
                importance_score=importance,
            )
            db.add(article)
            new_count += 1

            if importance >= 0.80:
                breaking.append({
                    "id": a["id"],
                    "title": a["title"],
                    "url": a["url"],
                    "source_name": a.get("source_name"),
                    "importance_score": importance,
                    "countries": countries,
                    "published_at": a["published_at"].isoformat() if a.get("published_at") else None,
                })

        db.commit()
        logger.info("Saved %d new articles", new_count)
    except Exception as exc:
        db.rollback()
        logger.error("Feed save error: %s", exc)
    finally:
        db.close()

    # Push breaking news via WebSocket
    if breaking:
        from api.routes.ws import broadcast_breaking_news
        for art in breaking[:5]:  # limit broadcast burst
            await broadcast_breaking_news(art)


# ── Earthquake Job ────────────────────────────────────────────────────────────

async def job_fetch_earthquakes() -> None:
    logger.info("Fetching earthquakes...")
    events = await fetch_earthquakes()
    _upsert_events(events)


# ── GDELT Job ─────────────────────────────────────────────────────────────────

async def job_fetch_gdelt() -> None:
    logger.info("Fetching GDELT events...")
    events = await fetch_gdelt_events()
    _upsert_events(events)


# ── Fire Job ──────────────────────────────────────────────────────────────────

async def job_fetch_fires() -> None:
    if not settings.firms_enabled:
        return
    logger.info("Fetching NASA FIRMS fire data...")
    events = await fetch_fires(settings.NASA_FIRMS_API_KEY)
    _upsert_events(events)


# ── Score Computation Job ─────────────────────────────────────────────────────

async def job_compute_scores() -> None:
    db: Session = SessionLocal()
    try:
        scores = compute_heatmap_scores(db)
        # Invalidate heatmap cache
        from api.routes.heatmap import invalidate_cache
        invalidate_cache()
        # Broadcast to WS clients
        from api.routes.ws import broadcast_heatmap_update
        await broadcast_heatmap_update(scores)
    finally:
        db.close()


# ── Cleanup Job ───────────────────────────────────────────────────────────────

async def job_cleanup() -> None:
    cutoff = datetime.now(timezone.utc) - timedelta(days=settings.MAX_ARTICLES_AGE_DAYS)
    db: Session = SessionLocal()
    try:
        deleted = db.query(Article).filter(Article.fetched_at < cutoff).delete()
        db.commit()
        logger.info("Cleaned up %d old articles", deleted)
    except Exception as exc:
        db.rollback()
        logger.error("Cleanup error: %s", exc)
    finally:
        db.close()


# ── Helpers ───────────────────────────────────────────────────────────────────

def _upsert_events(events: list[dict]) -> None:
    db: Session = SessionLocal()
    try:
        for e in events:
            if db.query(EventRecord).filter(EventRecord.id == e["id"]).first():
                continue
            record = EventRecord(
                id=e["id"],
                type=e["type"],
                title=e["title"],
                description=e.get("description"),
                lat=e.get("lat"),
                lon=e.get("lon"),
                magnitude=e.get("magnitude"),
                severity=e.get("severity"),
                occurred_at=e.get("occurred_at"),
                country=e.get("country"),
            )
            db.add(record)
        db.commit()
    except Exception as exc:
        db.rollback()
        logger.error("Event upsert error: %s", exc)
    finally:
        db.close()


def start_scheduler() -> None:
    interval = settings.REFRESH_INTERVAL_MINUTES

    scheduler.add_job(job_fetch_feeds, "interval", minutes=interval, id="feeds", replace_existing=True)
    scheduler.add_job(job_fetch_earthquakes, "interval", minutes=30, id="earthquakes", replace_existing=True)
    scheduler.add_job(job_fetch_gdelt, "interval", minutes=30, id="gdelt", replace_existing=True)
    scheduler.add_job(job_fetch_fires, "interval", minutes=60, id="fires", replace_existing=True)
    scheduler.add_job(job_compute_scores, "interval", minutes=5, id="scores", replace_existing=True)
    scheduler.add_job(job_cleanup, "cron", hour=3, minute=0, id="cleanup", replace_existing=True)

    scheduler.start()
    logger.info("Scheduler started (feed refresh every %d min)", interval)


async def run_initial_fetch() -> None:
    """Run first fetch immediately on startup."""
    logger.info("Running initial data fetch...")
    await asyncio.gather(
        job_fetch_feeds(),
        job_fetch_earthquakes(),
        job_fetch_gdelt(),
        return_exceptions=True,
    )
    await job_compute_scores()
