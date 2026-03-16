package com.worldmonitor.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmonitor.android.WorldMonitorApp
import com.worldmonitor.android.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val refreshInterval: Int = AppPreferences.DEFAULT_REFRESH_INTERVAL,
    val mapStyleUrl: String = "",
    val isConnected: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WorldMonitorApp
    private val prefs = app.preferences
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    refreshInterval = prefs.refreshInterval.first(),
                    mapStyleUrl = prefs.mapStyleUrl.first(),
                )
            }
        }
        // Observe shared connection state — updated by MapViewModel on every refresh cycle
        viewModelScope.launch {
            app.isServerConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        }
    }

    fun saveRefreshInterval(minutes: Int) {
        viewModelScope.launch {
            prefs.setRefreshInterval(minutes)
            _uiState.update { it.copy(refreshInterval = minutes) }
        }
    }

    fun saveMapStyleUrl(url: String) {
        viewModelScope.launch {
            prefs.setMapStyleUrl(url)
            _uiState.update { it.copy(mapStyleUrl = url) }
        }
    }
}
