"""
RSS feed fetcher. Fetches and parses ~50 curated feeds.
Handles timeouts, SSL errors, and malformed feeds gracefully.
"""
import asyncio
import hashlib
import logging
from datetime import datetime, timezone
from typing import Any

import feedparser
import httpx
from dateutil import parser as dateparser

logger = logging.getLogger(__name__)

# ── Feed registry ────────────────────────────────────────────────────────────
# reliability_weight 0.0–1.0 affects importance scoring
RSS_FEEDS: list[dict[str, Any]] = [
    # Global / Wire services
    {"url": "http://feeds.bbci.co.uk/news/world/rss.xml", "name": "BBC World News", "country": "GB", "category": "general", "reliability_weight": 0.95},
    {"url": "https://feeds.reuters.com/reuters/worldNews", "name": "Reuters World", "country": "US", "category": "general", "reliability_weight": 0.95},
    {"url": "https://feeds.npr.org/1004/rss.xml", "name": "NPR World", "country": "US", "category": "general", "reliability_weight": 0.90},
    {"url": "https://rss.dw.com/rdf/rss-en-all", "name": "Deutsche Welle", "country": "DE", "category": "general", "reliability_weight": 0.90},
    {"url": "https://www.aljazeera.com/xml/rss/all.xml", "name": "Al Jazeera", "country": "QA", "category": "general", "reliability_weight": 0.85},
    {"url": "https://www.france24.com/en/rss", "name": "France 24", "country": "FR", "category": "general", "reliability_weight": 0.85},
    {"url": "https://www3.nhk.or.jp/nhkworld/en/news/feeds/rss.xml", "name": "NHK World", "country": "JP", "category": "general", "reliability_weight": 0.85},
    {"url": "https://feeds.feedburner.com/euronews/en/news", "name": "Euronews", "country": "FR", "category": "general", "reliability_weight": 0.80},
    {"url": "https://rss.cnn.com/rss/edition_world.rss", "name": "CNN World", "country": "US", "category": "general", "reliability_weight": 0.80},
    {"url": "https://feeds.feedburner.com/time/world", "name": "TIME World", "country": "US", "category": "general", "reliability_weight": 0.75},

    # Geopolitics / Policy
    {"url": "https://foreignpolicy.com/feed/", "name": "Foreign Policy", "country": "US", "category": "geopolitics", "reliability_weight": 0.90},
    {"url": "https://www.chathamhouse.org/rss.xml", "name": "Chatham House", "country": "GB", "category": "geopolitics", "reliability_weight": 0.90},
    {"url": "https://www.rand.org/pubs/feed.xml", "name": "RAND Corporation", "country": "US", "category": "geopolitics", "reliability_weight": 0.85},
    {"url": "https://www.cfr.org/rss.xml", "name": "Council on Foreign Relations", "country": "US", "category": "geopolitics", "reliability_weight": 0.85},
    {"url": "https://thehill.com/homenews/international/feed/", "name": "The Hill International", "country": "US", "category": "geopolitics", "reliability_weight": 0.70},

    # Conflict / Security / Defense
    {"url": "https://www.bellingcat.com/feed/", "name": "Bellingcat", "country": "GB", "category": "conflict", "reliability_weight": 0.85},
    {"url": "https://thedefensepost.com/feed/", "name": "The Defense Post", "country": "US", "category": "conflict", "reliability_weight": 0.80},
    {"url": "https://www.defensenews.com/arc/outboundfeeds/rss/?outputType=xml", "name": "Defense News", "country": "US", "category": "conflict", "reliability_weight": 0.80},
    {"url": "https://breakingdefense.com/feed/", "name": "Breaking Defense", "country": "US", "category": "conflict", "reliability_weight": 0.75},
    {"url": "https://www.thedrive.com/the-war-zone/rss", "name": "The War Zone", "country": "US", "category": "conflict", "reliability_weight": 0.75},
    {"url": "https://www.militarytimes.com/rss/news/", "name": "Military Times", "country": "US", "category": "conflict", "reliability_weight": 0.75},

    # Economics / Finance
    {"url": "https://feeds.ft.com/ft-world-economy", "name": "Financial Times Economy", "country": "GB", "category": "economics", "reliability_weight": 0.90},
    {"url": "https://feeds.bloomberg.com/markets/news.rss", "name": "Bloomberg Markets", "country": "US", "category": "economics", "reliability_weight": 0.90},
    {"url": "https://feeds.a.dj.com/rss/RSSWorldNews.xml", "name": "WSJ World", "country": "US", "category": "economics", "reliability_weight": 0.85},
    {"url": "https://www.imf.org/en/News/RSS", "name": "IMF News", "country": "US", "category": "economics", "reliability_weight": 0.85},

    # Asia-Pacific
    {"url": "https://timesofindia.indiatimes.com/rssfeeds/296589292.cms", "name": "Times of India World", "country": "IN", "category": "general", "reliability_weight": 0.75},
    {"url": "https://www.scmp.com/rss/91/feed", "name": "South China Morning Post", "country": "HK", "category": "general", "reliability_weight": 0.80},
    {"url": "https://www.thejakartapost.com/feed", "name": "Jakarta Post", "country": "ID", "category": "general", "reliability_weight": 0.70},
    {"url": "https://www.koreatimes.co.kr/www/rss/rss.xml", "name": "Korea Times", "country": "KR", "category": "general", "reliability_weight": 0.70},
    {"url": "https://asiancorrespondent.com/feed/", "name": "Asian Correspondent", "country": "SG", "category": "general", "reliability_weight": 0.70},

    # Middle East
    {"url": "https://www.middleeasteye.net/rss", "name": "Middle East Eye", "country": "GB", "category": "geopolitics", "reliability_weight": 0.75},
    {"url": "https://www.haaretz.com/cmlink/1.628765", "name": "Haaretz", "country": "IL", "category": "general", "reliability_weight": 0.75},
    {"url": "https://english.alarabiya.net/tools/rss", "name": "Al Arabiya English", "country": "AE", "category": "general", "reliability_weight": 0.70},

    # Africa
    {"url": "https://www.africanews.com/feed/", "name": "Africanews", "country": "FR", "category": "general", "reliability_weight": 0.75},
    {"url": "https://allafrica.com/tools/headlines/rdf/latest/headlines.rdf", "name": "AllAfrica", "country": "ZA", "category": "general", "reliability_weight": 0.65},

    # Latin America
    {"url": "https://www.mercopress.com/rss", "name": "MercoPress", "country": "UY", "category": "general", "reliability_weight": 0.70},
    {"url": "https://rss.eluniversal.com.mx/noticias-rss.xml", "name": "El Universal (México)", "country": "MX", "category": "general", "reliability_weight": 0.65},

    # Russia / Eastern Europe
    {"url": "https://meduza.io/rss/en/all", "name": "Meduza", "country": "LV", "category": "general", "reliability_weight": 0.80},
    {"url": "https://kyivindependent.com/feed/", "name": "Kyiv Independent", "country": "UA", "category": "general", "reliability_weight": 0.80},

    # Environment / Climate
    {"url": "https://www.climatechangenews.com/feed/", "name": "Climate Change News", "country": "GB", "category": "environment", "reliability_weight": 0.80},
    {"url": "https://www.carbonbrief.org/feed/", "name": "Carbon Brief", "country": "GB", "category": "environment", "reliability_weight": 0.85},

    # Technology
    {"url": "https://feeds.arstechnica.com/arstechnica/index", "name": "Ars Technica", "country": "US", "category": "technology", "reliability_weight": 0.85},
    {"url": "https://www.theregister.com/headlines.atom", "name": "The Register", "country": "GB", "category": "technology", "reliability_weight": 0.80},

    # Health
    {"url": "https://www.who.int/rss-feeds/news-english.xml", "name": "WHO News", "country": "CH", "category": "health", "reliability_weight": 0.90},
    {"url": "https://www.statnews.com/feed/", "name": "STAT News", "country": "US", "category": "health", "reliability_weight": 0.80},

    # Human Rights / NGO
    {"url": "https://www.hrw.org/rss-feed/news-publication", "name": "Human Rights Watch", "country": "US", "category": "geopolitics", "reliability_weight": 0.80},
    {"url": "https://www.amnesty.org/en/feed/", "name": "Amnesty International", "country": "GB", "category": "geopolitics", "reliability_weight": 0.80},

    # Nuclear / WMD
    {"url": "https://www.armscontrol.org/rss.xml", "name": "Arms Control Association", "country": "US", "category": "conflict", "reliability_weight": 0.85},

    # Cyber
    {"url": "https://feeds.feedburner.com/TheHackersNews", "name": "The Hacker News", "country": "IN", "category": "technology", "reliability_weight": 0.75},
]

_SEMAPHORE_SIZE = 5  # max concurrent feed fetches


def _make_id(url: str) -> str:
    return hashlib.sha256(url.encode()).hexdigest()[:24]


def _parse_date(entry: Any) -> datetime | None:
    for attr in ("published_parsed", "updated_parsed"):
        t = getattr(entry, attr, None)
        if t:
            import time
            return datetime(*t[:6], tzinfo=timezone.utc)
    for attr in ("published", "updated"):
        s = getattr(entry, attr, None)
        if s:
            try:
                return dateparser.parse(s)
            except Exception:
                pass
    return None


def _get_image(entry: Any) -> str | None:
    # Try media:thumbnail / media:content
    media = getattr(entry, "media_thumbnail", None) or getattr(entry, "media_content", None)
    if media and isinstance(media, list) and media:
        return media[0].get("url")
    # Try enclosures
    for enc in getattr(entry, "enclosures", []):
        if enc.get("type", "").startswith("image/"):
            return enc.get("href") or enc.get("url")
    return None


async def fetch_feed(feed_meta: dict[str, Any], client: httpx.AsyncClient) -> list[dict]:
    url = feed_meta["url"]
    try:
        resp = await client.get(url, timeout=15.0, follow_redirects=True)
        resp.raise_for_status()
        parsed = feedparser.parse(resp.text)

        articles = []
        for entry in parsed.entries[:30]:  # limit per feed
            link = getattr(entry, "link", None)
            title = getattr(entry, "title", None)
            if not link or not title:
                continue

            summary = getattr(entry, "summary", None) or getattr(entry, "description", None)
            # Strip HTML tags from summary
            if summary:
                import re
                summary = re.sub(r"<[^>]+>", "", summary).strip()[:500]

            articles.append({
                "id": _make_id(link),
                "title": title.strip(),
                "summary": summary,
                "url": link,
                "source_name": feed_meta["name"],
                "source_country": feed_meta["country"],
                "image_url": _get_image(entry),
                "published_at": _parse_date(entry),
                "categories": [feed_meta["category"]],
                "reliability_weight": feed_meta["reliability_weight"],
            })
        return articles
    except Exception as exc:
        logger.debug("Feed %s failed: %s", url, exc)
        return []


async def fetch_all_feeds() -> list[dict]:
    sem = asyncio.Semaphore(_SEMAPHORE_SIZE)
    results: list[dict] = []

    async def bounded_fetch(meta: dict, client: httpx.AsyncClient) -> list[dict]:
        async with sem:
            return await fetch_feed(meta, client)

    async with httpx.AsyncClient(
        headers={"User-Agent": "WorldMonitor/1.0 (+https://github.com/worldmonitor)"},
        verify=False,  # some feeds have cert issues
    ) as client:
        tasks = [bounded_fetch(meta, client) for meta in RSS_FEEDS]
        batches = await asyncio.gather(*tasks, return_exceptions=True)

    for batch in batches:
        if isinstance(batch, list):
            results.extend(batch)

    # Deduplicate by id
    seen: set[str] = set()
    unique = []
    for a in results:
        if a["id"] not in seen:
            seen.add(a["id"])
            unique.append(a)

    logger.info("Fetched %d unique articles from %d feeds", len(unique), len(RSS_FEEDS))
    return unique
