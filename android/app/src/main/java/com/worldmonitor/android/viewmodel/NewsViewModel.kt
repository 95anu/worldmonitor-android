package com.worldmonitor.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmonitor.android.WorldMonitorApp
import com.worldmonitor.android.data.models.NewsItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewsUiState(
    val items: List<NewsItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val selectedCountry: String? = null,
    val selectedCategory: String? = null,
    val searchQuery: String? = null,
    val currentPage: Int = 1,
)

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WorldMonitorApp
    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private var liveJob: Job? = null

    init {
        viewModelScope.launch {
            app.preferences.serverUrl.collectLatest { url ->
                if (url.isNotBlank()) {
                    refresh()
                    connectBreakingNews(url)
                }
            }
        }
    }

    private fun connectBreakingNews(serverUrl: String) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            val repo = app.buildRepository(serverUrl)
            repo.getLiveUpdates().collect { msg ->
                if (msg.type == "breaking_news" && msg.article != null) {
                    val breaking = msg.article.copy(isBreaking = true)
                    _uiState.update { state ->
                        state.copy(items = listOf(breaking) + state.items)
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val url = app.preferences.serverUrl.first()
            if (url.isBlank()) return@launch
            _uiState.update { it.copy(isLoading = true, error = null, currentPage = 1) }
            val state = _uiState.value
            val repo = app.buildRepository(url)
            repo.getNews(
                page = 1,
                country = state.selectedCountry,
                category = state.selectedCategory,
                query = state.searchQuery,
            ).fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = response.items,
                            hasMore = response.page < response.pages,
                            currentPage = 1,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            val url = app.preferences.serverUrl.first()
            if (url.isBlank()) return@launch
            val nextPage = state.currentPage + 1
            _uiState.update { it.copy(isLoadingMore = true) }
            val repo = app.buildRepository(url)
            repo.getNews(
                page = nextPage,
                country = state.selectedCountry,
                category = state.selectedCategory,
                query = state.searchQuery,
            ).fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            items = it.items + response.items,
                            hasMore = response.page < response.pages,
                            currentPage = nextPage,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoadingMore = false, error = err.message) }
                }
            )
        }
    }

    fun filterByCountry(code: String?) {
        _uiState.update { it.copy(selectedCountry = code) }
        refresh()
    }

    fun filterByCategory(cat: String?) {
        _uiState.update { it.copy(selectedCategory = cat) }
        refresh()
    }

    fun search(query: String?) {
        _uiState.update { it.copy(searchQuery = query?.ifBlank { null }) }
        refresh()
    }
}
