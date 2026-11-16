package dev.lector.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import dev.lector.app.R
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme
import kotlin.math.roundToInt

/** Superficie flotante de píldora: `surface` al 94 %, contorno `line` y sombra flotante (DESIGN §2.3). */
@Composable
fun FloatingPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LectorTheme.colors
    Row(
        modifier = modifier
            .floatingShadow(LectorShapes.Pill, c.shadow)
            .clip(LectorShapes.Pill)
            .background(c.surface.copy(alpha = BAR_ALPHA))
            .lineBorder(LectorShapes.Pill, c.line),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Dock de Inicio: píldora flotante con **Abrir** y **Escanear** (DESIGN §4.1). */
@Composable
fun FloatingDock(onOpen: () -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    val c = LectorTheme.colors
    FloatingPill(modifier = modifier.padding(LectorSpacing.dockPadding)) {
        TextIconButton(text = stringResource(R.string.home_open), icon = R.drawable.ic_folder_open, onClick = onOpen)
        Box(Modifier.width(LectorSpacing.dockSpacing))
        androidx.compose.material3.Surface(
            onClick = onScan,
            shape = LectorShapes.Pill,
            color = c.accent,
            contentColor = c.onAccent,
        ) {
            Row(
                modifier = Modifier
                    .height(LectorSizes.buttonHeight)
                    .padding(start = LectorSpacing.xl, end = LectorSpacing.dockPadding + LectorSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LectorSpacing.m),
            ) {
                Text(
                    text = stringResource(R.string.home_scan),
                    style = LectorTheme.type.button,
                    color = c.onAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .size(LectorSizes.scanCircle)
                        .clip(LectorShapes.Pill)
                        .background(c.onAccent.copy(alpha = SCAN_CIRCLE_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    LectorIcon(R.drawable.ic_scan, null, tint = c.onAccent, size = LectorSizes.iconSizeInButton)
                }
            }
        }
    }
}

/** Barra superior flotante de los visores (DESIGN §4.4). */
@Composable
fun FloatingTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    FloatingPill(modifier = modifier.fillMaxWidth().height(LectorSizes.barHeight)) {
        IconCircleButton(R.drawable.ic_arrow_left, stringResource(R.string.viewer_back), onBack)
        Text(
            text = title,
            style = LectorTheme.type.item,
            color = LectorTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = LectorSpacing.xs),
        )
        actions()
    }
}

/** Barra inferior: contador en mono y deslizador de páginas. Oculta si hay menos de 4 páginas. */
@Composable
fun FloatingPageBar(
    page: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
) {
    val c = LectorTheme.colors
    FloatingPill(modifier = modifier.fillMaxWidth().height(LectorSizes.barHeight)) {
        Text(
            text = stringResource(R.string.viewer_page_counter, page + 1, pageCount),
            style = LectorTheme.type.mono,
            color = c.text,
            maxLines = 1,
            modifier = Modifier.padding(start = LectorSpacing.xl, end = LectorSpacing.m),
        )
        PageSlider(
            page = page,
            pageCount = pageCount,
            onPageChange = onPageChange,
            onChangeFinished = onChangeFinished,
            modifier = Modifier.weight(1f).padding(end = LectorSpacing.xl),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSlider(
    page: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
) {
    val c = LectorTheme.colors
    val haptics = LocalHapticFeedback.current
    var lastHaptic by remember { mutableLongStateOf(0L) }
    val description = stringResource(R.string.viewer_page_state, page + 1, pageCount)
    val sliderLabel = stringResource(R.string.viewer_page_slider)
    val last = (pageCount - 1).coerceAtLeast(1)
    Slider(
        value = page.toFloat().coerceIn(0f, last.toFloat()),
        onValueChange = { value ->
            val target = value.roundToInt().coerceIn(0, pageCount - 1)
            if (target != page) {
                val now = System.currentTimeMillis()
                if (now - lastHaptic >= HAPTIC_INTERVAL_MS) {
                    lastHaptic = now
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onPageChange(target)
            }
        },
        onValueChangeFinished = onChangeFinished,
        valueRange = 0f..last.toFloat(),
        steps = (last - 1).coerceAtLeast(0),
        modifier = modifier.semantics {
            contentDescription = sliderLabel
            stateDescription = description
        },
        thumb = {
            Box(Modifier.size(LectorSizes.sliderThumb).clip(LectorShapes.Pill).background(c.accent))
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) (state.value - state.valueRange.start) / span else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(LectorSizes.sliderTrack)
                    .clip(LectorShapes.Pill)
                    .background(c.line),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(c.accent),
                )
            }
        },
        colors = SliderDefaults.colors(),
    )
}

private const val BAR_ALPHA = 0.94f
private const val SCAN_CIRCLE_ALPHA = 0.16f
private const val HAPTIC_INTERVAL_MS = 60L
