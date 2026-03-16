package com.worldmonitor.android.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.worldmonitor.android.WorldMonitorApp
import com.worldmonitor.android.ui.screens.EventsScreen
import com.worldmonitor.android.ui.screens.MapScreen
import com.worldmonitor.android.ui.screens.NewsScreen
import com.worldmonitor.android.ui.screens.SettingsScreen
import com.worldmonitor.android.ui.theme.BgCard
import com.worldmonitor.android.ui.theme.BgSurface
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.OrangeAlert
import com.worldmonitor.android.ui.theme.TextMuted
import com.worldmonitor.android.ui.theme.TextPrimary

object Routes {
    const val MAP = "map"
    const val NEWS = "news"
    const val EVENTS = "events"
    const val SETTINGS = "settings"

    // Parameterised news route
    const val NEWS_WITH_COUNTRY = "news?country={country}"
    fun newsWithCountry(code: String) = "news?country=$code"
}

data class NavItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as WorldMonitorApp
    val serverUrl by app.preferences.serverUrl.collectAsState(initial = "")

    val snackbarHostState = remember { SnackbarHostState() }

    val navItems = listOf(
        NavItem(Routes.MAP, "Map") { Icon(Icons.Default.Language, contentDescription = "Map") },
        NavItem(Routes.NEWS, "News") { Icon(Icons.Default.Article, contentDescription = "News") },
        NavItem(Routes.EVENTS, "Events") { Icon(Icons.Default.Warning, contentDescription = "Events") },
        NavItem(Routes.SETTINGS, "Settings") { Icon(Icons.Default.Settings, contentDescription = "Settings") },
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // If no server URL is configured, nudge the user via snackbar — never block navigation
    LaunchedEffect(serverUrl) {
        if (serverUrl.isBlank()) {
            snackbarHostState.showSnackbar("Set server URL in Settings")
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = OrangeAlert,
                    contentColor = TextPrimary,
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = BgSurface) {
                navItems.forEach { item ->
                    // Match on the base route (strip query params for active-state checks)
                    val baseRoute = currentRoute?.substringBefore("?")
                    val selected = navBackStackEntry?.destination?.hierarchy
                        ?.any { it.route?.substringBefore("?") == item.route } == true
                        || (item.route == Routes.NEWS && baseRoute == Routes.NEWS)
                    NavigationBarItem(
                        icon = item.icon,
                        label = { Text(item.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = BgCard,
                        ),
                    )
                }
            }
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
                        navController.navigate(Routes.newsWithCountry(code))
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
            // Plain news route (no country filter) — keeps back-stack restore working
            composable(Routes.NEWS) {
                NewsScreen(initialCountry = null)
            }
            composable(Routes.EVENTS) { EventsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
