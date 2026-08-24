package com.vokie.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Vokie exact design palette.
 * The alert red is reserved ONLY for emergency/SOS actions and critical danger states.
 */
object VokiePalette {
    val BgBase = Color(0xFF111111)
    val Surface = Color(0xFF1C1C1C)
    val Border = Color(0xFF2F343C)
    val TextMuted = Color(0xFF4E4C4B)
    val TextPrimary = Color(0xFFDBDBDB)
    val TextSecondary = Color(0xFFB3B3B3)
    val LightBg = Color(0xFFD5D5D5)
    val Alert = Color(0xFFE8402B)
    val Success = Color(0xFF3FA66E)

    // Light-mode derived tokens (manually selectable)
    val LightSurface = Color(0xFFECECEC)
    val LightBorder = Color(0xFFBEBEBE)
    val LightTextPrimary = Color(0xFF161616)
    val LightTextSecondary = Color(0xFF3B3B3B)
    val LightTextMuted = Color(0xFF7A7A7A)
}
