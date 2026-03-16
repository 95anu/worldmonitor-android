package com.worldmonitor.android.ui.screens

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.worldmonitor.android.ui.theme.BgCard
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
import java.nio.charset.StandardCharsets

private const val DARK_MAP_STYLE = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val COUNTRIES_SOURCE_ID = "countries-source"
private const val EVENTS_SOURCE_ID = "events-source"
private const val COUNTRY_FILL_LAYER = "country-fill"
private const val COUNTRY_OUTLINE_LAYER = "country-fill-outline"
private const val EVENTS_CIRCLE_LAYER = "events-circles"
private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

// Module-level cache: read from assets once for the process lifetime
private var sCachedGeoJson: String? = null

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

    // Fix #1 — call onCreate() immediately so MapView initialises before lifecycle events fire
    val mapView = remember { MapView(context).also { it.onCreate(Bundle()) } }

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var countriesSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var eventsSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    // Countdown badge
    var countdown by remember { mutableIntStateOf(refreshIntervalSeconds) }
    LaunchedEffect(refreshIntervalSeconds, state.lastUpdated) {
        countdown = refreshIntervalSeconds
        while (countdown > 0) { delay(1_000L); countdown-- }
    }

    // Fix #2 — update heatmap by swapping the fill-layer expression, NOT by re-loading GeoJSON
    LaunchedEffect(state.scores, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            (style.getLayer(COUNTRY_FILL_LAYER) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(buildHeatmapColorExpression(state.scores))
            )
        }
    }

    // Update event markers
    LaunchedEffect(state.eventGeoJson, styleReady) {
        val src = eventsSource ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        src.setGeoJson(state.eventGeoJson)
    }

    // Fix #3 — only forward lifecycle changes; onCreate is handled in factory above
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
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
            // Guard — getMapAsync callback fires on every recompose; only set up once
            if (mapRef != null) return@AndroidView
            view.getMapAsync { map ->
                mapRef = map

                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0))
                    .zoom(1.5)
                    .build()

                // Cleaner UX: disable rotation (confusing on a world map), hide MapLibre branding
                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isCompassEnabled = false
                    isLogoEnabled = false
                    isAttributionEnabled = false
                }

                map.setStyle(Style.Builder().fromUri(DARK_MAP_STYLE)) { style ->

                    // Load raw GeoJSON once from assets (module-level cache after first read)
                    val rawGeoJson = sCachedGeoJson ?: run {
                        try {
                            context.assets.open("countries_simple.geojson")
                                .readBytes().toString(StandardCharsets.UTF_8)
                                .also { sCachedGeoJson = it }
                        } catch (_: Exception) { EMPTY_FEATURE_COLLECTION }
                    }

                    // Country fill source — raw GeoJSON, no score data needed here
                    val cSrc = GeoJsonSource(COUNTRIES_SOURCE_ID, rawGeoJson)
                    style.addSource(cSrc)
                    countriesSource = cSrc

                    // Heatmap fill layer — color driven purely by expression (no GeoJSON mutation)
                    val fillLayer = FillLayer(COUNTRY_FILL_LAYER, COUNTRIES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.fillColor(buildHeatmapColorExpression(state.scores)),
                            PropertyFactory.fillOpacity(1.0f),
                        )
                    }
                    style.addLayer(fillLayer)

                    // Subtle country borders
                    val outlineLayer = FillLayer(COUNTRY_OUTLINE_LAYER, COUNTRIES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.fillOpacity(0f),
                            PropertyFactory.fillOutlineColor(Expression.rgba(255.0, 255.0, 255.0, 0.12)),
                        )
                    }
                    style.addLayerAbove(outlineLayer, COUNTRY_FILL_LAYER)

                    // Events source + circle layer
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
                                    Expression.stop("high", 14f),
                                    Expression.stop("critical", 18f),
                                )
                            ),
                            PropertyFactory.circleColor(
                                Expression.match(
                                    Expression.get("type"),
                                    Expression.literal("conflict"),   Expression.rgb(255.0, 50.0,  50.0),
                                    Expression.literal("earthquake"), Expression.rgb(255.0, 150.0, 30.0),
                                    Expression.literal("fire"),       Expression.rgb(255.0, 200.0, 0.0),
                                    Expression.rgb(160.0, 160.0, 160.0),
                                )
                            ),
                            PropertyFactory.circleOpacity(0.92f),
                            PropertyFactory.circleStrokeWidth(1.5f),
                            PropertyFactory.circleStrokeColor(Expression.rgba(0.0, 0.0, 0.0, 0.55)),
                            PropertyFactory.circleBlur(0.15f),
                        )
                    }
                    style.addLayerAbove(circleLayer, COUNTRY_OUTLINE_LAYER)

                    styleReady = true

                    // Fix #4 — use a RectF (~40dp box) instead of a single pixel for hit testing
                    map.addOnMapClickListener { point ->
                        val px = map.projection.toScreenLocation(point)
                        val hitBox = android.graphics.RectF(px.x - 40f, px.y - 40f, px.x + 40f, px.y + 40f)
                        val features = map.queryRenderedFeatures(hitBox, COUNTRY_FILL_LAYER)
                        val iso = features.firstOrNull()
                            ?.let { feat ->
                                feat.getStringProperty("ISO_A2")
                                    ?.takeIf { it.isNotBlank() && it != "-99" && it != "-1" }
                                    ?: feat.getStringProperty("iso_a2")
                                    ?.takeIf { it.isNotBlank() && it != "-99" && it != "-1" }
                            }
                        if (!iso.isNullOrBlank()) {
                            vm.selectCountry(iso)
                            true
                        } else {
                            vm.selectCountry(null)
                            false
                        }
                    }
                }
            }
        }

        // ── Stats bar — top centre ────────────────────────────────────────
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

        // ── Countdown badge — top left ────────────────────────────────────
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
            Text(
                text = if (mins > 0) "↻ ${mins}m ${secs}s" else "↻ ${secs}s",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }

        // ── Live/Offline indicator — top right ───────────────────────────
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
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (state.isConnected) GreenOk else RedCritical)
            )
            Text(
                text = if (state.isConnected) "Live" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.isConnected) GreenOk else RedCritical,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
            )
        }

        // ── Initial loading spinner ───────────────────────────────────────
        if (state.isLoading && state.scores.isEmpty()) {
            Box(
                Modifier.fillMaxSize().background(BgDeep.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = CyanPrimary, strokeWidth = 3.dp)
            }
        }

        // ── Error banner ──────────────────────────────────────────────────
        state.error?.let { err ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OrangeAlert.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(err, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }

        // ── Country info card — slides up on tap ──────────────────────────
        AnimatedVisibility(
            visible = state.selectedCountry != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
        ) {
            state.selectedCountry?.let { iso ->
                CountryInfoCard(
                    iso = iso,
                    score = state.selectedCountryScore,
                    articles24h = state.selectedCountryArticles,
                    events7d = state.selectedCountryEvents,
                    onViewNews = {
                        vm.selectCountry(null)
                        onCountryClick(iso)
                    },
                    onDismiss = { vm.selectCountry(null) },
                )
            }
        }

        // ── Refresh FAB ───────────────────────────────────────────────────
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

// ── Country info card ─────────────────────────────────────────────────────────

@Composable
private fun CountryInfoCard(
    iso: String,
    score: Float,
    articles24h: Int?,
    events7d: Int?,
    onViewNews: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (threatLabel, threatColor) = when {
        score <= 0.0f -> "No Activity" to TextSecondary
        score < 0.15f -> "Minimal"     to GreenOk
        score < 0.40f -> "Moderate"    to GreenOk
        score < 0.65f -> "Elevated"    to OrangeAlert
        score < 0.85f -> "High"        to OrangeAlert
        else           -> "Critical"   to RedCritical
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = iso.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = threatLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = threatColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Score bar
            LinearProgressIndicator(
                progress = { score.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = threatColor,
                trackColor = BgCard,
            )

            if (articles24h != null || events7d != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    articles24h?.let {
                        CountryStat(value = "$it", label = "articles 24h", color = CyanPrimary)
                    }
                    events7d?.let {
                        CountryStat(value = "$it", label = "events 7d", color = OrangeAlert)
                    }
                }
            } else {
                // Loading placeholder
                Spacer(Modifier.height(10.dp))
                Text("Loading details…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onViewNews,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Article, null, tint = BgDeep, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "News from $iso",
                    color = BgDeep,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CountryStat(value: String, label: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Build a MapLibre `match` expression that maps ISO_A2 → rgba color.
 * This avoids any GeoJSON mutation — only the layer expression is updated when scores change.
 * Countries not present in [scores] default to fully transparent (dark basemap shows through).
 */
private fun buildHeatmapColorExpression(scores: Map<String, Float>): Expression {
    if (scores.isEmpty()) return Expression.rgba(0.0, 0.0, 0.0, 0.0)

    val parts = mutableListOf<Expression>()
    // Input: prefer ISO_A2, fall back to iso_a2
    parts.add(Expression.coalesce(Expression.get("ISO_A2"), Expression.get("iso_a2")))
    scores.forEach { (iso, score) ->
        parts.add(Expression.literal(iso))
        parts.add(scoreToRgba(score))
    }
    // Default: transparent → dark basemap shows through where there's no activity
    parts.add(Expression.rgba(0.0, 0.0, 0.0, 0.0))

    return Expression.match(*parts.toTypedArray())
}

private fun scoreToRgba(score: Float): Expression = when {
    score <= 0f   -> Expression.rgba(0.0,   0.0,   0.0,   0.0)
    score < 0.10f -> Expression.rgba(10.0,  30.0,  70.0,  0.45)
    score < 0.25f -> Expression.rgba(20.0,  50.0,  100.0, 0.60)
    score < 0.40f -> Expression.rgba(90.0,  45.0,  15.0,  0.68)
    score < 0.55f -> Expression.rgba(160.0, 65.0,  20.0,  0.75)
    score < 0.70f -> Expression.rgba(220.0, 85.0,  25.0,  0.82)
    score < 0.85f -> Expression.rgba(255.0, 40.0,  20.0,  0.88)
    else          -> Expression.rgba(255.0, 10.0,  10.0,  0.95)
}
