# WorldMonitor Android

Android equivalent of [worldmonitor](https://github.com/koala73/worldmonitor) with a Raspberry Pi 4 backend handling all heavy processing.

## Architecture

```
Android App  ←──HTTP/WebSocket──→  Raspberry Pi 4 Backend
(MapLibre +                        (FastAPI + SQLite)
 Jetpack Compose)                  ├─ RSS aggregation (50+ feeds)
                                   ├─ GDELT conflict events
                                   ├─ USGS earthquake data
                                   ├─ NASA FIRMS wildfire data
                                   └─ Country activity scoring
```

The Pi does all the heavy lifting — feed fetching, geo-mapping, importance scoring, caching. The Android app only renders and displays.

## Features

| Screen | Description |
|--------|-------------|
| **Map** | World heatmap with countries colored by news activity (MapLibre GL) |
| **News** | Aggregated feed from 50+ sources, filterable by country/category |
| **Events** | Earthquakes (USGS), wildfires (NASA FIRMS), conflicts (GDELT) with map pins |
| **Settings** | Configure Pi server URL, refresh interval |

---

## Backend Setup (Raspberry Pi 4)

### Requirements
- Raspberry Pi 4 (2GB+ RAM)
- Raspberry Pi OS (64-bit recommended)
- Python 3.10+
- Static local IP (see below)

### 1. Set a static IP on your Pi

Edit `/etc/dhcpcd.conf`:
```
interface eth0
static ip_address=192.168.1.100/24
static routers=192.168.1.1
static domain_name_servers=1.1.1.1
```
Or set a DHCP reservation in your router admin panel.

### 2. Install & run

```bash
git clone <this-repo>
cd worldmonitor-android/backend
chmod +x setup.sh
./setup.sh
```

The setup script will:
- Create a Python virtualenv
- Install all dependencies
- Register & start the `worldmonitor-backend` systemd service

### 3. Verify

```bash
curl http://192.168.1.100:8000/api/health
# → {"status": "ok", ...}

curl http://192.168.1.100:8000/api/stats
# → {"total_articles_24h": ..., "sources_active": ...}
```

### Optional: NASA FIRMS wildfire data

1. Get a free API key at https://firms.modaps.eosdis.nasa.gov/api/
2. Edit `backend/.env` (created by setup.sh):
   ```
   NASA_FIRMS_API_KEY=your_key_here
   ```
3. Restart: `sudo systemctl restart worldmonitor-backend`

### Configuration

Edit `backend/.env` or set environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_HOST` | `0.0.0.0` | Bind host |
| `SERVER_PORT` | `8000` | Bind port |
| `DB_PATH` | `./worldmonitor.db` | SQLite DB path |
| `REFRESH_INTERVAL_MINUTES` | `15` | Feed refresh interval |
| `MAX_ARTICLES_AGE_DAYS` | `3` | Purge articles older than N days |
| `NASA_FIRMS_API_KEY` | `""` | Optional — wildfire data |
| `LOG_LEVEL` | `INFO` | Logging level |

### API Reference

| Endpoint | Description |
|----------|-------------|
| `GET /api/health` | Health check |
| `GET /api/stats` | Global statistics |
| `GET /api/heatmap` | Country activity scores (0.0–1.0) |
| `GET /api/heatmap/{cc}` | Single country detailed stats |
| `GET /api/news` | Paginated news (`?page=1&limit=20&country=US&category=geopolitics&q=query`) |
| `GET /api/events` | Events (`?type=earthquake,fire,conflict&days=7`) |
| `WS /ws/live` | WebSocket — breaking news & heatmap updates |

### Resource usage on Pi 4

- Memory: ~150–250 MB (SQLite + Python process)
- CPU: low at rest, brief spikes during feed fetches
- Disk: ~500 MB for 3 days of articles
- Network: ~100 MB/day (feed fetches)

---

## Android App Setup

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 26+ (Android 8.0)
- Phone on the same WiFi as the Pi

### 1. Get the countries GeoJSON

Download a simplified countries GeoJSON and place it in `android/app/src/main/assets/countries_simple.geojson`:

```bash
# Option A: Download from Natural Earth (recommended)
curl -L "https://raw.githubusercontent.com/datasets/geo-countries/master/data/countries.geojson" \
  -o android/app/src/main/assets/countries_simple.geojson

# Option B: Use the tippecanoe-simplified version for better performance
# See: https://github.com/nvkelso/natural-earth-vector
```

The GeoJSON must have an `ISO_A2` property on each feature (standard Natural Earth format).

### 2. Build & install

```bash
cd android
./gradlew installDebug
```

Or open in Android Studio and run normally.

### 3. Configure

On first launch, the app shows a setup screen. Enter your Pi's address:
```
http://192.168.1.100:8000
```

Tap **Connect** — it tests the connection before saving.

---

## Development

### Backend (local dev)

```bash
cd backend
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Auto-reload enabled. API docs at http://localhost:8000/docs

### Adding RSS feeds

Edit `backend/feeds/rss_fetcher.py` and add entries to `RSS_FEEDS`:

```python
{"url": "https://example.com/feed.rss", "name": "Example News",
 "country": "US", "category": "general", "reliability_weight": 0.8}
```

Categories: `general`, `geopolitics`, `conflict`, `economics`, `technology`, `environment`, `health`

### Heatmap scoring algorithm

1. Count articles per country (last 24h)
2. Apply exponential time decay (half-life = 6h)
3. Weight by article importance score
4. Add event contributions (earthquakes, fires, conflicts)
5. Log-normalize to 0–1
6. Cache for 60s

---

## Project Structure

```
worldmonitor-android/
├── backend/                    # Raspberry Pi Python backend
│   ├── main.py                 # FastAPI app entry point
│   ├── config.py               # Configuration
│   ├── database.py             # SQLAlchemy + SQLite models
│   ├── scheduler.py            # APScheduler background jobs
│   ├── feeds/
│   │   ├── rss_fetcher.py      # RSS feed fetching (50+ feeds)
│   │   ├── gdelt.py            # GDELT conflict events
│   │   ├── usgs.py             # USGS earthquake data
│   │   └── nasa_firms.py       # NASA FIRMS wildfire data
│   ├── processing/
│   │   ├── geo_mapper.py       # Map text → country codes
│   │   ├── scorer.py           # Country activity scoring
│   │   └── importance.py       # Article importance scoring
│   ├── api/routes/
│   │   ├── heatmap.py
│   │   ├── news.py
│   │   ├── events.py
│   │   ├── stats.py
│   │   └── ws.py               # WebSocket
│   ├── requirements.txt
│   ├── setup.sh
│   └── systemd/
│       └── worldmonitor-backend.service
│
└── android/                    # Android Studio project
    └── app/src/main/
        ├── java/com/worldmonitor/android/
        │   ├── data/
        │   │   ├── api/         # Retrofit API client
        │   │   ├── models/      # Serializable data classes
        │   │   ├── websocket/   # Live updates client
        │   │   ├── preferences/ # DataStore settings
        │   │   └── repository/  # Repository pattern
        │   ├── ui/
        │   │   ├── screens/     # Map, News, Events, Settings, Setup
        │   │   ├── components/  # NewsCard, EventCard, StatsBar
        │   │   └── theme/       # Dark theme colors/typography
        │   └── viewmodel/       # MapViewModel, NewsViewModel, etc.
        └── assets/
            └── countries_simple.geojson  # ← you must download this
```

---

## Compared to original worldmonitor

| Feature | Original | This project |
|---------|----------|--------------|
| Platform | Web + Desktop (Tauri) | Android native |
| Processing | Client-side + Vercel Edge | Raspberry Pi 4 |
| Map | MapLibre GL (WebGL) | MapLibre Android SDK |
| AI analysis | Ollama / Groq / OpenRouter | None (hobby scope) |
| Data sources | 435+ feeds, AIS, aviation, etc. | ~50 feeds, USGS, GDELT, FIRMS |
| Cost | Vercel/Redis hosting costs | Free (local Pi) |

This project prioritizes simplicity and self-hosting over feature completeness.

---

## License

MIT
