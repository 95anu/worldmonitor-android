package com.worldmonitor.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = BgDeep,
    primaryContainer = CyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = OrangeAlert,
    onSecondary = BgDeep,
    error = RedCritical,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
)

@Composable
fun WorldMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = WorldMonitorTypography,
        content = content,
    )
}
