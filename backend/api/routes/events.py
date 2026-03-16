from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from database import EventRecord, get_db

router = APIRouter(prefix="/api/events", tags=["events"])


def _event_to_dict(e: EventRecord) -> dict:
    return {
        "id": e.id,
        "type": e.type,
        "title": e.title,
        "description": e.description,
        "lat": e.lat,
        "lon": e.lon,
        "magnitude": e.magnitude,
        "severity": e.severity,
        "occurred_at": e.occurred_at.isoformat() if e.occurred_at else None,
        "country": e.country,
    }


@router.get("")
async def list_events(
    type: Optional[str] = Query(None, description="Comma-separated: earthquake,fire,conflict"),
    days: int = Query(7, ge=1, le=30),
    min_severity: Optional[str] = Query(None, description="low|medium|high|critical"),
    db: Session = Depends(get_db),
):
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    query = db.query(EventRecord).filter(EventRecord.occurred_at >= cutoff)

    if type:
        types = [t.strip() for t in type.split(",")]
        query = query.filter(EventRecord.type.in_(types))

    if min_severity:
        severity_order = {"low": 0, "medium": 1, "high": 2, "critical": 3}
        min_val = severity_order.get(min_severity, 0)
        allowed = [s for s, v in severity_order.items() if v >= min_val]
        query = query.filter(EventRecord.severity.in_(allowed))

    events = query.order_by(EventRecord.occurred_at.desc()).limit(200).all()
    return [_event_to_dict(e) for e in events]


@router.get("/{event_id}")
async def get_event(event_id: str, db: Session = Depends(get_db)):
    event = db.query(EventRecord).filter(EventRecord.id == event_id).first()
    if not event:
        raise HTTPException(status_code=404, detail="Event not found")
    return _event_to_dict(event)
