#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_NAME="worldmonitor-backend"
VENV_DIR="$SCRIPT_DIR/venv"
SERVICE_FILE="$SCRIPT_DIR/systemd/${SERVICE_NAME}.service"
INSTALL_USER="${SUDO_USER:-$(whoami)}"

echo "==================================================="
echo "  WorldMonitor Backend Setup for Raspberry Pi 4"
echo "==================================================="
echo

# ── Check Python version ────────────────────────────────────────────────────
PY=$(command -v python3 2>/dev/null || true)
if [ -z "$PY" ]; then
    echo "ERROR: python3 not found. Install with: sudo apt install python3"
    exit 1
fi

PY_VER=$($PY -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
PY_MAJOR=$(echo "$PY_VER" | cut -d. -f1)
PY_MINOR=$(echo "$PY_VER" | cut -d. -f2)
if [ "$PY_MAJOR" -lt 3 ] || ([ "$PY_MAJOR" -eq 3 ] && [ "$PY_MINOR" -lt 10 ]); then
    echo "ERROR: Python 3.10+ required. Found: $PY_VER"
    echo "Upgrade with: sudo apt install python3.11"
    exit 1
fi
echo "[OK] Python $PY_VER"

# ── Create virtualenv ────────────────────────────────────────────────────────
if [ ! -d "$VENV_DIR" ]; then
    echo "Creating virtualenv at $VENV_DIR..."
    $PY -m venv "$VENV_DIR"
fi
echo "[OK] Virtualenv ready"

# ── Install dependencies ─────────────────────────────────────────────────────
echo "Installing Python dependencies..."
"$VENV_DIR/bin/pip" install --upgrade pip --quiet
"$VENV_DIR/bin/pip" install -r "$SCRIPT_DIR/requirements.txt" --quiet
echo "[OK] Dependencies installed"

# ── Create .env if not present ───────────────────────────────────────────────
if [ ! -f "$SCRIPT_DIR/.env" ]; then
    cat > "$SCRIPT_DIR/.env" <<EOF
SERVER_HOST=0.0.0.0
SERVER_PORT=8000
DB_PATH=$SCRIPT_DIR/worldmonitor.db
NASA_FIRMS_API_KEY=
REFRESH_INTERVAL_MINUTES=15
MAX_ARTICLES_AGE_DAYS=3
LOG_LEVEL=INFO
EOF
    echo "[OK] Created .env (edit to add NASA_FIRMS_API_KEY for wildfire data)"
else
    echo "[OK] .env already exists"
fi

# ── Install systemd service ──────────────────────────────────────────────────
if [ -f "$SERVICE_FILE" ]; then
    # Patch the service file with actual paths
    TMP_SERVICE="/tmp/${SERVICE_NAME}.service"
    sed \
        -e "s|__VENV_DIR__|$VENV_DIR|g" \
        -e "s|__SCRIPT_DIR__|$SCRIPT_DIR|g" \
        -e "s|__USER__|$INSTALL_USER|g" \
        "$SERVICE_FILE" > "$TMP_SERVICE"

    if command -v systemctl &>/dev/null && [ "$(id -u)" -eq 0 ]; then
        cp "$TMP_SERVICE" "/etc/systemd/system/${SERVICE_NAME}.service"
        systemctl daemon-reload
        systemctl enable "$SERVICE_NAME"
        systemctl restart "$SERVICE_NAME"
        echo "[OK] Systemd service installed and started"
    else
        echo "[NOTE] Not running as root — skipping systemd install."
        echo "       To install manually:"
        echo "       sudo cp $TMP_SERVICE /etc/systemd/system/"
        echo "       sudo systemctl enable --now $SERVICE_NAME"
    fi
fi

# ── Get local IP ─────────────────────────────────────────────────────────────
LOCAL_IP=$(hostname -I | awk '{print $1}' 2>/dev/null || echo "unknown")

echo
echo "==================================================="
echo "  Setup complete!"
echo "==================================================="
echo
echo "  Backend will be available at:"
echo "  http://$LOCAL_IP:8000"
echo
echo "  Use this URL in the Android app Settings."
echo
echo "  API docs: http://$LOCAL_IP:8000/docs"
echo "  Health:   http://$LOCAL_IP:8000/api/health"
echo
echo "  To set a static IP, edit /etc/dhcpcd.conf:"
echo "    static ip_address=$LOCAL_IP/24"
echo
echo "  Optional: add NASA FIRMS API key to .env for wildfire data"
echo "    https://firms.modaps.eosdis.nasa.gov/api/"
echo "==================================================="
