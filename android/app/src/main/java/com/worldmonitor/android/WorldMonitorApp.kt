package com.worldmonitor.android

import android.app.Application
import com.worldmonitor.android.data.preferences.AppPreferences
import com.worldmonitor.android.data.repository.WorldMonitorRepository
import com.worldmonitor.android.data.websocket.LiveUpdatesClient

class WorldMonitorApp : Application() {

    lateinit var preferences: AppPreferences
        private set

    // WS client — one instance, reused across the app
    val wsClient: LiveUpdatesClient by lazy { LiveUpdatesClient() }

    // Repository is recreated by ViewModels with the current server URL
    fun buildRepository(serverUrl: String): WorldMonitorRepository =
        WorldMonitorRepository(serverUrl, wsClient)

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
    }
}
