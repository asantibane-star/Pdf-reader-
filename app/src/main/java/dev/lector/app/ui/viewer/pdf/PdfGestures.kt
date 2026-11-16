package dev.lector.app.ui.viewer.pdf

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gestos del visor sin interferir con el desplazamiento vertical de la lista:
 * - pellizco de DOS dedos (consume solo entonces, en el pase `Initial`);
 * - arrastre horizontal con un dedo, solo si hay zoom;
 * - toque simple (mostrar/ocultar barras) y doble toque (1× ↔ 2,5×).
 */
fun Modifier.pdfGestures(
    state: PdfZoomState,
    list: LazyListState,
    scope: CoroutineScope,
    motionEnabled: Boolean,
    drag: DraggableState,
    onTap: () -> Unit,
    onScrollForward: () -> Unit,
    onZoomSettled: (Float) -> Unit,
): Modifier {
    return this
        .pointerInput(state, list) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var pinching = false
                var travelled = 0f
                var notified = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.count { it.pressed }
                    if (pressed >= 2) {
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (!pinching) {
                            pinching = true
                            state.beginPinch(centroid - pan)
                        }
                        state.updatePinch(zoomChange, centroid, centroid - pan)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else if (!pinching && !notified) {
                        // Un dedo desplazando el contenido hacia delante (el dedo sube): ocultar las barras.
                        travelled += event.calculatePan().y
                        if (travelled < -viewConfiguration.touchSlop) {
                            notified = true
                            onScrollForward()
                        }
                    }
                } while (event.changes.any { it.pressed && !it.changedToUp() })
                if (pinching) {
                    state.commit(list)
                    onZoomSettled(state.zoom)
                }
            }
        }
        .pointerInput(state, list, motionEnabled) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { at -> scope.animateDoubleTap(state, list, at, motionEnabled, onZoomSettled) },
            )
        }
        .draggable(state = drag, orientation = Orientation.Horizontal, enabled = state.isZoomed)
}

private fun CoroutineScope.animateDoubleTap(
    state: PdfZoomState,
    list: LazyListState,
    at: Offset,
    motionEnabled: Boolean,
    onZoomSettled: (Float) -> Unit,
) {
    launch {
        val target = state.doubleTapTarget() / state.zoom
        state.beginAnimatedZoom(at)
        if (motionEnabled) {
            animate(
                initialValue = 1f,
                targetValue = target,
                animationSpec = spring(dampingRatio = DOUBLE_TAP_DAMPING, stiffness = DOUBLE_TAP_STIFFNESS),
            ) { value, _ -> state.setAnimatedScale(value) }
        } else {
            state.setAnimatedScale(target)
        }
        state.commit(list)
        onZoomSettled(state.zoom)
    }
}

@Composable
fun rememberPdfZoomState(initialZoom: Float): PdfZoomState = remember { PdfZoomState(initialZoom) }

private const val DOUBLE_TAP_DAMPING = 0.9f
private const val DOUBLE_TAP_STIFFNESS = 300f
