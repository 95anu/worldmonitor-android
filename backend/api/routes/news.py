import json
from typing import Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy import and_, or_, text
from sqlalchemy.orm import Session

from database import Article, get_db

router = APIRouter(prefix="/api/news", tags=["news"])


def _article_to_dict(a: Article) -> dict:
    return {
        "id": a.id,
        "title": a.title,
        "summary": a.summary,
        "url": a.url,
        "source_name": a.source_name,
        "source_country": a.source_country,
        "image_url": a.image_url,
        "published_at": a.published_at.isoformat() if a.published_at else None,
        "fetched_at": a.fetched_at.isoformat() if a.fetched_at else None,
        "countries": a.get_countries(),
        "categories": a.get_categories(),
        "importance_score": a.importance_score,
    }


@router.get("")
async def list_news(
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=100),
    country: Optional[str] = None,
    category: Optional[str] = None,
    q: Optional[str] = None,
    db: Session = Depends(get_db),
):
    query = db.query(Article)

    if country:
        # Filter articles that mention this country
        code = country.upper()[:2]
        query = query.filter(Article.countries.like(f'%"{code}"%'))

    if category:
        query = query.filter(Article.categories.like(f'%"{category}"%'))

    if q:
        search = f"%{q}%"
        query = query.filter(
            or_(Article.title.like(search), Article.summary.like(search))
        )

    total = query.count()
    items = (
        query.order_by(Article.importance_score.desc(), Article.fetched_at.desc())
        .offset((page - 1) * limit)
        .limit(limit)
        .all()
    )

    return {
        "items": [_article_to_dict(a) for a in items],
        "total": total,
        "page": page,
        "limit": limit,
        "pages": (total + limit - 1) // limit,
    }


@router.get("/{article_id}")
async def get_article(article_id: str, db: Session = Depends(get_db)):
    article = db.query(Article).filter(Article.id == article_id).first()
    if not article:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Article not found")
    return _article_to_dict(article)
