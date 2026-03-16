package com.worldmonitor.android.ui.screens

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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.sin

private const val DARK_MAP_STYLE    = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val COUNTRIES_SOURCE  = "countries-source"
private const val EVENTS_SOURCE     = "events-source"
private const val COUNTRY_FILL      = "country-fill"
private const val COUNTRY_OUTLINE   = "country-fill-outline"
private const val EVENTS_CIRCLES    = "events-circles"
private const val EMPTY_FC          = """{"type":"FeatureCollection","features":[]}"""

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
    "DM" to LatLng(15.41, -61.37), "DO" to LatLng(18.74, -70.16), "EC" to LatLng(-1.83, -78.18),
    "EG" to LatLng(26.82, 30.80),  "SV" to LatLng(13.79, -88.90), "GQ" to LatLng(1.65, 10.27),
    "ER" to LatLng(15.18, 39.78),  "EE" to LatLng(58.60, 25.01),  "SZ" to LatLng(-26.52, 31.47),
    "ET" to LatLng(9.15, 40.49),   "FJ" to LatLng(-16.58, 179.41),"FI" to LatLng(61.92, 25.75),
    "FR" to LatLng(46.23, 2.21),   "GA" to LatLng(-0.80, 11.61),  "GM" to LatLng(13.44, -15.31),
    "GE" to LatLng(42.32, 43.36),  "DE" to LatLng(51.17, 10.45),  "GH" to LatLng(7.95, -1.02),
    "GR" to LatLng(39.07, 21.82),  "GD" to LatLng(12.12, -61.68), "GT" to LatLng(15.78, -90.23),
    "GN" to LatLng(9.95, -11.24),  "GW" to LatLng(11.80, -15.18), "GY" to LatLng(4.86, -58.93),
    "HT" to LatLng(18.97, -72.29), "HN" to LatLng(15.20, -86.24), "HU" to LatLng(47.16, 19.50),
    "IS" to LatLng(64.96, -19.02), "IN" to LatLng(20.59, 78.96),  "ID" to LatLng(-0.79, 113.92),
    "IR" to LatLng(32.43, 53.69),  "IQ" to LatLng(33.22, 43.68),  "IE" to LatLng(53.41, -8.24),
    "IL" to LatLng(31.05, 34.85),  "IT" to LatLng(41.87, 12.57),  "JM" to LatLng(18.11, -77.30),
    "JP" to LatLng(36.20, 138.25), "JO" to LatLng(30.59, 36.24),  "KZ" to LatLng(48.02, 66.92),
    "KE" to LatLng(-0.02, 37.91),  "KI" to LatLng(-3.37, -168.73),"KP" to LatLng(40.34, 127.51),
    "KR" to LatLng(35.91, 127.77), "XK" to LatLng(42.60, 20.90),  "KW" to LatLng(29.31, 47.48),
    "KG" to LatLng(41.20, 74.77),  "LA" to LatLng(19.86, 102.50), "LV" to LatLng(56.88, 24.60),
    "LB" to LatLng(33.85, 35.86),  "LS" to LatLng(-29.61, 28.23), "LR" to LatLng(6.43, -9.43),
    "LY" to LatLng(26.34, 17.23),  "LI" to LatLng(47.17, 9.56),   "LT" to LatLng(55.17, 23.88),
    "LU" to LatLng(49.82, 6.13),   "MG" to LatLng(-18.77, 46.87), "MW" to LatLng(-13.25, 34.30),
    "MY" to LatLng(4.21, 108.00),  "MV" to LatLng(3.20, 73.22),   "ML" to LatLng(17.57, -3.99),
    "MT" to LatLng(35.94, 14.38),  "MH" to LatLng(7.13, 171.18),  "MR" to LatLng(21.01, -10.94),
    "MU" to LatLng(-20.35, 57.55), "MX" to LatLng(23.63, -102.55),"FM" to LatLng(7.43, 150.55),
    "MD" to LatLng(47.41, 28.37),  "MC" to LatLng(43.75, 7.41),   "MN" to LatLng(46.86, 103.85),
    "ME" to LatLng(42.71, 19.37),  "MA" to LatLng(31.79, -7.09),  "MZ" to LatLng(-18.67, 35.53),
    "MM" to LatLng(21.91, 95.96),  "NA" to LatLng(-22.96, 18.49), "NR" to LatLng(-0.52, 166.93),
    "NP" to LatLng(28.39, 84.12),  "NL" to LatLng(52.13, 5.29),   "NZ" to LatLng(-40.90, 174.89),
    "NI" to LatLng(12.87, -85.21), "NE" to LatLng(17.61, 8.08),   "NG" to LatLng(9.08, 8.68),
    "MK" to LatLng(41.61, 21.75),  "NO" to LatLng(60.47, 8.47),   "OM" to LatLng(21.51, 55.92),
    "PK" to LatLng(30.38, 69.35),  "PW" to LatLng(7.52, 134.58),  "PS" to LatLng(31.95, 35.23),
    "PA" to LatLng(8.54, -80.78),  "PG" to LatLng(-6.31, 143.96), "PY" to LatLng(-23.44, -58.44),
    "PE" to LatLng(-9.19, -75.02), "PH" to LatLng(12.88, 121.77), "PL" to LatLng(51.92, 19.15),
    "PT" to LatLng(39.40, -8.22),  "QA" to LatLng(25.35, 51.18),  "RO" to LatLng(45.94, 24.97),
    "RU" to LatLng(61.52, 105.32), "RW" to LatLng(-1.94, 29.87),  "KN" to LatLng(17.36, -62.78),
    "LC" to LatLng(13.91, -60.98), "VC" to LatLng(12.98, -61.29), "WS" to LatLng(-13.76, -172.10),
    "SM" to LatLng(43.94, 12.46),  "ST" to LatLng(0.19, 6.61),    "SA" to LatLng(23.89, 45.08),
    "SN" to LatLng(14.50, -14.45), "RS" to LatLng(44.02, 21.01),  "SC" to LatLng(-4.68, 55.49),
    "SL" to LatLng(8.46, -11.78),  "SG" to LatLng(1.35, 103.82),  "SK" to LatLng(48.67, 19.70),
    "SI" to LatLng(46.15, 14.99),  "SB" to LatLng(-9.64, 160.16), "SO" to LatLng(5.15, 46.20),
    "ZA" to LatLng(-30.56, 22.94), "SS" to LatLng(6.88, 31.57),   "ES" to LatLng(40.46, -3.75),
    "LK" to LatLng(7.87, 80.77),   "SD" to LatLng(12.86, 30.22),  "SR" to LatLng(3.92, -56.03),
    "SE" to LatLng(60.13, 18.64),  "CH" to LatLng(46.82, 8.23),   "SY" to LatLng(34.80, 38.99),
    "TW" to LatLng(23.70, 121.00), "TJ" to LatLng(38.86, 71.28),  "TZ" to LatLng(-6.37, 34.89),
    "TH" to LatLng(15.87, 100.99), "TL" to LatLng(-8.87, 125.73), "TG" to LatLng(8.62, 0.82),
    "TO" to LatLng(-21.18, -175.20),"TT" to LatLng(10.69, -61.22),"TN" to LatLng(33.89, 9.54),
    "TR" to LatLng(38.96, 35.24),  "TM" to LatLng(38.97, 59.56),  "TV" to LatLng(-7.11, 177.64),
    "UG" to LatLng(1.37, 32.29),   "UA" to LatLng(48.38, 31.17),  "AE" to LatLng(23.42, 53.85),
    "GB" to LatLng(55.38, -3.44),  "US" to LatLng(37.09, -95.71), "UY" to LatLng(-32.52, -55.77),
    "UZ" to LatLng(41.38, 64.59),  "VU" to LatLng(-15.38, 166.96),"VE" to LatLng(6.42, -66.59),
    "VN" to LatLng(14.06, 108.28), "YE" to LatLng(15.55, 48.52),  "ZM" to LatLng(-13.13, 27.85),
    "ZW" to LatLng(-19.02, 29.15),
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

    // Reuse the ViewModel-held MapView — never recreated on navigation
    val mapView = remember { vm.getOrCreateMapView(context) }

    // Countdown badge
    var countdown by remember { mutableIntStateOf(refreshIntervalSeconds) }
    LaunchedEffect(refreshIntervalSeconds, state.lastUpdated) {
        countdown = refreshIntervalSeconds
        while (countdown > 0) { delay(1_000L); countdown-- }
    }

    // Forward NavBackStackEntry lifecycle to MapView (handles resume/pause on navigation)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                else -> {}
                // onDestroy is intentionally omitted — ViewModel.onCleared() handles it
                // so the GL context is preserved across navigation
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Update heatmap when scores change
    LaunchedEffect(state.scores, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapRef?.getStyle { style ->
            (style.getLayer(COUNTRY_FILL) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(buildHeatmapColorExpression(state.scores))
            )
        }
    }

    // Glistening heatmap — sine-wave opacity animation
    LaunchedEffect(styleReady) {
        if (!styleReady) return@LaunchedEffect
        var t = 0.0
        while (true) {
            val alpha = (0.72f + 0.28f * sin(t).toFloat()).coerceIn(0f, 1f)
            mapRef?.getStyle { style ->
                (style.getLayer(COUNTRY_FILL) as? FillLayer)?.setProperties(
                    PropertyFactory.fillOpacity(alpha)
                )
            }
            t += 0.08
            delay(100L)
        }
    }

    // Update event circles
    LaunchedEffect(state.eventGeoJson, styleReady) {
        if (!styleReady) return@LaunchedEffect
        vm.eventsSource?.setGeoJson(state.eventGeoJson)
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        ) { view ->
            // Map already initialised in a previous composition — skip setup
            if (vm.mapLibreMap.value != null) return@AndroidView

            view.getMapAsync { map ->
                vm.mapLibreMap.value = map

                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isCompassEnabled        = false
                    isLogoEnabled           = false
                    isAttributionEnabled    = false
                }

                // Initial camera — will be overridden to user's locale country below
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0))
                    .zoom(1.5)
                    .build()

                map.setStyle(Style.Builder().fromUri(DARK_MAP_STYLE)) { style ->

                    // Globe projection (MapLibre 11+)
                    try {
                        val projClass = Class.forName("org.maplibre.android.style.projection.StyleProjection")
                        val nameClass = Class.forName("org.maplibre.android.style.projection.StyleProjectionName")
                        val globe = nameClass.enumConstants?.firstOrNull { it.toString().equals("globe", ignoreCase = true) }
                        if (globe != null) {
                            val ctor = projClass.getConstructor(nameClass)
                            style.javaClass.getMethod("setProjection", projClass).invoke(style, ctor.newInstance(globe))
                        }
                    } catch (_: Exception) {}

                    // Countries source + heatmap fill
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

                    // Events source + circles
                    val eSrc = GeoJsonSource(EVENTS_SOURCE, state.eventGeoJson)
                    style.addSource(eSrc)
                    vm.eventsSource = eSrc

                    style.addLayerAbove(CircleLayer(EVENTS_CIRCLES, EVENTS_SOURCE).apply {
                        setProperties(
                            PropertyFactory.circleRadius(Expression.match(
                                Expression.get("severity"),
                                Expression.literal("low"),      Expression.literal(6f),
                                Expression.literal("medium"),   Expression.literal(10f),
                                Expression.literal("high"),     Expression.literal(15f),
                                Expression.literal("critical"), Expression.literal(20f),
                                Expression.literal(7f),
                            )),
                            PropertyFactory.circleColor(Expression.match(
                                Expression.get("type"),
                                Expression.literal("conflict"),   Expression.rgb(255.0, 50.0,  50.0),
                                Expression.literal("earthquake"), Expression.rgb(255.0, 165.0, 30.0),
                                Expression.literal("fire"),       Expression.rgb(255.0, 210.0, 0.0),
                                Expression.rgb(160.0, 160.0, 160.0),
                            )),
                            PropertyFactory.circleOpacity(0.92f),
                            PropertyFactory.circleStrokeWidth(1.8f),
                            PropertyFactory.circleStrokeColor(Expression.rgba(0.0, 0.0, 0.0, 0.55)),
                            PropertyFactory.circleBlur(0.1f),
                        )
                    }, COUNTRY_OUTLINE)

                    vm.mapStyleReady.value = true

                    // Auto-centre on India at a wide zoom on first load
                    if (!vm.hasSetInitialCamera) {
                        vm.hasSetInitialCamera = true
                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(20.59, 78.96)) // India centroid
                                    .zoom(2.2)
                                    .build()
                            ), 1800
                        )
                    }

                    // Click handler: events first, then countries
                    map.addOnMapClickListener { point ->
                        val px = map.projection.toScreenLocation(point)
                        val eventBox   = android.graphics.RectF(px.x - 30f, px.y - 30f, px.x + 30f, px.y + 30f)
                        val countryBox = android.graphics.RectF(px.x - 50f, px.y - 50f, px.x + 50f, px.y + 50f)

                        val eventFeatures = map.queryRenderedFeatures(eventBox, EVENTS_CIRCLES)
                        if (eventFeatures.isNotEmpty()) {
                            val f = eventFeatures.first()
                            vm.selectEvent(MapEventInfo(
                                type      = f.getStringProperty("type") ?: "unknown",
                                severity  = f.getStringProperty("severity") ?: "low",
                                title     = f.getStringProperty("title") ?: "",
                                magnitude = f.getNumberProperty("magnitude")?.toDouble() ?: 0.0,
                            ))
                            return@addOnMapClickListener true
                        }

                        val iso = map.queryRenderedFeatures(countryBox, COUNTRY_FILL).firstOrNull()
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

        // Stats bar
        state.stats?.let { stats ->
            StatsBar(
                articlesCount = stats.totalArticles24h,
                sourcesCount  = stats.sourcesActive,
                lastUpdated   = state.lastUpdated,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }

        // Countdown — top left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgElevated.copy(alpha = 0.88f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            val m = countdown / 60; val s = countdown % 60
            Text(
                text = if (m > 0) "↻ ${m}m ${s}s" else "↻ ${s}s",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary, fontSize = 10.sp,
            )
        }

        // Server connection — top right
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
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.isConnected) GreenOk else RedCritical))
            Text(
                text = if (state.isConnected) "Live" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.isConnected) GreenOk else RedCritical,
                fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
            )
        }

        // Loading spinner (first load)
        if (state.isLoading && state.scores.isEmpty()) {
            Box(Modifier.fillMaxSize().background(BgDeep.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanPrimary, strokeWidth = 3.dp)
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
            ) { Text(err, color = TextPrimary, style = MaterialTheme.typography.bodySmall) }
        }

        // Event info card
        AnimatedVisibility(
            visible = state.selectedEvent != null,
            enter = slideInVertically { it } + fadeIn(),
            exit  = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
        ) {
            state.selectedEvent?.let { EventInfoCard(it) { vm.selectEvent(null) } }
        }

        // Country info card
        AnimatedVisibility(
            visible = state.selectedCountry != null && state.selectedEvent == null,
            enter = slideInVertically { it } + fadeIn(),
            exit  = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
        ) {
            state.selectedCountry?.let { iso ->
                CountryInfoCard(
                    iso         = iso,
                    score       = state.selectedCountryScore,
                    articles24h = state.selectedCountryArticles,
                    events7d    = state.selectedCountryEvents,
                    onViewNews  = { vm.selectCountry(null); onCountryClick(iso) },
                    onDismiss   = { vm.selectCountry(null) },
                )
            }
        }

        // Refresh FAB
        FloatingActionButton(
            onClick = { vm.refresh() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = BgElevated,
            elevation = FloatingActionButtonDefaults.elevation(6.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyanPrimary)
        }
    }
}

// ── Event info card ───────────────────────────────────────────────────────────

@Composable
private fun EventInfoCard(event: MapEventInfo, onDismiss: () -> Unit) {
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
        elevation = CardDefaults.cardElevation(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(typeLabel, style = MaterialTheme.typography.titleLarge, color = typeColor, fontWeight = FontWeight.Bold)
                    Text(event.severity.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = severityColor, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
            }
            if (event.title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(event.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 3)
            }
            if (event.type == "earthquake" && event.magnitude > 0.0) {
                Spacer(Modifier.height(8.dp))
                CountryStat("M%.1f".format(event.magnitude), "magnitude", OrangeAlert)
            }
        }
    }
}

// ── Country info card ─────────────────────────────────────────────────────────

@Composable
private fun CountryInfoCard(iso: String, score: Float, articles24h: Int?, events7d: Int?, onViewNews: () -> Unit, onDismiss: () -> Unit) {
    val (threatLabel, threatColor) = when {
        score <= 0f  -> "No Activity" to TextSecondary
        score < 0.15f -> "Minimal"    to GreenOk
        score < 0.40f -> "Moderate"   to GreenOk
        score < 0.65f -> "Elevated"   to OrangeAlert
        score < 0.85f -> "High"       to OrangeAlert
        else           -> "Critical"  to RedCritical
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgElevated),
        elevation = CardDefaults.cardElevation(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(iso.uppercase(), style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(threatLabel, style = MaterialTheme.typography.bodySmall, color = threatColor, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { score.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = threatColor, trackColor = BgCard,
            )
            if (articles24h != null || events7d != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    articles24h?.let { CountryStat("$it", "articles 24h", CyanPrimary) }
                    events7d?.let    { CountryStat("$it", "events 7d",    OrangeAlert) }
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
                Text("News from $iso", color = BgDeep, fontWeight = FontWeight.Bold)
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

// ── Heatmap helpers ───────────────────────────────────────────────────────────

private fun buildHeatmapColorExpression(scores: Map<String, Float>): Expression {
    if (scores.isEmpty()) return Expression.rgba(0.0, 0.0, 0.0, 0.0)
    val parts = mutableListOf<Expression>(Expression.get("ISO3166-1-Alpha-2"))
    scores.forEach { (iso, score) -> parts += Expression.literal(iso); parts += scoreToRgba(score) }
    parts += Expression.rgba(0.0, 0.0, 0.0, 0.0)
    return Expression.match(*parts.toTypedArray())
}

private fun scoreToRgba(score: Float): Expression = when {
    score <= 0f    -> Expression.rgba(0.0,   0.0,   0.0,   0.0)
    score < 0.10f  -> Expression.rgba(10.0,  40.0,  120.0, 0.55)
    score < 0.25f  -> Expression.rgba(20.0,  60.0,  160.0, 0.68)
    score < 0.40f  -> Expression.rgba(100.0, 50.0,  10.0,  0.75)
    score < 0.55f  -> Expression.rgba(180.0, 70.0,  15.0,  0.82)
    score < 0.70f  -> Expression.rgba(230.0, 90.0,  20.0,  0.88)
    score < 0.85f  -> Expression.rgba(255.0, 45.0,  15.0,  0.92)
    else           -> Expression.rgba(255.0, 10.0,  10.0,  0.97)
}
