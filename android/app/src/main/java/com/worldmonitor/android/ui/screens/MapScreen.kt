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
import com.worldmonitor.android.viewmodel.MapEventInfo
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
import kotlin.math.sin

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

    val mapView = remember { MapView(context).also { it.onCreate(Bundle()) } }

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var eventsSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    // Countdown badge
    var countdown by remember { mutableIntStateOf(refreshIntervalSeconds) }
    LaunchedEffect(refreshIntervalSeconds, state.lastUpdated) {
        countdown = refreshIntervalSeconds
        while (countdown > 0) { delay(1_000L); countdown-- }
    }

    // Ensure MapView is properly started when this composable enters the screen
    LaunchedEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
    }

    // Forward Activity/NavBackStackEntry lifecycle events to MapView
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
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Update heatmap color expression when scores change
    LaunchedEffect(state.scores, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            (style.getLayer(COUNTRY_FILL_LAYER) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(buildHeatmapColorExpression(state.scores))
            )
        }
    }

    // Glistening heatmap: slowly animate fill opacity in a sine wave for a living-light effect
    LaunchedEffect(styleReady) {
        if (!styleReady) return@LaunchedEffect
        var t = 0.0
        while (true) {
            val alpha = (0.72f + 0.28f * sin(t).toFloat()).coerceIn(0f, 1f)
            mapRef?.getStyle { style ->
                (style.getLayer(COUNTRY_FILL_LAYER) as? FillLayer)?.setProperties(
                    PropertyFactory.fillOpacity(alpha)
                )
            }
            t += 0.08  // ~2.5 second full cycle at 100ms tick
            delay(100L)
        }
    }

    // Update event markers
    LaunchedEffect(state.eventGeoJson, styleReady) {
        val src = eventsSource ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        src.setGeoJson(state.eventGeoJson)
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        ) { view ->
            if (mapRef != null) return@AndroidView
            view.getMapAsync { map ->
                mapRef = map

                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0))
                    .zoom(1.5)
                    .build()

                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isCompassEnabled = false
                    isLogoEnabled = false
                    isAttributionEnabled = false
                }

                map.setStyle(Style.Builder().fromUri(DARK_MAP_STYLE)) { style ->

                    // Attempt globe projection — gives a spherical earth look
                    try {
                        val projClass = Class.forName("org.maplibre.android.style.projection.StyleProjection")
                        val nameClass = Class.forName("org.maplibre.android.style.projection.StyleProjectionName")
                        val globeValue = nameClass.enumConstants?.firstOrNull { it.toString().equals("globe", ignoreCase = true) }
                        if (globeValue != null) {
                            val constructor = projClass.getConstructor(nameClass)
                            val projection = constructor.newInstance(globeValue)
                            style.javaClass.getMethod("setProjection", projClass).invoke(style, projection)
                        }
                    } catch (_: Exception) {
                        // Globe projection not available in this build — flat map is fine
                    }

                    val rawGeoJson = sCachedGeoJson ?: run {
                        try {
                            context.assets.open("countries_simple.geojson")
                                .readBytes().toString(Charsets.UTF_8)
                                .also { sCachedGeoJson = it }
                        } catch (_: Exception) { EMPTY_FEATURE_COLLECTION }
                    }

                    val cSrc = GeoJsonSource(COUNTRIES_SOURCE_ID, rawGeoJson)
                    style.addSource(cSrc)

                    // Heatmap fill — glistening animation drives opacity via separate LaunchedEffect
                    val fillLayer = FillLayer(COUNTRY_FILL_LAYER, COUNTRIES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.fillColor(buildHeatmapColorExpression(state.scores)),
                            PropertyFactory.fillOpacity(1.0f),
                        )
                    }
                    style.addLayer(fillLayer)

                    // Country borders
                    val outlineLayer = FillLayer(COUNTRY_OUTLINE_LAYER, COUNTRIES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.fillOpacity(0f),
                            PropertyFactory.fillOutlineColor(Expression.rgba(255.0, 255.0, 255.0, 0.15)),
                        )
                    }
                    style.addLayerAbove(outlineLayer, COUNTRY_FILL_LAYER)

                    // Events source + circles
                    val eSrc = GeoJsonSource(EVENTS_SOURCE_ID, state.eventGeoJson)
                    style.addSource(eSrc)
                    eventsSource = eSrc

                    val circleLayer = CircleLayer(EVENTS_CIRCLE_LAYER, EVENTS_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.circleRadius(
                                Expression.match(
                                    Expression.get("severity"),
                                    Expression.literal("low"),      Expression.literal(6f),
                                    Expression.literal("medium"),   Expression.literal(10f),
                                    Expression.literal("high"),     Expression.literal(15f),
                                    Expression.literal("critical"), Expression.literal(20f),
                                    Expression.literal(7f),
                                )
                            ),
                            PropertyFactory.circleColor(
                                Expression.match(
                                    Expression.get("type"),
                                    Expression.literal("conflict"),   Expression.rgb(255.0, 50.0,  50.0),
                                    Expression.literal("earthquake"), Expression.rgb(255.0, 165.0, 30.0),
                                    Expression.literal("fire"),       Expression.rgb(255.0, 210.0, 0.0),
                                    Expression.rgb(160.0, 160.0, 160.0),
                                )
                            ),
                            PropertyFactory.circleOpacity(0.92f),
                            PropertyFactory.circleStrokeWidth(1.8f),
                            PropertyFactory.circleStrokeColor(Expression.rgba(0.0, 0.0, 0.0, 0.55)),
                            PropertyFactory.circleBlur(0.1f),
                            // Pulse-like stroke for visibility
                            PropertyFactory.circleStrokeOpacity(0.8f),
                        )
                    }
                    style.addLayerAbove(circleLayer, COUNTRY_OUTLINE_LAYER)

                    styleReady = true

                    // Click: check event circles first (smaller target), then country fill
                    map.addOnMapClickListener { point ->
                        val px = map.projection.toScreenLocation(point)
                        // Larger hit box (50dp) catches small countries and events better
                        val hitBox = android.graphics.RectF(px.x - 50f, px.y - 50f, px.x + 50f, px.y + 50f)

                        // 1. Event circles — tighter box for precision
                        val eventBox = android.graphics.RectF(px.x - 30f, px.y - 30f, px.x + 30f, px.y + 30f)
                        val eventFeatures = map.queryRenderedFeatures(eventBox, EVENTS_CIRCLE_LAYER)
                        if (eventFeatures.isNotEmpty()) {
                            val f = eventFeatures.first()
                            val eventInfo = MapEventInfo(
                                type      = f.getStringProperty("type") ?: "unknown",
                                severity  = f.getStringProperty("severity") ?: "low",
                                title     = f.getStringProperty("title") ?: "",
                                magnitude = f.getNumberProperty("magnitude")?.toDouble() ?: 0.0,
                            )
                            vm.selectEvent(eventInfo)
                            return@addOnMapClickListener true
                        }

                        // 2. Country fill
                        val countryFeatures = map.queryRenderedFeatures(hitBox, COUNTRY_FILL_LAYER)
                        val iso = countryFeatures.firstOrNull()
                            ?.getStringProperty("ISO3166-1-Alpha-2")
                            ?.takeIf { it.isNotBlank() && it != "-99" && it != "-1" }

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

        // ── Server connection indicator — top right ───────────────────────
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

        // ── Event info card ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.selectedEvent != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
        ) {
            state.selectedEvent?.let { event ->
                EventInfoCard(
                    event = event,
                    onDismiss = { vm.selectEvent(null) },
                )
            }
        }

        // ── Country info card — slides up on tap ──────────────────────────
        AnimatedVisibility(
            visible = state.selectedCountry != null && state.selectedEvent == null,
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

// ── Event info card ───────────────────────────────────────────────────────────

@Composable
private fun EventInfoCard(
    event: MapEventInfo,
    onDismiss: () -> Unit,
) {
    val (typeLabel, typeColor) = when (event.type) {
        "earthquake" -> "Earthquake" to OrangeAlert
        "fire"       -> "Wildfire"   to Color(0xFFFFD700)
        "conflict"   -> "Conflict"   to RedCritical
        else         -> event.type.replaceFirstChar { it.uppercase() } to TextSecondary
    }
    val severityColor = when (event.severity) {
        "low"      -> GreenOk
        "medium"   -> OrangeAlert
        "high"     -> Color(0xFFFF6B35)
        "critical" -> RedCritical
        else       -> TextSecondary
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
                        text = typeLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = event.severity.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = severityColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                }
            }

            if (event.title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 3,
                )
            }

            if (event.type == "earthquake" && event.magnitude > 0.0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CountryStat(
                        value = "M%.1f".format(event.magnitude),
                        label = "magnitude",
                        color = OrangeAlert,
                    )
                }
            }
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

private fun buildHeatmapColorExpression(scores: Map<String, Float>): Expression {
    if (scores.isEmpty()) return Expression.rgba(0.0, 0.0, 0.0, 0.0)

    val parts = mutableListOf<Expression>()
    parts.add(Expression.get("ISO3166-1-Alpha-2"))
    scores.forEach { (iso, score) ->
        parts.add(Expression.literal(iso))
        parts.add(scoreToRgba(score))
    }
    parts.add(Expression.rgba(0.0, 0.0, 0.0, 0.0))

    return Expression.match(*parts.toTypedArray())
}

/**
 * Maps a country threat score (0–1) to a rich RGBA color.
 * Colors shift from cool blue (low activity) through amber and into hot red (critical).
 * The opacity values are deliberately high to make the glistening animation visible.
 */
private fun scoreToRgba(score: Float): Expression = when {
    score <= 0f   -> Expression.rgba(0.0,   0.0,   0.0,   0.0)
    score < 0.10f -> Expression.rgba(10.0,  40.0,  120.0, 0.55)
    score < 0.25f -> Expression.rgba(20.0,  60.0,  160.0, 0.68)
    score < 0.40f -> Expression.rgba(100.0, 50.0,  10.0,  0.75)
    score < 0.55f -> Expression.rgba(180.0, 70.0,  15.0,  0.82)
    score < 0.70f -> Expression.rgba(230.0, 90.0,  20.0,  0.88)
    score < 0.85f -> Expression.rgba(255.0, 45.0,  15.0,  0.92)
    else          -> Expression.rgba(255.0, 10.0,  10.0,  0.97)
}
