package com.worldmonitor.android.ui.screens

import android.os.Bundle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmonitor.android.ui.components.StatsBar
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.BgElevated
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.GreenOk
import com.worldmonitor.android.ui.theme.OrangeAlert
import com.worldmonitor.android.ui.theme.RedCritical
import com.worldmonitor.android.ui.theme.TextPrimary
import com.worldmonitor.android.ui.theme.TextSecondary
import com.worldmonitor.android.viewmodel.MapViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.InputStream
import java.nio.charset.StandardCharsets

private const val DARK_MAP_STYLE = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

private const val COUNTRIES_SOURCE_ID = "countries-source"
private const val EVENTS_SOURCE_ID = "events-source"
private const val COUNTRY_FILL_LAYER = "country-fill"
private const val COUNTRY_OUTLINE_LAYER = "country-fill-outline"
private const val EVENTS_CIRCLE_LAYER = "events-circles"

private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

@Composable
fun MapScreen(
    vm: MapViewModel = viewModel(),
    onCountryClick: (String) -> Unit = {},
) {
    val state by vm.uiState.collectAsState()
    val refreshIntervalSeconds by vm.refreshIntervalSeconds.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    remember { MapLibre.getInstance(context) }

    val mapView = remember { MapView(context) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var countriesSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var eventsSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    // Countdown timer for refresh badge
    var countdown by remember { mutableIntStateOf(refreshIntervalSeconds) }
    LaunchedEffect(refreshIntervalSeconds) {
        countdown = refreshIntervalSeconds
    }
    LaunchedEffect(refreshIntervalSeconds, state.lastUpdated) {
        countdown = refreshIntervalSeconds
        while (countdown > 0) {
            delay(1_000L)
            countdown--
        }
    }

    // Update country scores whenever they change (after style is loaded)
    LaunchedEffect(state.scores, styleReady) {
        val src = countriesSource ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val geoJson = loadCountriesGeoJson(context, state.scores)
        src.setGeoJson(geoJson)
    }

    // Update events GeoJSON whenever it changes
    LaunchedEffect(state.eventGeoJson, styleReady) {
        val src = eventsSource ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        src.setGeoJson(state.eventGeoJson)
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        ) { view ->
            view.getMapAsync { map ->
                mapRef = map

                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0))
                    .zoom(1.5)
                    .build()

                map.setStyle(Style.Builder().fromUri(DARK_MAP_STYLE)) { style ->
                    // 1. Country fill source + layer
                    val initialGeoJson = loadCountriesGeoJson(context, state.scores)
                    val cSrc = GeoJsonSource(COUNTRIES_SOURCE_ID, initialGeoJson)
                    style.addSource(cSrc)
                    countriesSource = cSrc

                    val fillLayer = FillLayer(COUNTRY_FILL_LAYER, COUNTRIES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.fillColor(buildHeatmapColorExpression()),
                            PropertyFactory.fillOpacity(0.80f),
                        )
                    }
                    style.addLayer(fillLayer)

                    // 2. Country outline layer (subtle)
                    val outlineLayer = FillLayer(COUNTRY_OUTLINE_LAYER, COUNTRIES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.fillOpacity(0f),
                            PropertyFactory.fillOutlineColor(Expression.rgba(255.0, 255.0, 255.0, 0.10)),
                        )
                    }
                    style.addLayerAbove(outlineLayer, COUNTRY_FILL_LAYER)

                    // 3. Events source + circle layer
                    val eSrc = GeoJsonSource(EVENTS_SOURCE_ID, state.eventGeoJson)
                    style.addSource(eSrc)
                    eventsSource = eSrc

                    val circleLayer = CircleLayer(EVENTS_CIRCLE_LAYER, EVENTS_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.circleRadius(
                                Expression.step(
                                    Expression.get("severity"),
                                    Expression.literal(5f),
                                    Expression.stop("low", 6f),
                                    Expression.stop("medium", 9f),
                                    Expression.stop("high", 13f),
                                    Expression.stop("critical", 16f),
                                )
                            ),
                            PropertyFactory.circleColor(
                                Expression.match(
                                    Expression.get("type"),
                                    Expression.literal("conflict"), Expression.rgb(255.0, 50.0, 50.0),
                                    Expression.literal("earthquake"), Expression.rgb(255.0, 150.0, 30.0),
                                    Expression.literal("fire"), Expression.rgb(255.0, 200.0, 0.0),
                                    Expression.rgb(150.0, 150.0, 150.0),
                                )
                            ),
                            PropertyFactory.circleOpacity(0.85f),
                            PropertyFactory.circleStrokeWidth(1.5f),
                            PropertyFactory.circleStrokeColor(Expression.rgba(0.0, 0.0, 0.0, 0.5)),
                        )
                    }
                    style.addLayerAbove(circleLayer, COUNTRY_OUTLINE_LAYER)

                    styleReady = true

                    // Country tap listener
                    map.addOnMapClickListener { point ->
                        val screenPoint = map.projection.toScreenLocation(point)
                        val features = map.queryRenderedFeatures(screenPoint, COUNTRY_FILL_LAYER)
                        val feature = features.firstOrNull()
                        val iso = feature?.getStringProperty("ISO_A2")
                            ?: feature?.getStringProperty("iso_a2")
                        if (!iso.isNullOrBlank()) {
                            onCountryClick(iso)
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }

        // Stats bar — top center
        state.stats?.let { stats ->
            StatsBar(
                articlesCount = stats.totalArticles24h,
                sourcesCount = stats.sourcesActive,
                lastUpdated = state.lastUpdated,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            )
        }

        // Auto-refresh countdown badge — top left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgElevated.copy(alpha = 0.88f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            val mins = countdown / 60
            val secs = countdown % 60
            val label = if (mins > 0) "Refreshing in ${mins}m ${secs}s" else "Refreshing in ${secs}s"
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }

        // Connection status badge — top right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgElevated.copy(alpha = 0.88f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (state.isConnected) GreenOk else RedCritical)
            )
            Text(
                text = if (state.isConnected) "Live" else "Disconnected",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.isConnected) GreenOk else RedCritical,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
            )
        }

        // Loading shimmer overlay
        if (state.isLoading && state.scores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDeep.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = CyanPrimary,
                    strokeWidth = 3.dp,
                )
            }
        }

        // Error banner
        state.error?.let { err ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OrangeAlert.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = err,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Refresh FAB — bottom right
        FloatingActionButton(
            onClick = { vm.refresh() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = BgElevated,
            elevation = FloatingActionButtonDefaults.elevation(6.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyanPrimary)
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun loadCountriesGeoJson(context: android.content.Context, scores: Map<String, Float>): String {
    return try {
        val stream: InputStream = context.assets.open("countries_simple.geojson")
        val raw = stream.readBytes().toString(StandardCharsets.UTF_8)
        stream.close()
        injectScores(raw, scores)
    } catch (e: Exception) {
        EMPTY_FEATURE_COLLECTION
    }
}

private fun injectScores(geojsonText: String, scores: Map<String, Float>): String {
    return try {
        val fc = JSONObject(geojsonText)
        val features = fc.getJSONArray("features")
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val props = feature.getJSONObject("properties")
            val code = props.optString("ISO_A2", props.optString("iso_a2", ""))
            props.put("score", (scores[code] ?: 0f).toDouble())
        }
        fc.toString()
    } catch (e: Exception) {
        geojsonText
    }
}

/**
 * Vivid dark-matter heatmap gradient:
 * dark blue (no data) → deep navy → burnt orange → vivid orange-red → crimson
 */
private fun buildHeatmapColorExpression(): Expression {
    return Expression.interpolate(
        Expression.linear(),
        Expression.get("score"),
        Expression.stop(0.0,  Expression.rgb(10.0,  14.0, 26.0)),   // near-black blue
        Expression.stop(0.15, Expression.rgb(20.0,  50.0, 80.0)),   // deep navy
        Expression.stop(0.4,  Expression.rgb(100.0, 50.0, 10.0)),   // burnt orange
        Expression.stop(0.7,  Expression.rgb(220.0, 80.0, 20.0)),   // vivid orange-red
        Expression.stop(1.0,  Expression.rgb(255.0, 20.0, 20.0)),   // crimson
    )
}
