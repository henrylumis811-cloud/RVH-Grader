package com.henrylumis.rvhgrader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same brand palette (cyan/teal on dark navy) as the original web app's HUD theme, split into
// a proper light scheme and dark scheme rather than forcing one look regardless of the device
// setting.

private val BrandCyan = Color(0xFF00E5FF)
private val BrandTeal = Color(0xFF00B8A9)
private val NeonRed = Color(0xFFFF3366)

private val DarkColors = darkColorScheme(
    primary = BrandCyan,
    onPrimary = Color(0xFF00131A),
    secondary = BrandTeal,
    background = Color(0xFF060B14),
    surface = Color(0xFF0D1420),
    surfaceVariant = Color(0xFF141D2E),
    onBackground = Color(0xFFE8F1FA),
    onSurface = Color(0xFFE8F1FA),
    onSurfaceVariant = Color(0xFFAAB8CC),
    outline = Color(0xFF2A3B52),
    outlineVariant = Color(0xFF1C2836),
    error = NeonRed
)

private val LightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    secondary = BrandCyan,
    background = Color(0xFFF5F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4ECF2),
    onBackground = Color(0xFF10161F),
    onSurface = Color(0xFF10161F),
    onSurfaceVariant = Color(0xFF48566A),
    outline = Color(0xFFB8C6D6),
    outlineVariant = Color(0xFFD6E0E8),
    error = NeonRed
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
