package com.worldmonitor.android.ui.theme

import androidx.compose.ui.graphics.Color

// Background layers — deeper/darker than before
val BgDeep = Color(0xFF060A14)      // near black
val BgSurface = Color(0xFF0B1120)
val BgElevated = Color(0xFF111B2E)
val BgCard = Color(0xFF162138)

// Accents
val CyanPrimary = Color(0xFF00E5FF)
val CyanDim = Color(0xFF0094B3)
val OrangeAlert = Color(0xFF2968D0)      // navy accent (medium-bright)
val RedCritical = Color(0xFFFF1744)
val GreenOk = Color(0xFF00E676)

// Text
val TextPrimary = Color(0xFFECF0F8)
val TextSecondary = Color(0xFF7A8FA8)
val TextMuted = Color(0xFF3D5068)

// Heatmap color scale — navy severity gradient
val HeatmapNone = Color(0xFF070C18)      // 0.0 — no data
val HeatmapLow = Color(0xFF0D2655)       // 0.15 — dark navy
val HeatmapMedium = Color(0xFF1A4490)    // 0.4  — medium navy
val HeatmapHigh = Color(0xFF2B6BD4)      // 0.7  — bright navy
val HeatmapCritical = Color(0xFF5090F0)  // 1.0  — electric navy

// Severity badge colors — navy scale
val SeverityLow = Color(0xFF2A4A6E)      // dim navy
val SeverityMedium = Color(0xFF1E52A0)   // medium navy
val SeverityHigh = Color(0xFF3A7AE4)     // bright navy
val SeverityCritical = Color(0xFF5F9CF5) // brightest quiet navy

// Glass / depth system
val GlassBorder = Color(0x14FFFFFF)      // white 8% — 1dp glass border
val GlassHighlight = Color(0x0AFFFFFF)   // white 4% — inner glow tint

// Glow overlays
val CyanGlow = Color(0x2600E5FF)         // cyan 15% — node halo
val RedGlow = Color(0x26FF1744)          // red 15% — critical glow

// Floating nav pill
val NavPill = Color(0xF2111B2E)          // near-opaque elevated surface

// Skeleton shimmer
val SkeletonBase = Color(0xFF111B2E)
val SkeletonHighlight = Color(0xFF1C2E45)
