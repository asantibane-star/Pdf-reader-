package dev.lector.app.ui.common

import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LocalMotionEnabled

/** Escala de pulsación: 0,97 con muelle (`stiffness 600`, `damping 0.7`); instantánea sin animaciones. */
@Composable
fun rememberPressScale(source: InteractionSource): State<Float> {
    val pressed = source.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current
    return animateFloatAsState(
        targetValue = if (pressed.value) PRESSED_SCALE else 1f,
        animationSpec = if (motion) spring(dampingRatio = 0.7f, stiffness = 600f) else snap(),
        label = "pressScale",
    )
}

fun Modifier.scaledBy(scale: State<Float>): Modifier = graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}

/** Contorno interior de 1 dp con el token `line`. */
fun Modifier.lineBorder(shape: Shape, line: Color): Modifier = border(LectorSizes.hairline, line, shape)

/** Sombra única "flotante" (DESIGN §2.3): elevación 12 dp con color teñido. */
fun Modifier.floatingShadow(shape: Shape, tint: Color): Modifier =
    shadow(elevation = 12.dp, shape = shape, ambientColor = tint, spotColor = tint)

private const val PRESSED_SCALE = 0.97f
