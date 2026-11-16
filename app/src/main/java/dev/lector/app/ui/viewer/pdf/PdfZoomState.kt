package dev.lector.app.ui.viewer.pdf

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Estado del zoom del visor (PLAN §6.3). El zoom "consolidado" [zoom] (1×–5×) ensancha el contenido y
 * [panX] lo desplaza. Durante un gesto el cambio es una transformación transitoria en espacio de pantalla
 * (`graphicsLayer`, barata): `P0 + scale·(x − P0) + translation`; al soltar se consolida con [commit].
 */
@Stable
class PdfZoomState(initialZoom: Float = MIN_ZOOM) {
    var zoom by mutableFloatStateOf(initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var viewport by mutableStateOf(IntSize.Zero)

    var scale by mutableFloatStateOf(1f)
        private set
    var pivot by mutableStateOf(Offset.Zero)
        private set
    var translation by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = zoom > ZOOMED_THRESHOLD

    fun clampPan(pan: Float, forZoom: Float = zoom): Float {
        val minPan = -(viewport.width * (forZoom - 1f)).coerceAtLeast(0f)
        return pan.coerceIn(minPan, 0f)
    }

    fun panBy(dx: Float) {
        panX = clampPan(panX + dx)
    }

    fun reclampPan() {
        panX = clampPan(panX)
    }

    fun beginPinch(startCentroid: Offset) {
        scale = 1f
        translation = Offset.Zero
        pivot = startCentroid
    }

    /** Aplica un paso del pellizco manteniendo bajo los dedos el mismo punto del contenido. */
    fun updatePinch(zoomChange: Float, centroid: Offset, previousCentroid: Offset) {
        val newScale = (scale * zoomChange).coerceIn(MIN_ZOOM / zoom, MAX_ZOOM / zoom)
        val effective = newScale / scale
        translation = centroid - pivot - (previousCentroid - translation - pivot) * effective
        scale = newScale
    }

    /** Doble toque: sin traslación, el pivote es el punto tocado. */
    fun beginAnimatedZoom(at: Offset) {
        pivot = at
        translation = Offset.Zero
        scale = 1f
    }

    fun setAnimatedScale(value: Float) {
        scale = value.coerceIn(MIN_ZOOM / zoom, MAX_ZOOM / zoom)
    }

    /** Zoom objetivo del doble toque: 1× ↔ 2,5×. */
    fun doubleTapTarget(): Float = if (isZoomed) MIN_ZOOM else DOUBLE_TAP_ZOOM

    /** Consolida la transformación transitoria en `zoom`, `panX` y la posición de la lista vertical. */
    fun commit(list: LazyListState) {
        val newZoom = (zoom * scale).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val effective = newZoom / zoom
        val info = list.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull()
        val newPan = clampPan(effective * panX + (1f - effective) * pivot.x + translation.x, newZoom)
        if (first != null) {
            val padding = -info.viewportStartOffset
            val topOnScreen = first.offset + padding
            val newTop = pivot.y + effective * (topOnScreen - pivot.y) + translation.y
            list.requestScrollToItem(first.index, (padding - newTop).roundToInt())
        }
        zoom = newZoom
        panX = newPan
        scale = 1f
        translation = Offset.Zero
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
        const val DOUBLE_TAP_ZOOM = 2.5f
        private const val ZOOMED_THRESHOLD = 1.01f
    }
}
