package com.worldmonitor.android.data.websocket

import android.util.Log
import com.worldmonitor.android.data.models.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

class LiveUpdatesClient {

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun connect(serverUrl: String): Flow<WsMessage> = callbackFlow {
        val wsUrl = serverUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/ws/live"

        var attempt = 0

        fun doConnect() {
            val request = Request.Builder().url(wsUrl).build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.i("LiveUpdates", "WebSocket connected")
                    attempt = 0
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    try {
                        val msg = json.decodeFromString<WsMessage>(text)
                        if (msg.type != "ping") trySend(msg)
                    } catch (e: Exception) {
                        Log.w("LiveUpdates", "Parse error: $text")
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w("LiveUpdates", "WS failure: ${t.message}")
                    val backoffMs = min(60_000L, (1000 * 2.0.pow(attempt)).toLong())
                    attempt++
                    scope.launch {
                        delay(backoffMs)
                        doConnect()
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.i("LiveUpdates", "WS closed: $code")
                }
            })
        }

        doConnect()

        awaitClose {
            webSocket?.close(1000, "Client closed")
            webSocket = null
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnect requested")
        webSocket = null
    }
}
