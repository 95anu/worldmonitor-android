"""
WebSocket endpoint for real-time updates.
Pushes:
  {"type": "breaking_news", "article": {...}}
  {"type": "heatmap_update", "scores": {...}}
  {"type": "ping"}
"""
import asyncio
import json
import logging
from typing import Any

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

logger = logging.getLogger(__name__)
router = APIRouter(tags=["websocket"])

PING_INTERVAL = 30  # seconds


class ConnectionManager:
    def __init__(self) -> None:
        self._connections: list[WebSocket] = []

    async def connect(self, ws: WebSocket) -> None:
        await ws.accept()
        self._connections.append(ws)
        logger.debug("WS client connected. Total: %d", len(self._connections))

    def disconnect(self, ws: WebSocket) -> None:
        if ws in self._connections:
            self._connections.remove(ws)
        logger.debug("WS client disconnected. Total: %d", len(self._connections))

    async def broadcast(self, message: dict[str, Any]) -> None:
        dead: list[WebSocket] = []
        payload = json.dumps(message)
        for ws in list(self._connections):
            try:
                await ws.send_text(payload)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)

    @property
    def connection_count(self) -> int:
        return len(self._connections)


manager = ConnectionManager()


@router.websocket("/ws/live")
async def websocket_endpoint(ws: WebSocket):
    await manager.connect(ws)
    try:
        # Heartbeat task
        async def ping_loop():
            while True:
                await asyncio.sleep(PING_INTERVAL)
                try:
                    await ws.send_text(json.dumps({"type": "ping"}))
                except Exception:
                    break

        ping_task = asyncio.create_task(ping_loop())
        try:
            while True:
                # Keep connection alive; client sends pong
                data = await ws.receive_text()
        except WebSocketDisconnect:
            pass
        finally:
            ping_task.cancel()
    finally:
        manager.disconnect(ws)


async def broadcast_breaking_news(article: dict) -> None:
    await manager.broadcast({"type": "breaking_news", "article": article})


async def broadcast_heatmap_update(scores: dict[str, float]) -> None:
    if manager.connection_count > 0:
        await manager.broadcast({"type": "heatmap_update", "scores": scores})
