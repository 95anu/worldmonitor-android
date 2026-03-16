package com.worldmonitor.android.data.repository

import com.worldmonitor.android.data.api.ApiClient
import com.worldmonitor.android.data.models.CountryDetailResponse
import com.worldmonitor.android.data.models.EventItem
import com.worldmonitor.android.data.models.HeatmapResponse
import com.worldmonitor.android.data.models.NewsResponse
import com.worldmonitor.android.data.models.StatsResponse
import com.worldmonitor.android.data.models.WsMessage
import com.worldmonitor.android.data.websocket.LiveUpdatesClient
import kotlinx.coroutines.flow.Flow

class WorldMonitorRepository(
    private val serverUrl: String,
    private val wsClient: LiveUpdatesClient,
) {
    private val api get() = ApiClient.getApi(serverUrl)

    suspend fun getHeatmap(): Result<HeatmapResponse> = runCatching { api.getHeatmap() }

    suspend fun getCountryDetail(code: String): Result<CountryDetailResponse> =
        runCatching { api.getCountryDetail(code) }

    suspend fun getNews(
        page: Int = 1,
        limit: Int = 20,
        country: String? = null,
        category: String? = null,
        query: String? = null,
    ): Result<NewsResponse> = runCatching {
        api.getNews(page = page, limit = limit, country = country, category = category, query = query)
    }

    suspend fun getEvents(
        types: String? = null,
        days: Int = 7,
        minSeverity: String? = null,
    ): Result<List<EventItem>> = runCatching {
        api.getEvents(type = types, days = days, minSeverity = minSeverity)
    }

    suspend fun getStats(): Result<StatsResponse> = runCatching { api.getStats() }

    suspend fun checkHealth(): Result<Boolean> = runCatching {
        api.getHealth().isSuccessful
    }

    fun getLiveUpdates(): Flow<WsMessage> = wsClient.connect(serverUrl)
}
