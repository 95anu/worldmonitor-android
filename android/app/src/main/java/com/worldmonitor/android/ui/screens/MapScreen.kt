package com.worldmonitor.android.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmonitor.android.ui.theme.BgCard
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.BgElevated
import com.worldmonitor.android.ui.theme.BgSurface
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.GlassBorder
import com.worldmonitor.android.ui.theme.GreenOk
import com.worldmonitor.android.ui.theme.RedCritical
import com.worldmonitor.android.ui.theme.TextPrimary
import com.worldmonitor.android.ui.theme.TextSecondary
import com.worldmonitor.android.viewmodel.MapEventInfo
import com.worldmonitor.android.viewmodel.MapViewModel
import com.worldmonitor.android.viewmodel.PULSE_FRAMES
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.TileSet
import kotlin.math.sin

private const val DARK_MAP_STYLE   = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val TERRAIN_SOURCE   = "terrain-dem"
private const val HILLSHADE_LAYER  = "hillshade-layer"
private const val COUNTRIES_SOURCE = "countries-source"
private const val EVENTS_SOURCE    = "events-source"
private const val COUNTRY_FILL     = "country-fill"
private const val COUNTRY_OUTLINE  = "country-fill-outline"
private const val HEATMAP_LAYER    = "events-heatmap"
private const val EVENTS_SYMBOLS   = "events-symbols"
private const val EMPTY_FC         = """{"type":"FeatureCollection","features":[]}"""

private var sCachedGeoJson: String? = null

// Approximate geographic centroids for auto-camera on first load
private val COUNTRY_CENTROIDS = mapOf(
    "AF" to LatLng(33.93, 67.71),  "AL" to LatLng(41.15, 20.17),  "DZ" to LatLng(28.03, 1.66),
    "AD" to LatLng(42.55, 1.60),   "AO" to LatLng(-11.20, 17.87), "AG" to LatLng(17.06, -61.80),
    "AR" to LatLng(-38.42, -63.62),"AM" to LatLng(40.07, 45.04),  "AU" to LatLng(-25.27, 133.78),
    "AT" to LatLng(47.52, 14.55),  "AZ" to LatLng(40.14, 47.58),  "BS" to LatLng(25.03, -77.40),
    "BH" to LatLng(26.00, 50.55),  "BD" to LatLng(23.68, 90.36),  "BB" to LatLng(13.19, -59.54),
    "BY" to LatLng(53.71, 27.95),  "BE" to LatLng(50.50, 4.47),   "BZ" to LatLng(17.19, -88.50),
    "BJ" to LatLng(9.31, 2.32),    "BT" to LatLng(27.51, 90.43),  "BO" to LatLng(-16.29, -63.59),
    "BA" to LatLng(43.92, 17.68),  "BW" to LatLng(-22.33, 24.68), "BR" to LatLng(-14.24, -51.93),
    "BN" to LatLng(4.54, 114.73),  "BG" to LatLng(42.73, 25.49),  "BF" to LatLng(12.36, -1.53),
    "BI" to LatLng(-3.37, 29.92),  "CV" to LatLng(16.00, -24.01), "KH" to LatLng(12.57, 104.99),
    "CM" to LatLng(3.85, 11.50),   "CA" to LatLng(56.13, -106.35),"CF" to LatLng(6.61, 20.94),
    "TD" to LatLng(15.45, 18.73),  "CL" to LatLng(-35.68, -71.54),"CN" to LatLng(35.86, 104.20),
    "CO" to LatLng(4.57, -74.30),  "KM" to LatLng(-11.65, 43.33), "CG" to LatLng(-0.23, 15.83),
    "CD" to LatLng(-4.04, 21.76),  "CR" to LatLng(9.75, -83.75),  "CI" to LatLng(7.54, -5.55),
    "HR" to LatLng(45.10, 15.20),  "CU" to LatLng(21.52, -77.78), "CY" to LatLng(35.13, 33.43),
    "CZ" to LatLng(49.82, 15.47),  "DK" to LatLng(56.26, 9.50),   "DJ" to LatLng(11.83, 42.59),
    "DO" to LatLng(18.74, -70.16), "EC" to LatLng(-1.83, -78.18), "EG" to LatLng(26.82, 30.80),
    "SV" to LatLng(13.79, -88.90), "GQ" to LatLng(1.65, 10.27),   "ER" to LatLng(15.18, 39.78),
    "EE" to LatLng(58.60, 25.01),  "ET" to LatLng(9.15, 40.49),   "FJ" to LatLng(-16.58, 179.41),
    "FI" to LatLng(61.92, 25.75),  "FR" to LatLng(46.23, 2.21),   "GA" to LatLng(-0.80, 11.61),
    "GM" to LatLng(13.44, -15.31), "GE" to LatLng(42.32, 43.36),  "DE" to LatLng(51.17, 10.45),
    "GH" to LatLng(7.95, -1.02),   "GR" to LatLng(39.07, 21.82),  "GT" to LatLng(15.78, -90.23),
    "GN" to LatLng(9.95, -11.24),  "GW" to LatLng(11.80, -15.18), "GY" to LatLng(4.86, -58.93),
    "HT" to LatLng(18.97, -72.29), "HN" to LatLng(15.20, -86.24), "HU" to LatLng(47.16, 19.50),
    "IS" to LatLng(64.96, -19.02), "IN" to LatLng(20.59, 78.96),  "ID" to LatLng(-0.79, 113.92),
    "IR" to LatLng(32.43, 53.69),  "IQ" to LatLng(33.22, 43.68),  "IE" to LatLng(53.41, -8.24),
    "IL" to LatLng(31.05, 34.85),  "IT" to LatLng(41.87, 12.57),  "JM" to LatLng(18.11, -77.30),
    "JP" to LatLng(36.20, 138.25), "JO" to LatLng(30.59, 36.24),  "KZ" to LatLng(48.02, 66.92),
    "KE" to LatLng(-0.02, 37.91),  "KP" to LatLng(40.34, 127.51), "KR" to LatLng(35.91, 127.77),
    "KW" to LatLng(29.31, 47.48),  "KG" to LatLng(41.20, 74.77),  "LA" to LatLng(19.86, 102.50),
    "LV" to LatLng(56.88, 24.60),  "LB" to LatLng(33.85, 35.86),  "LS" to LatLng(-29.61, 28.23),
    "LR" to LatLng(6.43, -9.43),   "LY" to LatLng(26.34, 17.23),  "LT" to LatLng(55.17, 23.88),
    "LU" to LatLng(49.82, 6.13),   "MG" to LatLng(-18.77, 46.87), "MW" to LatLng(-13.25, 34.30),
    "MY" to LatLng(4.21, 108.00),  "MV" to LatLng(3.20, 73.22),   "ML" to LatLng(17.57, -3.99),
    "MT" to LatLng(35.94, 14.38),  "MR" to LatLng(21.01, -10.94), "MU" to LatLng(-20.35, 57.55),
    "MX" to LatLng(23.63, -102.55),"MD" to LatLng(47.41, 28.37),  "MN" to LatLng(46.86, 103.85),
    "ME" to LatLng(42.71, 19.37),  "MA" to LatLng(31.79, -7.09),  "MZ" to LatLng(-18.67, 35.53),
    "MM" to LatLng(21.91, 95.96),  "NA" to LatLng(-22.96, 18.49), "NP" to LatLng(28.39, 84.12),
    "NL" to LatLng(52.13, 5.29),   "NZ" to LatLng(-40.90, 174.89),"NI" to LatLng(12.87, -85.21),
    "NE" to LatLng(17.61, 8.08),   "NG" to LatLng(9.08, 8.68),    "MK" to LatLng(41.61, 21.75),
    "NO" to LatLng(60.47, 8.47),   "OM" to LatLng(21.51, 55.92),  "PK" to LatLng(30.38, 69.35),
    "PA" to LatLng(8.54, -80.78),  "PG" to LatLng(-6.31, 143.96), "PY" to LatLng(-23.44, -58.44),
    "PE" to LatLng(-9.19, -75.02), "PH" to LatLng(12.88, 121.77), "PL" to LatLng(51.92, 19.15),
    "PT" to LatLng(39.40, -8.22),  "QA" to LatLng(25.35, 51.18),  "RO" to LatLng(45.94, 24.97),
    "RU" to LatLng(61.52, 105.32), "RW" to LatLng(-1.94, 29.87),  "SA" to LatLng(23.89, 45.08),
    "SN" to LatLng(14.50, -14.45), "RS" to LatLng(44.02, 21.01),  "SC" to LatLng(-4.68, 55.49),
    "SL" to LatLng(8.46, -11.78),  "SG" to LatLng(1.35, 103.82),  "SK" to LatLng(48.67, 19.70),
    "SI" to LatLng(46.15, 14.99),  "SO" to LatLng(5.15, 46.20),   "ZA" to LatLng(-30.56, 22.94),
    "SS" to LatLng(6.88, 31.57),   "ES" to LatLng(40.46, -3.75),  "LK" to LatLng(7.87, 80.77),
    "SD" to LatLng(12.86, 30.22),  "SR" to LatLng(3.92, -56.03),  "SE" to LatLng(60.13, 18.64),
    "CH" to LatLng(46.82, 8.23),   "SY" to LatLng(34.80, 38.99),  "TW" to LatLng(23.70, 121.00),
    "TJ" to LatLng(38.86, 71.28),  "TZ" to LatLng(-6.37, 34.89),  "TH" to LatLng(15.87, 100.99),
    "TL" to LatLng(-8.87, 125.73), "TG" to LatLng(8.62, 0.82),    "TT" to LatLng(10.69, -61.22),
    "TN" to LatLng(33.89, 9.54),   "TR" to LatLng(38.96, 35.24),  "TM" to LatLng(38.97, 59.56),
    "UG" to LatLng(1.37, 32.29),   "UA" to LatLng(48.38, 31.17),  "AE" to LatLng(23.42, 53.85),
    "GB" to LatLng(55.38, -3.44),  "US" to LatLng(37.09, -95.71), "UY" to LatLng(-32.52, -55.77),
    "UZ" to LatLng(41.38, 64.59),  "VE" to LatLng(6.42, -66.59),  "VN" to LatLng(14.06, 108.28),
    "YE" to LatLng(15.55, 48.52),  "ZM" to LatLng(-13.13, 27.85), "ZW" to LatLng(-19.02, 29.15),
)

@Composable
fun MapScreen(
    vm: MapViewModel = viewModel(),
    onCountryClick: (String) -> Unit = {},
) {
    val state by vm.uiState.collectAsState()
    val mapRef by vm.mapLibreMap.collectAsState()
    val styleReady by vm.mapStyleReady.collectAsState()
    val refreshIntervalSeconds by vm.refreshIntervalSeconds.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    remember { MapLibre.getInstance(context) }
    val mapView = remember { vm.getOrCreateMapView(context) }

    var countdown by remember { mutableIntStateOf(refreshIntervalSeconds) }
    LaunchedEffect(refreshIntervalSeconds, state.lastUpdated) {
        countdown = refreshIntervalSeconds
        while (countdown > 0) { delay(1_000L); countdown-- }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START  -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                Lifecycle.Event.ON_STOP   -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))  mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
        }
    }

    // Update country heatmap colors when scores arrive
    LaunchedEffect(state.scores, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            (style.getLayer(COUNTRY_FILL) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(buildHeatmapColorExpression(state.scores))
            )
        }
    }

    // Slow-pulse country fill opacity
    LaunchedEffect(styleReady) {
        if (!styleReady) return@LaunchedEffect
        var t = 0.0
        while (true) {
            val alpha = (0.80f + 0.18f * sin(t).toFloat()).coerceIn(0f, 1f)
            mapRef?.getStyle { style ->
                (style.getLayer(COUNTRY_FILL) as? FillLayer)?.setProperties(
                    PropertyFactory.fillOpacity(alpha)
                )
            }
            t += 0.010; delay(100L)
        }
    }

    // Heatmap breathing — very slow intensity oscillation for organic feel
    LaunchedEffect(styleReady) {
        if (!styleReady) return@LaunchedEffect
        var t = 0.0
        while (true) {
            val intensity = (1.0f + 0.12f * sin(t).toFloat())
            mapRef?.getStyle { style ->
                (style.getLayer(HEATMAP_LAYER) as? HeatmapLayer)?.setProperties(
                    PropertyFactory.heatmapIntensity(intensity)
                )
            }
            t += 0.006; delay(160L)
        }
    }

    // Keep event source in sync when initial GeoJSON loads
    LaunchedEffect(state.eventGeoJson, styleReady) {
        if (!styleReady) return@LaunchedEffect
        vm.eventsSource?.setGeoJson(state.eventGeoJson)
    }

    // Start pulse animation clock once style is ready
    LaunchedEffect(styleReady) {
        if (styleReady) vm.startPulseClock()
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {

        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) { view ->
            if (vm.mapLibreMap.value != null) return@AndroidView
            view.getMapAsync { map ->
                vm.mapLibreMap.value = map

                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isCompassEnabled        = false
                    isLogoEnabled           = false
                    isAttributionEnabled    = false
                }

                map.setMinZoomPreference(0.5)

                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0)).zoom(1.2).build()

                map.setStyle(Style.Builder().fromUri(DARK_MAP_STYLE)) { style ->

                    // ── Terrain hillshade ──────────────────────────────────
                    val terrainTiles = TileSet("2.2.0",
                        "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
                    ).apply {
                        encoding = "terrarium"
                        minZoom  = 0f
                        maxZoom  = 15f
                    }
                    style.addSource(RasterDemSource(TERRAIN_SOURCE, terrainTiles, 256))
                    style.addLayer(HillshadeLayer(HILLSHADE_LAYER, TERRAIN_SOURCE).apply {
                        setProperties(
                            PropertyFactory.hillshadeIlluminationDirection(335f),
                            PropertyFactory.hillshadeIlluminationAnchor("map"),
                            PropertyFactory.hillshadeExaggeration(0.28f),
                            PropertyFactory.hillshadeHighlightColor(
                                android.graphics.Color.argb(90, 95, 156, 244)
                            ),
                            PropertyFactory.hillshadeShadowColor(
                                android.graphics.Color.argb(140, 4, 12, 38)
                            ),
                            PropertyFactory.hillshadeAccentColor(
                                android.graphics.Color.argb(55, 26, 70, 160)
                            ),
                        )
                    })

                    // ── Country fill ──────────────────────────────────────
                    val rawGeoJson = sCachedGeoJson ?: run {
                        try { context.assets.open("countries_simple.geojson").readBytes()
                            .toString(Charsets.UTF_8).also { sCachedGeoJson = it }
                        } catch (_: Exception) { EMPTY_FC }
                    }
                    style.addSource(GeoJsonSource(COUNTRIES_SOURCE, rawGeoJson))
                    style.addLayer(FillLayer(COUNTRY_FILL, COUNTRIES_SOURCE).apply {
                        setProperties(
                            PropertyFactory.fillColor(buildHeatmapColorExpression(state.scores)),
                            PropertyFactory.fillOpacity(1.0f),
                        )
                    })
                    style.addLayerAbove(FillLayer(COUNTRY_OUTLINE, COUNTRIES_SOURCE).apply {
                        setProperties(
                            PropertyFactory.fillOpacity(0f),
                            PropertyFactory.fillOutlineColor(Expression.rgba(255.0, 255.0, 255.0, 0.15)),
                        )
                    }, COUNTRY_FILL)

                    // ── Events source ─────────────────────────────────────
                    val eSrc = GeoJsonSource(EVENTS_SOURCE, state.eventGeoJson)
                    style.addSource(eSrc)
                    vm.eventsSource = eSrc

                    // ── Register all pulse icon frames (4 types × 6 frames) ──
                    registerAllPulseIcons(style)

                    // ── Heatmap layer (overview, fades out at z≥9) ────────
                    style.addLayerAbove(buildHeatmapLayer(EVENTS_SOURCE), COUNTRY_OUTLINE)

                    // ── Symbol layer (detailed icons at z≥4) ─────────────
                    style.addLayerAbove(SymbolLayer(EVENTS_SYMBOLS, EVENTS_SOURCE).apply {
                        minZoom = 4f
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon_name")),
                            PropertyFactory.iconSize(
                                Expression.interpolate(
                                    Expression.linear(),
                                    Expression.get("severity_score"),
                                    Expression.literal(0.0), Expression.literal(0.38),
                                    Expression.literal(1.0), Expression.literal(1.05),
                                )
                            ),
                            PropertyFactory.iconOpacity(
                                Expression.interpolate(
                                    Expression.linear(),
                                    Expression.get("hours_ago"),
                                    Expression.literal(0.0),   Expression.literal(1.0),
                                    Expression.literal(168.0), Expression.literal(0.35),
                                )
                            ),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAnchor("center"),
                        )
                    }, HEATMAP_LAYER)

                    // ── Suppress noisy label layers ───────────────────────
                    applyIntelligenceStyle(style)

                    vm.mapStyleReady.value = true

                    // ── Initial camera: India, wide view ──────────────────
                    if (!vm.hasSetInitialCamera) {
                        vm.hasSetInitialCamera = true
                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(20.59, 78.96))
                                    .zoom(2.0).build()
                            ), 1800
                        )
                    }

                    // ── Click: orbs first, then countries ─────────────────
                    map.addOnMapClickListener { point ->
                        val px = map.projection.toScreenLocation(point)
                        val small = android.graphics.RectF(px.x - 35f, px.y - 35f, px.x + 35f, px.y + 35f)
                        val large = android.graphics.RectF(px.x - 55f, px.y - 55f, px.x + 55f, px.y + 55f)

                        val ef = map.queryRenderedFeatures(small, EVENTS_SYMBOLS)
                        if (ef.isNotEmpty()) {
                            val f = ef.first()
                            vm.selectEvent(MapEventInfo(
                                type      = f.getStringProperty("type") ?: "unknown",
                                severity  = f.getStringProperty("severity") ?: "low",
                                title     = f.getStringProperty("title") ?: "",
                                magnitude = f.getNumberProperty("magnitude")?.toDouble() ?: 0.0,
                            ))
                            // Gently drift camera to tapped event
                            map.animateCamera(CameraUpdateFactory.newLatLng(point), 500)
                            return@addOnMapClickListener true
                        }

                        val iso = map.queryRenderedFeatures(large, COUNTRY_FILL).firstOrNull()
                            ?.getStringProperty("ISO3166-1-Alpha-2")
                            ?.takeIf { it.isNotBlank() && it != "-99" && it != "-1" }
                        if (!iso.isNullOrBlank()) { vm.selectCountry(iso); true }
                        else { vm.selectCountry(null); false }
                    }
                }
            }
        }

        // ── Unified intel top bar ──────────────────────────────────────────
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            IntelTopBar(
                isConnected    = state.isConnected,
                totalArticles  = state.stats?.totalArticles24h,
                sourcesActive  = state.stats?.sourcesActive,
                countdown      = countdown,
                showTimeSlider = state.showTimeSlider,
                onTimeFilterToggle = { vm.toggleTimeSlider() },
            )
            AnimatedVisibility(
                visible = state.showTimeSlider,
                enter   = slideInVertically { -it } + fadeIn(),
                exit    = slideOutVertically { -it } + fadeOut(),
            ) {
                TimeFilterOverlay(
                    hours        = state.timeFilterHours,
                    onHoursChange = { vm.onTimeSliderChange(it) },
                )
            }
        }

        // ── Loading ────────────────────────────────────────────────────────
        if (state.isLoading && state.scores.isEmpty()) {
            Box(Modifier.fillMaxSize().background(BgDeep.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanPrimary, strokeWidth = 3.dp)
            }
        }

        // ── Error banner ───────────────────────────────────────────────────
        state.error?.let { err ->
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(8.dp)).background(RedCritical.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(err, color = TextPrimary, style = MaterialTheme.typography.bodySmall) }
        }

        // ── Event info card ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.selectedEvent != null,
            enter   = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) { it } + fadeIn(tween(180)),
            exit    = slideOutVertically(tween(200)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
        ) { state.selectedEvent?.let { EventInfoCard(it) { vm.selectEvent(null) } } }

        // ── Country info card ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.selectedCountry != null && state.selectedEvent == null,
            enter   = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) { it } + fadeIn(tween(180)),
            exit    = slideOutVertically(tween(200)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
        ) {
            state.selectedCountry?.let { iso ->
                CountryInfoCard(
                    iso          = iso,
                    score        = state.selectedCountryScore,
                    articles24h  = state.selectedCountryArticles,
                    events7d     = state.selectedCountryEvents,
                    onViewNews   = { vm.selectCountry(null); onCountryClick(iso) },
                    onDismiss    = { vm.selectCountry(null) },
                )
            }
        }

        // ── Refresh FAB ────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { vm.refresh() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = BgElevated, elevation = FloatingActionButtonDefaults.elevation(6.dp),
        ) { Icon(Icons.Default.Refresh, null, tint = CyanPrimary) }
    }
}

// ── Pulse event type ──────────────────────────────────────────────────────────

private enum class PulseEventType(
    val highlight: Int,
    val core: Int,
    val mid: Int,
    val glow: Int,
) {
    EARTHQUAKE(
        highlight = AndroidColor.argb(255, 180, 230, 255),  // icy cyan highlight
        core      = AndroidColor.argb(255,   0, 180, 255),  // cyan core
        mid       = AndroidColor.argb(255,   0,  90, 150),  // deep cyan shadow
        glow      = AndroidColor.argb(90,    0, 200, 255),  // cyan glow
    ),
    FIRE(
        highlight = AndroidColor.argb(255, 255, 220, 160),  // warm highlight
        core      = AndroidColor.argb(255, 255,  90,  20),  // orange-red core
        mid       = AndroidColor.argb(255, 140,  30,   0),  // deep red shadow
        glow      = AndroidColor.argb(90,  255,  80,  20),  // orange glow
    ),
    CONFLICT(
        highlight = AndroidColor.argb(255, 255, 160, 160),  // pink highlight
        core      = AndroidColor.argb(255, 220,  20,  50),  // deep red core
        mid       = AndroidColor.argb(255, 100,   5,  15),  // very dark red shadow
        glow      = AndroidColor.argb(90,  200,  20,  50),  // red glow
    ),
    DEFAULT(
        highlight = AndroidColor.argb(255, 140, 175, 220),  // muted highlight
        core      = AndroidColor.argb(255,  26,  70, 140),  // dim navy core
        mid       = AndroidColor.argb(255,  10,  30,  75),  // very deep shadow
        glow      = AndroidColor.argb(60,   26,  70, 140),  // navy glow
    );

    val typeKey: String get() = name.lowercase()
}

/**
 * Renders a single animation frame: a glowing orb with an expanding pulse ring.
 * Frame 0 = ring tight around orb; frame PULSE_FRAMES-1 = ring fully expanded and faded.
 */
private fun makePulseIcon(type: PulseEventType, frame: Int, sizePx: Int = 96): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val cv  = AndroidCanvas(bmp)
    val cx  = sizePx / 2f
    val cy  = sizePx / 2f
    val r   = sizePx * 0.22f   // orb radius

    val frac = if (PULSE_FRAMES > 1) frame.toFloat() / (PULSE_FRAMES - 1) else 0f

    // ── Expanding pulse rings ──────────────────────────────────────────────
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = sizePx * 0.038f
    }
    // Inner ring
    val innerRadius = sizePx * (0.30f + frac * 0.14f)
    ringPaint.color = type.glow
    ringPaint.alpha = ((1f - frac) * 160).toInt().coerceIn(0, 255)
    cv.drawCircle(cx, cy, innerRadius, ringPaint)

    // Outer ring (slightly behind the inner)
    val outerRadius = sizePx * (0.38f + frac * 0.10f)
    ringPaint.strokeWidth = sizePx * 0.022f
    ringPaint.alpha = ((1f - frac) * 80).toInt().coerceIn(0, 255)
    cv.drawCircle(cx, cy, outerRadius, ringPaint)

    // ── Core orb — radial gradient with top-left specular highlight ────────
    val hlX = cx - r * 0.38f
    val hlY = cy - r * 0.38f
    val sphereGrad = RadialGradient(
        hlX, hlY, r * 1.15f,
        intArrayOf(
            AndroidColor.argb(255, 255, 255, 230),  // specular highlight
            type.highlight, type.core, type.mid,
            AndroidColor.argb(220, 0, 0, 0),        // shadow rim
        ),
        floatArrayOf(0f, 0.15f, 0.45f, 0.78f, 1f),
        Shader.TileMode.CLAMP,
    )
    cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sphereGrad })

    // ── Surface markings ───────────────────────────────────────────────────
    val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(180, 255, 255, 255) }
    when (type) {
        PulseEventType.EARTHQUAKE -> {
            markPaint.style = Paint.Style.STROKE; markPaint.strokeWidth = sizePx * 0.022f
            listOf(0.52f to 160, 0.70f to 100, 0.88f to 60).forEach { (s, a) ->
                markPaint.alpha = a; cv.drawCircle(cx, cy, r * s, markPaint)
            }
        }
        PulseEventType.FIRE -> {
            markPaint.style = Paint.Style.FILL; markPaint.alpha = 200
            val path = Path()
            val fy = cy - r * 0.15f
            path.moveTo(cx, fy - r * 0.58f)
            path.quadTo(cx + r * 0.32f, fy - r * 0.05f, cx, fy + r * 0.25f)
            path.quadTo(cx - r * 0.32f, fy - r * 0.05f, cx, fy - r * 0.58f)
            cv.drawPath(path, markPaint)
        }
        PulseEventType.CONFLICT -> {
            markPaint.style = Paint.Style.STROKE
            markPaint.strokeWidth = sizePx * 0.07f
            markPaint.strokeCap = Paint.Cap.ROUND; markPaint.alpha = 230
            val m = r * 0.40f
            cv.drawLine(cx - m, cy - m, cx + m, cy + m, markPaint)
            cv.drawLine(cx + m, cy - m, cx - m, cy + m, markPaint)
        }
        PulseEventType.DEFAULT -> Unit
    }

    return bmp
}

private fun registerAllPulseIcons(style: Style) {
    PulseEventType.entries.forEach { type ->
        for (frame in 0 until PULSE_FRAMES) {
            style.addImage("pulse_${type.typeKey}_f$frame", makePulseIcon(type, frame))
        }
    }
}

// ── Heatmap layer (event density / severity overview) ────────────────────────

private fun buildHeatmapLayer(sourceId: String): HeatmapLayer =
    HeatmapLayer(HEATMAP_LAYER, sourceId).apply {
        maxZoom = 9f
        setProperties(
            PropertyFactory.heatmapWeight(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("severity_score"),
                    Expression.literal(0.0), Expression.literal(0.0),
                    Expression.literal(1.0), Expression.literal(1.0),
                )
            ),
            PropertyFactory.heatmapIntensity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.literal(0.0), Expression.literal(1.0),
                    Expression.literal(9.0), Expression.literal(3.0),
                )
            ),
            PropertyFactory.heatmapColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.heatmapDensity(),
                    Expression.literal(0.0), Expression.rgba(0.0,   0.0,   0.0,   0.0),
                    Expression.literal(0.2), Expression.rgba(0.0,   180.0, 255.0, 0.6),
                    Expression.literal(0.4), Expression.rgba(30.0,  80.0,  220.0, 0.75),
                    Expression.literal(0.6), Expression.rgba(100.0, 0.0,   200.0, 0.85),
                    Expression.literal(0.8), Expression.rgba(200.0, 0.0,   80.0,  0.9),
                    Expression.literal(1.0), Expression.rgba(255.0, 30.0,  30.0,  1.0),
                )
            ),
            PropertyFactory.heatmapRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.literal(0.0), Expression.literal(15.0),
                    Expression.literal(9.0), Expression.literal(35.0),
                )
            ),
            PropertyFactory.heatmapOpacity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.literal(7.0), Expression.literal(0.85),
                    Expression.literal(9.0), Expression.literal(0.0),
                )
            ),
        )
    }

// ── Intelligence style: suppress noisy labels ─────────────────────────────────

private fun applyIntelligenceStyle(style: Style) {
    listOf(
        "poi_label", "poi-label", "transit-label", "transit_label",
        "road_label", "road-label", "road-shields",
        "waterway_label", "waterway-label",
        "airport-label", "airport_label",
        "ferry-aerialway-label",
    ).forEach { id ->
        style.getLayer(id)?.setProperties(PropertyFactory.visibility("none"))
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun PulsingDot(isConnected: Boolean) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Box(
        Modifier.size(8.dp).clip(CircleShape)
            .background(
                if (isConnected) GreenOk.copy(alpha = alpha) else RedCritical
            )
    )
}

@Composable
private fun IntelStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = CyanPrimary,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun IntelTopBar(
    isConnected: Boolean,
    totalArticles: Int?,
    sourcesActive: Int?,
    countdown: Int,
    showTimeSlider: Boolean,
    onTimeFilterToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(BgElevated.copy(alpha = 0.97f), BgSurface.copy(alpha = 0.99f))
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PulsingDot(isConnected)
        Text(
            if (isConnected) "LIVE" else "OFFLINE",
            style = MaterialTheme.typography.labelSmall,
            color = if (isConnected) GreenOk else RedCritical,
            fontWeight = FontWeight.Bold, fontSize = 9.sp,
        )
        if (totalArticles != null) IntelStat(totalArticles.toString(), "art")
        if (sourcesActive != null) IntelStat(sourcesActive.toString(), "src")
        Spacer(Modifier.weight(1f))
        val m = countdown / 60; val s = countdown % 60
        Text(
            if (m > 0) "${m}m ${s}s" else "${s}s",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        IconButton(onClick = onTimeFilterToggle, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Schedule, null,
                tint = if (showTimeSlider) CyanPrimary else TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TimeFilterOverlay(hours: Int, onHoursChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(BgElevated.copy(alpha = 0.97f), BgSurface.copy(alpha = 0.99f))
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Time window", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(
                when {
                    hours >= 168 -> "7 days"
                    hours >= 48  -> "${hours / 24}d"
                    else         -> "${hours}h"
                },
                style = MaterialTheme.typography.labelMedium,
                color = CyanPrimary, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value        = hours.toFloat(),
            onValueChange = { onHoursChange(it.toInt()) },
            valueRange   = 6f..168f,
            modifier     = Modifier.fillMaxWidth(),
            colors       = SliderDefaults.colors(
                thumbColor         = CyanPrimary,
                activeTrackColor   = CyanPrimary,
                inactiveTrackColor = BgCard,
            ),
        )
    }
}

// ── Info cards ────────────────────────────────────────────────────────────────

@Composable
private fun EventInfoCard(event: MapEventInfo, onDismiss: () -> Unit) {
    val (typeLabel, typeTint) = when (event.type) {
        "earthquake" -> "Earthquake" to CyanPrimary
        "fire"       -> "Wildfire"   to Color(0xFFFF5722)
        "conflict"   -> "Conflict"   to RedCritical
        else         -> event.type.replaceFirstChar { it.uppercase() } to TextSecondary
    }
    val severityColor = when (event.severity) {
        "low"      -> Color(0xFF2A4A6E)
        "medium"   -> Color(0xFF1E52A0)
        "high"     -> Color(0xFF3A7AE4)
        "critical" -> Color(0xFF5F9CF5)
        else       -> TextSecondary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(BgElevated.copy(alpha = 0.98f), BgSurface.copy(alpha = 0.99f))
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(typeLabel, style = MaterialTheme.typography.titleLarge, color = typeTint, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(severityColor.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        event.severity.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
        }
        if (event.title.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(event.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 3)
        }
        if (event.type == "earthquake" && event.magnitude > 0.0) {
            Spacer(Modifier.height(10.dp))
            StatChip("M%.1f".format(event.magnitude), "magnitude", CyanPrimary)
        }
    }
}

@Composable
private fun CountryInfoCard(
    iso: String, score: Float, articles24h: Int?, events7d: Int?,
    onViewNews: () -> Unit, onDismiss: () -> Unit,
) {
    val (statusLabel, statusTint) = when {
        score <= 0f    -> "No Activity" to TextSecondary
        score < 0.15f  -> "Minimal"     to GreenOk
        score < 0.40f  -> "Moderate"    to GreenOk
        score < 0.65f  -> "Elevated"    to CyanPrimary
        score < 0.85f  -> "High"        to Color(0xFF3A7AE4)
        else            -> "Critical"   to RedCritical
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(BgElevated.copy(alpha = 0.98f), BgSurface.copy(alpha = 0.99f))
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(iso.uppercase(), style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(statusLabel, style = MaterialTheme.typography.bodySmall,
                    color = statusTint, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
        }
        Spacer(Modifier.height(8.dp))
        // Gradient severity bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BgCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(statusTint.copy(alpha = 0.5f), statusTint)
                        )
                    )
            )
        }
        if (articles24h != null || events7d != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                articles24h?.let { StatChip("$it", "articles 24h", CyanPrimary) }
                events7d?.let    { StatChip("$it", "events 7d",    Color(0xFF3A7AE4)) }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text("Loading details…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onViewNews, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(CyanPrimary), shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Default.Article, null, tint = BgDeep, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("News from $iso", color = BgDeep, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, color = color,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

// ── Heatmap color helpers ─────────────────────────────────────────────────────

private fun buildHeatmapColorExpression(scores: Map<String, Float>): Expression {
    if (scores.isEmpty()) return Expression.rgba(0.0, 0.0, 0.0, 0.0)
    val parts = mutableListOf<Expression>(Expression.get("ISO3166-1-Alpha-2"))
    scores.forEach { (iso, score) -> parts += Expression.literal(iso); parts += scoreToRgba(score) }
    parts += Expression.rgba(0.0, 0.0, 0.0, 0.0)
    return Expression.match(*parts.toTypedArray())
}

private fun scoreToRgba(score: Float): Expression = when {
    score <= 0f   -> Expression.rgba(0.0,   0.0,   0.0,   0.0)
    score < 0.10f -> Expression.rgba(13.0,  38.0,  85.0,  0.55)
    score < 0.25f -> Expression.rgba(20.0,  62.0,  142.0, 0.68)
    score < 0.40f -> Expression.rgba(26.0,  82.0,  180.0, 0.75)
    score < 0.55f -> Expression.rgba(35.0,  100.0, 210.0, 0.82)
    score < 0.70f -> Expression.rgba(55.0,  120.0, 228.0, 0.88)
    score < 0.85f -> Expression.rgba(75.0,  140.0, 244.0, 0.92)
    else          -> Expression.rgba(95.0,  160.0, 255.0, 0.97)
}
