package com.worldmonitor.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmonitor.android.WorldMonitorApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionStatus { IDLE, TESTING, SUCCESS, FAILURE }

data class SettingsUiState(
    val serverUrl: String = "",
    val refreshInterval: Int = 5,
    val mapStyleUrl: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val connectionError: String? = null,
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
                    serverUrl = prefs.serverUrl.first(),
                    refreshInterval = prefs.refreshInterval.first(),
                    mapStyleUrl = prefs.mapStyleUrl.first(),
                )
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url, connectionStatus = ConnectionStatus.IDLE) }
    }

    fun testAndSaveConnection(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.TESTING, connectionError = null) }
            val repo = app.buildRepository(url)
            repo.checkHealth().fold(
                onSuccess = {
                    prefs.setServerUrl(url)
                    _uiState.update { it.copy(connectionStatus = ConnectionStatus.SUCCESS, serverUrl = url) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.FAILURE,
                            connectionError = err.message ?: "Connection failed",
                        )
                    }
                }
            )
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
