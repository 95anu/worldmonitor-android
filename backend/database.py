import json
from datetime import datetime
from typing import Generator

from sqlalchemy import (
    Column, DateTime, Float, Integer, String, Text, create_engine, event
)
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from config import settings

engine = create_engine(
    settings.db_url,
    connect_args={"check_same_thread": False},
)

# Enable WAL mode for better concurrent read performance
@event.listens_for(engine, "connect")
def set_sqlite_pragma(dbapi_conn, _):
    cursor = dbapi_conn.cursor()
    cursor.execute("PRAGMA journal_mode=WAL")
    cursor.execute("PRAGMA synchronous=NORMAL")
    cursor.close()

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


class Article(Base):
    __tablename__ = "articles"

    id = Column(String, primary_key=True)
    title = Column(Text, nullable=False)
    summary = Column(Text)
    url = Column(Text, nullable=False, unique=True)
    source_name = Column(String)
    source_country = Column(String(2))
    image_url = Column(Text)
    published_at = Column(DateTime)
    fetched_at = Column(DateTime, default=datetime.utcnow)
    # JSON arrays stored as text
    countries = Column(Text, default="[]")
    categories = Column(Text, default="[]")
    importance_score = Column(Float, default=0.5)

    def get_countries(self) -> list[str]:
        return json.loads(self.countries or "[]")

    def get_categories(self) -> list[str]:
        return json.loads(self.categories or "[]")


class EventRecord(Base):
    __tablename__ = "events"

    id = Column(String, primary_key=True)
    type = Column(String, nullable=False)  # earthquake | fire | conflict
    title = Column(Text, nullable=False)
    description = Column(Text)
    lat = Column(Float)
    lon = Column(Float)
    magnitude = Column(Float)
    severity = Column(String)  # low | medium | high | critical
    occurred_at = Column(DateTime)
    country = Column(String(2))
    fetched_at = Column(DateTime, default=datetime.utcnow)


class FeedStatus(Base):
    __tablename__ = "feed_status"

    url = Column(Text, primary_key=True)
    name = Column(String)
    last_fetched = Column(DateTime)
    last_success = Column(DateTime)
    error_count = Column(Integer, default=0)


def init_db() -> None:
    Base.metadata.create_all(bind=engine)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
