import time
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from database import get_db
from processing.scorer import compute_country_stats, compute_heatmap_scores

router = APIRouter(prefix="/api/heatmap", tags=["heatmap"])

# Simple in-memory cache
_cache: dict[str, Any] = {}
_CACHE_TTL = 60  # seconds


def _is_fresh(key: str) -> bool:
    if key not in _cache:
        return False
    return time.time() - _cache[key]["ts"] < _CACHE_TTL


@router.get("")
async def get_heatmap(db: Session = Depends(get_db)):
    if _is_fresh("heatmap"):
        return _cache["heatmap"]["data"]

    scores = compute_heatmap_scores(db)
    result = {
        "scores": scores,
        "updated_at": datetime.now(timezone.utc).isoformat(),
        "country_count": len(scores),
    }
    _cache["heatmap"] = {"ts": time.time(), "data": result}
    return result


@router.get("/{country_code}")
async def get_country_heatmap(country_code: str, db: Session = Depends(get_db)):
    code = country_code.upper()[:2]
    cache_key = f"country_{code}"
    if _is_fresh(cache_key):
        return _cache[cache_key]["data"]

    stats = compute_country_stats(db, code)
    _cache[cache_key] = {"ts": time.time(), "data": stats}
    return stats


def invalidate_cache() -> None:
    """Called by scheduler after recompute."""
    _cache.clear()
