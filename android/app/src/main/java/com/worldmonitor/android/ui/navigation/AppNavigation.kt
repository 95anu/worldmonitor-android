package com.worldmonitor.android.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.worldmonitor.android.ui.screens.EventsScreen
import com.worldmonitor.android.ui.screens.MapScreen
import com.worldmonitor.android.ui.screens.NewsScreen
import com.worldmonitor.android.ui.screens.SettingsScreen
import com.worldmonitor.android.ui.theme.BgElevated
import com.worldmonitor.android.ui.theme.BgSurface
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.GlassBorder
import com.worldmonitor.android.ui.theme.NavPill
import com.worldmonitor.android.ui.theme.TextMuted
import com.worldmonitor.android.ui.theme.TextSecondary

object Routes {
    const val MAP = "map"
    const val NEWS = "news"
    const val EVENTS = "events"
    const val SETTINGS = "settings"

    const val NEWS_WITH_COUNTRY = "news?country={country}"
    fun newsWithCountry(code: String) = "news?country=$code"
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val navItems = listOf(
        NavItem(Routes.MAP,      "Map",     Icons.Default.Language),
        NavItem(Routes.NEWS,     "News",    Icons.Default.Article),
        NavItem(Routes.EVENTS,   "Events",  Icons.Default.Warning),
        NavItem(Routes.SETTINGS, "Settings", Icons.Default.Settings),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            FloatingNavBar(
                items = navItems,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.MAP) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                navBackStackEntry = navBackStackEntry,
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.MAP) {
                MapScreen(
                    onCountryClick = { code ->
                        navController.navigate(Routes.newsWithCountry(code)) {
                            popUpTo(Routes.MAP)
                        }
                    }
                )
            }
            composable(
                route = Routes.NEWS_WITH_COUNTRY,
                arguments = listOf(
                    navArgument("country") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val country = backStackEntry.arguments
                    ?.getString("country")
                    ?.takeIf { it.isNotBlank() }
                NewsScreen(initialCountry = country)
            }
            composable(Routes.NEWS)     { NewsScreen(initialCountry = null) }
            composable(Routes.EVENTS)   { EventsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

@Composable
private fun FloatingNavBar(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    navBackStackEntry: androidx.navigation.NavBackStackEntry?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = CyanPrimary.copy(alpha = 0.08f),
                    spotColor = CyanPrimary.copy(alpha = 0.12f),
                )
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(BgElevated, NavPill)
                    )
                )
                .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val baseRoute = currentRoute?.substringBefore("?")
                val selected = navBackStackEntry?.destination?.hierarchy
                    ?.any { it.route?.substringBefore("?") == item.route } == true
                    || (item.route == Routes.NEWS && baseRoute == Routes.NEWS)

                PillNavItem(
                    item     = item,
                    selected = selected,
                    onClick  = { onNavigate(item.route) },
                )
            }
        }
    }
}

@Composable
private fun PillNavItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "scale",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.42f,
        animationSpec = tween(220),
        label = "iconAlpha",
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.0f,
        animationSpec = tween(240),
        label = "pillAlpha",
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .background(CyanPrimary.copy(alpha = pillAlpha * 0.13f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (selected) CyanPrimary else TextMuted,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { alpha = iconAlpha },
            )
            if (selected) {
                Spacer(Modifier.height(3.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}
