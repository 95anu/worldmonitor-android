package com.worldmonitor.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "worldmonitor_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_REFRESH_INTERVAL = intPreferencesKey("refresh_interval_minutes")
        private val KEY_MAP_STYLE = stringPreferencesKey("map_style_url")

        const val DEFAULT_SERVER_URL = "https://api.anuragtech.in"
        const val DEFAULT_MAP_STYLE = "https://demotiles.maplibre.org/style.json"
        const val DEFAULT_REFRESH_INTERVAL = 5
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val refreshInterval: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_REFRESH_INTERVAL] ?: DEFAULT_REFRESH_INTERVAL
    }

    val mapStyleUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAP_STYLE] ?: DEFAULT_MAP_STYLE
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun setRefreshInterval(minutes: Int) {
        context.dataStore.edit { it[KEY_REFRESH_INTERVAL] = minutes }
    }

    suspend fun setMapStyleUrl(url: String) {
        context.dataStore.edit { it[KEY_MAP_STYLE] = url }
    }
}
