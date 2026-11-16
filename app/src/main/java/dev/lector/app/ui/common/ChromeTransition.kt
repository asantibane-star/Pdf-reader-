package dev.lector.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import dev.lector.app.ui.theme.LocalMotionEnabled
import androidx.compose.ui.unit.dp

/** Curva de la especificación de movimiento (DESIGN §3). */
val LectorEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Aparición/ocultación de las barras del visor: fundido + desplazamiento de 16 dp hacia su borde, 220 ms. */
@Composable
fun FloatingChrome(visible: Boolean, fromTop: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = LocalMotionEnabled.current
    val offsetPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val direction = if (fromTop) -1 else 1
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motion) {
            fadeIn(tween(CHROME_MS, easing = LectorEasing)) +
                slideInVertically(tween(CHROME_MS, easing = LectorEasing)) { direction * offsetPx }
        } else {
            EnterTransition.None
        },
        exit = if (motion) {
            fadeOut(tween(CHROME_MS, easing = LectorEasing)) +
                slideOutVertically(tween(CHROME_MS, easing = LectorEasing)) { direction * offsetPx }
        } else {
            ExitTransition.None
        },
    ) { content() }
}

private const val CHROME_MS = 220
