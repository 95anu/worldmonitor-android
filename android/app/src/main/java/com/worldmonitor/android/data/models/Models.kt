package com.worldmonitor.android.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeatmapResponse(
    val scores: Map<String, Float>,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("country_count") val countryCount: Int = 0,
)

@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    val summary: String? = null,
    val url: String,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("source_country") val sourceCountry: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("fetched_at") val fetchedAt: String? = null,
    val countries: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    @SerialName("importance_score") val importanceScore: Float = 0.5f,
    // local-only flag for breaking news highlight
    @kotlinx.serialization.Transient val isBreaking: Boolean = false,
)

@Serializable
data class NewsResponse(
    val items: List<NewsItem>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val pages: Int = 1,
)

@Serializable
data class EventItem(
    val id: String,
    val type: String, // earthquake | fire | conflict
    val title: String,
    val description: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val magnitude: Double? = null,
    val severity: String = "low", // low | medium | high | critical
    @SerialName("occurred_at") val occurredAt: String? = null,
    val country: String? = null,
)

@Serializable
data class CountryStats(
    val code: String,
    @SerialName("article_count") val articleCount: Int,
)

@Serializable
data class StatsResponse(
    @SerialName("total_articles_24h") val totalArticles24h: Int,
    @SerialName("sources_active") val sourcesActive: Int,
    @SerialName("top_countries") val topCountries: List<CountryStats> = emptyList(),
    @SerialName("earthquakes_7d") val earthquakes7d: Int = 0,
    @SerialName("fires_24h") val fires24h: Int = 0,
    @SerialName("last_updated") val lastUpdated: String,
)

@Serializable
data class WsMessage(
    val type: String, // breaking_news | heatmap_update | ping
    val article: NewsItem? = null,
    val scores: Map<String, Float>? = null,
)

@Serializable
data class CountryDetailResponse(
    @SerialName("country_code") val countryCode: String,
    val score: Float,
    @SerialName("articles_24h") val articles24h: Int,
    @SerialName("events_7d") val events7d: Int,
)
