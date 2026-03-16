package com.worldmonitor.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmonitor.android.WorldMonitorApp
import com.worldmonitor.android.data.models.EventItem
import com.worldmonitor.android.data.models.StatsResponse
import com.worldmonitor.android.data.models.WsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

data class MapEventInfo(
    val type: String,
    val severity: String,
    val title: String,
    val magnitude: Double,
)

data class MapUiState(
    val isLoading: Boolean = true,
    val scores: Map<String, Float> = emptyMap(),
    val stats: StatsResponse? = null,
    val error: String? = null,
    val lastUpdated: String? = null,
    val eventGeoJson: String = EMPTY_FEATURE_COLLECTION,
    val isConnected: Boolean = false,
    // Country selection
    val selectedCountry: String? = null,
    val selectedCountryScore: Float = 0f,
    val selectedCountryArticles: Int? = null,
    val selectedCountryEvents: Int? = null,
    // Event point selection
    val selectedEvent: MapEventInfo? = null,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WorldMonitorApp
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /** Refresh interval in seconds (converted from the minutes preference). */
    private val _refreshIntervalSeconds = MutableStateFlow(DEFAULT_REFRESH_INTERVAL_SECONDS)
    val refreshIntervalSeconds: StateFlow<Int> = _refreshIntervalSeconds.asStateFlow()

    private var refreshJob: Job? = null
    private var liveJob: Job? = null

    init {
        viewModelScope.launch {
            app.preferences.refreshInterval.map { minutes -> minutes * 60 }.collect { secs ->
                _refreshIntervalSeconds.value = secs
            }
        }
        viewModelScope.launch {
            app.preferences.serverUrl.collectLatest { url ->
                if (url.isNotBlank()) {
                    startAutoRefresh(url)
                    connectLiveUpdates(url)
                }
            }
        }
    }

    private fun startAutoRefresh(serverUrl: String) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val intervalMinutes = app.preferences.refreshInterval.first()
            while (true) {
                loadData(serverUrl)
                delay(intervalMinutes * 60_000L)
            }
        }
    }

    private fun connectLiveUpdates(serverUrl: String) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            val repo = app.buildRepository(serverUrl)
            repo.getLiveUpdates().collect { msg ->
                when (msg.type) {
                    "heatmap_update" -> msg.scores?.let { scores ->
                        _uiState.update { it.copy(scores = scores) }
                    }
                }
            }
        }
    }

    private suspend fun loadData(serverUrl: String) {
        val repo = app.buildRepository(serverUrl)
        _uiState.update { it.copy(isLoading = true, error = null) }

        val heatmapResult = repo.getHeatmap()
        val statsResult = repo.getStats()
        val eventsResult = repo.getEvents("earthquake,conflict,fire", days = 7)

        heatmapResult.fold(
            onSuccess = { heatmap ->
                app.setServerConnected(true)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        scores = heatmap.scores,
                        lastUpdated = heatmap.updatedAt,
                        isConnected = true,
                    )
                }
            },
            onFailure = { err ->
                app.setServerConnected(false)
                _uiState.update { it.copy(isLoading = false, error = err.message, isConnected = false) }
            }
        )

        statsResult.onSuccess { stats ->
            _uiState.update { it.copy(stats = stats) }
        }

        eventsResult.fold(
            onSuccess = { events ->
                val geoJson = withContext(Dispatchers.Default) { buildEventGeoJson(events) }
                _uiState.update { it.copy(eventGeoJson = geoJson) }
            },
            onFailure = { /* events don't block the heatmap */ }
        )
    }

    fun refresh() {
        viewModelScope.launch {
            val url = app.preferences.serverUrl.first()
            if (url.isNotBlank()) loadData(url)
        }
    }

    /** Called when the user taps an event circle on the map. */
    fun selectEvent(event: MapEventInfo?) {
        _uiState.update { it.copy(selectedEvent = event, selectedCountry = null, selectedCountryArticles = null, selectedCountryEvents = null) }
    }

    /** Called when the user taps a country on the map. Fetches country detail async. */
    fun selectCountry(iso: String?) {
        if (iso == null) {
            _uiState.update { it.copy(selectedCountry = null, selectedCountryArticles = null, selectedCountryEvents = null, selectedEvent = null) }
            return
        }
        _uiState.update {
            it.copy(
                selectedCountry = iso,
                selectedCountryScore = it.scores[iso] ?: 0f,
                selectedCountryArticles = null,
                selectedCountryEvents = null,
                selectedEvent = null,
            )
        }
        viewModelScope.launch {
            val url = app.preferences.serverUrl.first()
            if (url.isBlank()) return@launch
            app.buildRepository(url).getCountryDetail(iso).onSuccess { detail ->
                _uiState.update {
                    it.copy(
                        selectedCountryArticles = detail.articles24h,
                        selectedCountryEvents = detail.events7d,
                    )
                }
            }
        }
    }

    private fun buildEventGeoJson(events: List<EventItem>): String {
        val features = events
            .filter { it.lat != null && it.lon != null }
            .joinToString(",") { event ->
                val safeTitle = event.title.replace("\"", "'")
                """{"type":"Feature","geometry":{"type":"Point","coordinates":[${event.lon},${event.lat}]},"properties":{"type":"${event.type}","severity":"${event.severity}","title":"$safeTitle","magnitude":${event.magnitude ?: 0.0}}}"""
            }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    private companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 300
    }
}
