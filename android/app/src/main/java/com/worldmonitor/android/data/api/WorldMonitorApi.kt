package com.worldmonitor.android.data.api

import com.worldmonitor.android.data.models.CountryDetailResponse
import com.worldmonitor.android.data.models.EventItem
import com.worldmonitor.android.data.models.HeatmapResponse
import com.worldmonitor.android.data.models.NewsResponse
import com.worldmonitor.android.data.models.StatsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface WorldMonitorApi {

    @GET("api/heatmap")
    suspend fun getHeatmap(): HeatmapResponse

    @GET("api/heatmap/{countryCode}")
    suspend fun getCountryDetail(
        @Path("countryCode") countryCode: String,
    ): CountryDetailResponse

    @GET("api/news")
    suspend fun getNews(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("country") country: String? = null,
        @Query("category") category: String? = null,
        @Query("q") query: String? = null,
    ): NewsResponse

    @GET("api/events")
    suspend fun getEvents(
        @Query("type") type: String? = null,
        @Query("days") days: Int = 7,
        @Query("min_severity") minSeverity: String? = null,
    ): List<EventItem>

    @GET("api/stats")
    suspend fun getStats(): StatsResponse

    @GET("api/health")
    suspend fun getHealth(): Response<Unit>
}
