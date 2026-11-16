package dev.lector.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme
import dev.lector.app.ui.theme.LocalMotionEnabled

/**
 * Estado vacío, de carga o de error (DESIGN §4.6): alineado a la izquierda, centrado en vertical,
 * margen de 28 dp, titular, cuerpo y hasta dos botones. Nunca un indicador circular a pantalla completa.
 */
@Composable
fun StatePane(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = LectorTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(LectorSpacing.statePaneMargin),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        if (illustration != null) {
            illustration()
            Spacer(Modifier.height(LectorSpacing.xl))
        }
        Text(text = title, style = LectorTheme.type.displayReduced, color = c.text)
        Spacer(Modifier.height(LectorSpacing.s))
        Text(text = body, style = LectorTheme.type.body, color = c.textMuted, modifier = Modifier.padding(end = LectorSpacing.xl))
        if (primaryLabel != null && onPrimary != null) {
            Spacer(Modifier.height(LectorSpacing.xl))
            PrimaryButton(text = primaryLabel, onClick = onPrimary)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(LectorSpacing.m))
            SecondaryButton(text = secondaryLabel, onClick = onSecondary)
        }
    }
}

/** Esqueleto con brillo que recorre la forma (1 200 ms, lineal). Estático con las animaciones apagadas. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = LectorShapes.Tile, color: androidx.compose.ui.graphics.Color = LectorTheme.colors.sunken) {
    val motion = LocalMotionEnabled.current
    val progress = if (motion) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmerProgress",
        )
        value
    } else {
        SHIMMER_STATIC
    }
    val highlight = LectorTheme.colors.surface.copy(alpha = SHIMMER_ALPHA)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .drawWithContent {
                drawContent()
                val width = size.width
                val start = -width + progress * width * 2f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(androidx.compose.ui.graphics.Color.Transparent, highlight, androidx.compose.ui.graphics.Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(start + width, 0f),
                    ),
                )
            },
    )
}

/** Marcador de página en carga: rectángulo blanco con brillo (nunca un hueco vacío). */
@Composable
fun PageSkeleton(modifier: Modifier = Modifier) {
    SkeletonBox(modifier = modifier.fillMaxWidth(), shape = LectorShapes.Page, color = androidx.compose.ui.graphics.Color.White)
}

/** Esqueleto de Inicio: tarjeta destacada y cuatro filas. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = LectorSpacing.screenMargin), verticalArrangement = Arrangement.spacedBy(LectorSpacing.l)) {
        SkeletonBox(Modifier.fillMaxWidth().height(SKELETON_CARD_HEIGHT), shape = LectorShapes.CardOuter)
        repeat(SKELETON_ROWS) {
            SkeletonBox(Modifier.fillMaxWidth().height(SKELETON_ROW_HEIGHT))
        }
    }
}

private const val SHIMMER_MS = 1_200
private const val SHIMMER_STATIC = 0.5f
private const val SHIMMER_ALPHA = 0.55f
private const val SKELETON_ROWS = 4
private val SKELETON_CARD_HEIGHT = 128.dp
private val SKELETON_ROW_HEIGHT = 56.dp
