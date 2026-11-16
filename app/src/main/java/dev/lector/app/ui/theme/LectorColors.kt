package dev.lector.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tokens de color de `docs/DESIGN.md` §2.1. Las pantallas solo usan estos nombres, nunca literales. */
@Immutable
class LectorColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val sunken: Color,
    val canvas: Color,
    val text: Color,
    val textMuted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val danger: Color,
    val scrim: Color,
    /** Color de la sombra flotante (§2.3). */
    val shadow: Color,
)

val LightLectorColors = LectorColors(
    isDark = false,
    bg = Color(0xFFF4F5F6),
    surface = Color(0xFFFCFCFD),
    sunken = Color(0xFFE9EBEE),
    canvas = Color(0xFFE3E6E9),
    text = Color(0xFF15171A),
    textMuted = Color(0xFF5C636B),
    line = Color(0xFF15171A).copy(alpha = 0.08f),
    accent = Color(0xFF0E6B56),
    onAccent = Color(0xFFF4FBF8),
    accentSoft = Color(0xFFDDEEE8),
    onAccentSoft = Color(0xFF0A4A3C),
    danger = Color(0xFFB3261E),
    scrim = Color(0xFF0E1012).copy(alpha = 0.5f),
    shadow = Color(0xFF142832).copy(alpha = 0.35f),
)

val DarkLectorColors = LectorColors(
    isDark = true,
    bg = Color(0xFF0E1012),
    surface = Color(0xFF17191C),
    sunken = Color(0xFF202327),
    canvas = Color(0xFF08090A),
    text = Color(0xFFECEEF0),
    textMuted = Color(0xFF9BA3AB),
    line = Color(0xFFECEEF0).copy(alpha = 0.09f),
    accent = Color(0xFF5FD3B3),
    onAccent = Color(0xFF06281F),
    accentSoft = Color(0xFF16332C),
    onAccentSoft = Color(0xFFA9E8D6),
    danger = Color(0xFFF2B8B5),
    scrim = Color(0xFF000000).copy(alpha = 0.6f),
    shadow = Color(0xFF000000).copy(alpha = 0.8f),
)

val LocalLectorColors = staticCompositionLocalOf { LightLectorColors }
