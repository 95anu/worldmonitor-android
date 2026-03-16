"""
WorldMonitor Backend — FastAPI application entry point.
Run with: uvicorn main:app --host 0.0.0.0 --port 8000
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from database import init_db

# Configure logging
logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("WorldMonitor backend starting...")
    init_db()
    logger.info("Database initialized at %s", settings.DB_PATH)

    from scheduler import run_initial_fetch, start_scheduler
    start_scheduler()
    await run_initial_fetch()

    logger.info("Backend ready. Listening on %s:%d", settings.SERVER_HOST, settings.SERVER_PORT)
    yield

    # Shutdown
    from scheduler import scheduler
    scheduler.shutdown(wait=False)
    logger.info("Scheduler stopped. Goodbye.")


app = FastAPI(
    title="WorldMonitor API",
    description="Global news & events aggregation for the WorldMonitor Android app",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # local network — allow all
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

# ── Routers ──────────────────────────────────────────────────────────────────
from api.routes import events, heatmap, news, stats, ws

app.include_router(heatmap.router)
app.include_router(news.router)
app.include_router(events.router)
app.include_router(stats.router)
app.include_router(ws.router)


@app.get("/")
async def root():
    return {
        "name": "WorldMonitor API",
        "version": "1.0.0",
        "docs": "/docs",
        "health": "/api/health",
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host=settings.SERVER_HOST,
        port=settings.SERVER_PORT,
        log_level=settings.LOG_LEVEL.lower(),
        reload=False,
    )
