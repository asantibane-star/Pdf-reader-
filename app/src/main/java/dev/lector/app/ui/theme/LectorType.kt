package dev.lector.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.lector.app.R

val GeistFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

val GeistMonoFamily = FontFamily(Font(R.font.geist_mono_medium, FontWeight.Medium))

/** Estilos de texto de `docs/DESIGN.md` §2.2 (todo en `sp`; tamaño/alto de línea/peso/tracking de la tabla). */
@Immutable
class LectorType(
    val display: TextStyle,
    /** `display` reducido a 26/30 para `StatePane`. */
    val displayReduced: TextStyle,
    val title: TextStyle,
    val itemPrimary: TextStyle,
    val item: TextStyle,
    val body: TextStyle,
    /** Lectura de texto plano: `body` 16/26. */
    val reading: TextStyle,
    /** Cifras de hoja de cálculo: `body` 13 sp. */
    val bodySmall: TextStyle,
    val meta: TextStyle,
    val metaGroup: TextStyle,
    val button: TextStyle,
    val mono: TextStyle,
    val monogram: TextStyle,
)

val DefaultLectorType = LectorType(
    display = TextStyle(fontFamily = GeistFamily, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.035).em),
    displayReduced = TextStyle(fontFamily = GeistFamily, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.035).em),
    title = TextStyle(fontFamily = GeistFamily, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    itemPrimary = TextStyle(fontFamily = GeistFamily, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em),
    item = TextStyle(fontFamily = GeistFamily, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    body = TextStyle(fontFamily = GeistFamily, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    reading = TextStyle(fontFamily = GeistFamily, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = GeistFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    meta = TextStyle(fontFamily = GeistFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    metaGroup = TextStyle(fontFamily = GeistFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    button = TextStyle(fontFamily = GeistFamily, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    mono = TextStyle(fontFamily = GeistMonoFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    monogram = TextStyle(fontFamily = GeistMonoFamily, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em),
)

val LocalLectorType = staticCompositionLocalOf { DefaultLectorType }
