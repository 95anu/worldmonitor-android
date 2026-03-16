package com.worldmonitor.android

import android.app.Application
import com.worldmonitor.android.data.preferences.AppPreferences
import com.worldmonitor.android.data.repository.WorldMonitorRepository
import com.worldmonitor.android.data.websocket.LiveUpdatesClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WorldMonitorApp : Application() {

    lateinit var preferences: AppPreferences
        private set

    val wsClient: LiveUpdatesClient by lazy { LiveUpdatesClient() }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Shared connection state updated by MapViewModel on every data load cycle. */
    private val _isServerConnected = MutableStateFlow(false)
    val isServerConnected: StateFlow<Boolean> = _isServerConnected.asStateFlow()

    fun setServerConnected(connected: Boolean) {
        _isServerConnected.value = connected
    }

    fun buildRepository(serverUrl: String): WorldMonitorRepository =
        WorldMonitorRepository(serverUrl, wsClient)

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        // Always enforce the Cloudflare tunnel endpoint — overrides any previously stored URL
        applicationScope.launch {
            preferences.setServerUrl(AppPreferences.DEFAULT_SERVER_URL)
        }
    }
}
