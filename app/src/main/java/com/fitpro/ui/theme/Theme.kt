package com.fitpro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Brand colors ─────────────────────────────────────────────────────────────

val CyanPrimary     = Color(0xFF22D3EE)  // Cyan 400
val CyanDark        = Color(0xFF0891B2)  // Cyan 600
val CyanLight       = Color(0xFF67E8F9)  // Cyan 300
val SlateBg         = Color(0xFF0F172A)  // Slate 900
val SlateSurface    = Color(0xFF1E293B)  // Slate 800
val SlateCard       = Color(0xFF334155)  // Slate 700
val SlateSubtle     = Color(0xFF475569)  // Slate 600

// Macro colors (consistent with web dashboard)
val ProteinBlue     = Color(0xFF378ADD)
val CarbAmber       = Color(0xFFBA7517)
val FatPink         = Color(0xFFD4537E)
val FiberGreen      = Color(0xFF639922)
val CalorieOrange   = Color(0xFFEA580C)

// Status colors
val StatusNormal    = Color(0xFF22C55E)  // green
val StatusBorder    = Color(0xFFF59E0B)  // amber
val StatusAltered   = Color(0xFFEF4444)  // red

// Training heatmap levels
val HeatLevel0      = Color(0xFF1E293B)  // no activity
val HeatLevel1      = Color(0xFF164E63)  // light
val HeatLevel2      = Color(0xFF0E7490)  // moderate
val HeatLevel3      = Color(0xFF0891B2)  // high
val HeatLevel4      = Color(0xFF22D3EE)  // intense

// ─── Dark color scheme (default) ─────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary           = CyanPrimary,
    onPrimary         = Color(0xFF001F26),
    primaryContainer  = CyanDark,
    onPrimaryContainer= CyanLight,
    secondary         = Color(0xFF67E8F9),
    onSecondary       = Color(0xFF00363E),
    background        = SlateBg,
    onBackground      = Color(0xFFF1F5F9),
    surface           = SlateSurface,
    onSurface         = Color(0xFFE2E8F0),
    surfaceVariant    = SlateCard,
    onSurfaceVariant  = Color(0xFFCBD5E1),
    outline           = SlateSubtle,
    error             = StatusAltered,
    onError           = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary           = CyanDark,
    onPrimary         = Color.White,
    primaryContainer  = Color(0xFFE0F7FA),
    onPrimaryContainer= Color(0xFF004D5C),
    secondary         = Color(0xFF0097A7),
    onSecondary       = Color.White,
    background        = Color(0xFFF8FAFC),
    onBackground      = Color(0xFF0F172A),
    surface           = Color.White,
    onSurface         = Color(0xFF1E293B),
    surfaceVariant    = Color(0xFFF1F5F9),
    onSurfaceVariant  = Color(0xFF475569),
    outline           = Color(0xFFCBD5E1),
    error             = StatusAltered,
    onError           = Color.White
)

@Composable
fun FitProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = FitProTypography,
        content     = content
    )
}
