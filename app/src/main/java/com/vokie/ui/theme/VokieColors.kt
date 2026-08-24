package com.vokie.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens exposed to the UI so components never reference raw palette
 * values directly. This keeps dark (default) and light modes swappable.
 */
@Immutable
data class VokieColors(
    val background: Color,
    val surface: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val alert: Color,
    val success: Color,
    val onAlert: Color,
    val onSuccess: Color,
    val isDark: Boolean,
)

val DarkVokieColors = VokieColors(
    background = VokiePalette.BgBase,
    surface = VokiePalette.Surface,
    border = VokiePalette.Border,
    textPrimary = VokiePalette.TextPrimary,
    textSecondary = VokiePalette.TextSecondary,
    textMuted = VokiePalette.TextMuted,
    alert = VokiePalette.Alert,
    success = VokiePalette.Success,
    onAlert = Color.White,
    onSuccess = Color(0xFF06210F),
    isDark = true,
)

val LightVokieColors = VokieColors(
    background = VokiePalette.LightBg,
    surface = VokiePalette.LightSurface,
    border = VokiePalette.LightBorder,
    textPrimary = VokiePalette.LightTextPrimary,
    textSecondary = VokiePalette.LightTextSecondary,
    textMuted = VokiePalette.LightTextMuted,
    alert = VokiePalette.Alert,
    success = Color(0xFF2C7D50),
    onAlert = Color.White,
    onSuccess = Color.White,
    isDark = false,
)

val LocalVokieColors = staticCompositionLocalOf { DarkVokieColors }
