package com.worldmonitor.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmonitor.android.WorldMonitorApp
import com.worldmonitor.android.data.models.EventItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventsUiState(
    val allEvents: List<EventItem> = emptyList(),
    val filteredEvents: List<EventItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTypes: Set<String> = setOf("earthquake", "fire", "conflict"),
    val daysBack: Int = 7,
)

class EventsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WorldMonitorApp
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            app.preferences.serverUrl.collectLatest { url ->
                if (url.isNotBlank()) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val url = app.preferences.serverUrl.first()
            if (url.isBlank()) return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            val state = _uiState.value
            val repo = app.buildRepository(url)
            repo.getEvents(days = state.daysBack).fold(
                onSuccess = { events ->
                    val filtered = applyFilters(events, state.selectedTypes)
                    _uiState.update {
                        it.copy(isLoading = false, allEvents = events, filteredEvents = filtered)
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
            )
        }
    }

    fun toggleType(type: String) {
        val current = _uiState.value.selectedTypes.toMutableSet()
        if (type in current) current.remove(type) else current.add(type)
        val filtered = applyFilters(_uiState.value.allEvents, current)
        _uiState.update { it.copy(selectedTypes = current, filteredEvents = filtered) }
    }

    fun setDaysBack(days: Int) {
        _uiState.update { it.copy(daysBack = days) }
        refresh()
    }

    private fun applyFilters(events: List<EventItem>, types: Set<String>): List<EventItem> {
        return events
            .filter { it.type in types }
            .sortedWith(compareByDescending<EventItem> { severityOrder(it.severity) }.thenByDescending { it.occurredAt })
    }

    private fun severityOrder(s: String): Int = when (s) {
        "critical" -> 3
        "high" -> 2
        "medium" -> 1
        else -> 0
    }
}
