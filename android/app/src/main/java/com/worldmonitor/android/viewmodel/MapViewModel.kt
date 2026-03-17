package com.worldmonitor.android.viewmodel

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmonitor.android.WorldMonitorApp
import com.worldmonitor.android.data.models.EventItem
import com.worldmonitor.android.data.models.StatsResponse
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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource

private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
const val PULSE_FRAMES = 6

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
    val selectedCountry: String? = null,
    val selectedCountryScore: Float = 0f,
    val selectedCountryArticles: Int? = null,
    val selectedCountryEvents: Int? = null,
    val selectedEvent: MapEventInfo? = null,
    val timeFilterHours: Int = 168,
    val showTimeSlider: Boolean = false,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WorldMonitorApp
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _refreshIntervalSeconds = MutableStateFlow(DEFAULT_REFRESH_INTERVAL_SECONDS)
    val refreshIntervalSeconds: StateFlow<Int> = _refreshIntervalSeconds.asStateFlow()

    private var _mapView: MapView? = null
    val mapLibreMap = MutableStateFlow<MapLibreMap?>(null)
    val mapStyleReady = MutableStateFlow(false)
    var eventsSource: GeoJsonSource? = null
    var hasSetInitialCamera: Boolean = false

    // ── Event data + pulse animation ──────────────────────────────────────────
    private var _allEvents: List<EventItem> = emptyList()
    private var pulseFrame = 0
    private var pulseJob: Job? = null
    private var filterJob: Job? = null

    fun getOrCreateMapView(context: Context): MapView =
        _mapView ?: MapView(context.applicationContext).also {
            it.onCreate(Bundle())
            _mapView = it
        }

    override fun onCleared() {
        super.onCleared()
        pulseJob?.cancel()
        filterJob?.cancel()
        _mapView?.run { onPause(); onStop(); onDestroy() }
        _mapView = null
    }

    /** Start the 220ms frame clock that animates pulse rings on event nodes. */
    fun startPulseClock() {
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            while (true) {
                delay(220L)
                pulseFrame = (pulseFrame + 1) % PULSE_FRAMES
                val src = eventsSource ?: continue
                if (_allEvents.isEmpty()) continue
                val geoJson = withContext(Dispatchers.Default) {
                    buildEventGeoJson(getFilteredEvents(), pulseFrame)
                }
                src.setGeoJson(geoJson)
            }
        }
    }

    fun toggleTimeSlider() {
        _uiState.update { it.copy(showTimeSlider = !it.showTimeSlider) }
    }

    fun onTimeSliderChange(hours: Int) {
        _uiState.update { it.copy(timeFilterHours = hours) }
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(80L)
            val geoJson = withContext(Dispatchers.Default) {
                buildEventGeoJson(getFilteredEvents(), pulseFrame)
            }
            eventsSource?.setGeoJson(geoJson)
        }
    }

    private fun getFilteredEvents(): List<EventItem> {
        val cutoffMs = System.currentTimeMillis() - _uiState.value.timeFilterHours * 3_600_000L
        return _allEvents.filter { event ->
            val ts = event.occurredAt ?: return@filter true
            try {
                val ms = java.time.ZonedDateTime.parse(ts).toInstant().toEpochMilli()
                ms >= cutoffMs
            } catch (_: Exception) { true }
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private var refreshJob: Job? = null
    private var liveJob: Job? = null

    init {
        viewModelScope.launch {
            app.preferences.refreshInterval.map { it * 60 }.collect { secs ->
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
                if (msg.type == "heatmap_update") {
                    msg.scores?.let { scores -> _uiState.update { it.copy(scores = scores) } }
                }
            }
        }
    }

    private suspend fun loadData(serverUrl: String) {
        val repo = app.buildRepository(serverUrl)
        _uiState.update { it.copy(isLoading = true, error = null) }

        val heatmapResult = repo.getHeatmap()
        val statsResult   = repo.getStats()
        val eventsResult  = repo.getEvents("earthquake,conflict,fire", days = 7)

        heatmapResult.fold(
            onSuccess = { heatmap ->
                app.setServerConnected(true)
                _uiState.update { it.copy(isLoading = false, scores = heatmap.scores, lastUpdated = heatmap.updatedAt, isConnected = true) }
            },
            onFailure = { err ->
                app.setServerConnected(false)
                _uiState.update { it.copy(isLoading = false, error = err.message, isConnected = false) }
            }
        )

        statsResult.onSuccess { stats -> _uiState.update { it.copy(stats = stats) } }

        eventsResult.fold(
            onSuccess = { events ->
                _allEvents = events
                val geoJson = withContext(Dispatchers.Default) {
                    buildEventGeoJson(getFilteredEvents(), pulseFrame)
                }
                _uiState.update { it.copy(eventGeoJson = geoJson) }
            },
            onFailure = {}
        )
    }

    fun refresh() {
        viewModelScope.launch {
            val url = app.preferences.serverUrl.first()
            if (url.isNotBlank()) loadData(url)
        }
    }

    fun selectEvent(event: MapEventInfo?) {
        _uiState.update { it.copy(selectedEvent = event, selectedCountry = null, selectedCountryArticles = null, selectedCountryEvents = null) }
    }

    fun selectCountry(iso: String?) {
        if (iso == null) {
            _uiState.update { it.copy(selectedCountry = null, selectedCountryArticles = null, selectedCountryEvents = null, selectedEvent = null) }
            return
        }
        _uiState.update { it.copy(selectedCountry = iso, selectedCountryScore = _uiState.value.scores[iso] ?: 0f, selectedCountryArticles = null, selectedCountryEvents = null, selectedEvent = null) }
        viewModelScope.launch {
            val url = app.preferences.serverUrl.first()
            if (url.isBlank()) return@launch
            app.buildRepository(url).getCountryDetail(iso).onSuccess { detail ->
                _uiState.update { it.copy(selectedCountryArticles = detail.articles24h, selectedCountryEvents = detail.events7d) }
            }
        }
    }

    fun buildEventGeoJson(events: List<EventItem>, frame: Int): String {
        val nowMs = System.currentTimeMillis()
        val filtered = events.filter { it.lat != null && it.lon != null }
        val sb = StringBuilder(maxOf(filtered.size * 320, 60))
        sb.append("""{"type":"FeatureCollection","features":[""")
        var first = true
        filtered.forEach { e ->
            if (!first) sb.append(',')
            first = false
            val safeTitle = e.title.replace("\"", "'")
            val hoursAgo = e.occurredAt?.let {
                try {
                    val ms = java.time.ZonedDateTime.parse(it).toInstant().toEpochMilli()
                    ((nowMs - ms) / 3_600_000L).toInt().coerceIn(0, 168)
                } catch (_: Exception) { 84 }
            } ?: 84
            val sevScore = when (e.severity) {
                "low" -> 0.20f; "medium" -> 0.45f; "high" -> 0.72f; "critical" -> 0.95f; else -> 0.20f
            }
            val typeKey = when (e.type) {
                "earthquake", "fire", "conflict" -> e.type
                else -> "default"
            }
            val iconName = "pulse_${typeKey}_f$frame"
            sb.append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[${e.lon},${e.lat}]},"properties":{"type":"${e.type}","severity":"${e.severity}","severity_score":$sevScore,"title":"$safeTitle","magnitude":${e.magnitude ?: 0.0},"hours_ago":$hoursAgo,"icon_name":"$iconName"}}""")
        }
        sb.append("]}")
        return sb.toString()
    }

    private companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 300
    }
}
