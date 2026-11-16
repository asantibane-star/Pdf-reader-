package dev.lector.app.ui

import androidx.compose.ui.graphics.Color
import dev.lector.app.ui.theme.DarkLectorColors
import dev.lector.app.ui.theme.LectorColors
import dev.lector.app.ui.theme.LightLectorColors
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contraste AA de cada par texto/fondo de DESIGN §2.1: 4,5:1 para texto normal. */
class ThemeContrastTest {
    private fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun over(top: Color, bottom: Color): Color {
        val a = top.alpha
        return Color(top.red * a + bottom.red * (1 - a), top.green * a + bottom.green * (1 - a), top.blue * a + bottom.blue * (1 - a), 1f)
    }

    private fun ratio(fg: Color, bg: Color): Double {
        val l1 = luminance(over(fg, bg))
        val l2 = luminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun pairs(c: LectorColors): List<Triple<String, Color, Color>> = listOf(
        Triple("text/bg", c.text, c.bg),
        Triple("text/surface", c.text, c.surface),
        Triple("text/sunken", c.text, c.sunken),
        Triple("text/canvas", c.text, c.canvas),
        Triple("textMuted/bg", c.textMuted, c.bg),
        Triple("textMuted/surface", c.textMuted, c.surface),
        Triple("textMuted/sunken", c.textMuted, c.sunken),
        Triple("accent/bg", c.accent, c.bg),
        Triple("accent/surface", c.accent, c.surface),
        Triple("onAccent/accent", c.onAccent, c.accent),
        Triple("onAccentSoft/accentSoft", c.onAccentSoft, c.accentSoft),
        Triple("danger/bg", c.danger, c.bg),
        Triple("danger/surface", c.danger, c.surface),
        Triple("bg/text (aviso invertido)", c.bg, c.text),
        Triple("accentSoft/text (acción del aviso)", c.accentSoft, c.text),
    )

    @Test fun lightTokensMeetAA() = check("claro", LightLectorColors)

    @Test fun darkTokensMeetAA() = check("oscuro", DarkLectorColors)

    private fun check(name: String, colors: LectorColors) {
        for ((label, fg, bg) in pairs(colors)) {
            val r = ratio(fg, bg)
            assertTrue("$name $label: contraste %.2f < 4,5".format(r), r >= 4.5)
        }
    }
}
