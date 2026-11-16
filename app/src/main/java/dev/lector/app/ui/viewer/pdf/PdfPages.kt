package dev.lector.app.ui.viewer.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.rememberDraggableState
import dev.lector.app.ui.theme.LocalMotionEnabled
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import dev.lector.app.domain.model.FractionRect
import dev.lector.app.domain.model.SearchHit
import dev.lector.app.data.pdf.FrameworkPdfEngine
import dev.lector.app.ui.common.PageSkeleton
import dev.lector.app.ui.theme.LectorPaper
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** Matriz que invierte la luminancia (modo noche) sin tocar el color de fondo de la interfaz. */
private val InvertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** Cómo el visor pide bitmaps (con caché) sin conocer el motor. */
class PageSource(
    val aspects: PageAspects,
    val cached: (index: Int, widthPx: Int, heightPx: Int, region: RectF?) -> Bitmap?,
    val render: suspend (index: Int, widthPx: Int, heightPx: Int, region: RectF?) -> Bitmap?,
)

/**
 * Lista vertical continua con zoom. El contenido tiene ancho `viewport × zoom` y se desplaza en horizontal
 * con [PdfZoomState.panX]; la lista vertical la gestiona `LazyColumn` (PLAN §6.3).
 */
@Composable
fun PdfPages(
    pages: PageSource,
    pageCount: Int,
    zoom: PdfZoomState,
    list: LazyListState,
    night: Boolean,
    hits: List<SearchHit>,
    currentHit: SearchHit?,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onScrollForward: () -> Unit,
    onZoomSettled: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { LectorSpacing.pageSideMargin.toPx() }
    val scope = rememberCoroutineScope()
    val motion = LocalMotionEnabled.current
    val drag = rememberDraggableState { delta -> zoom.panBy(delta) }
    // Contador que sube cuando el zoom, el desplazamiento horizontal o vertical se estabilizan (150 ms).
    val hitsByPage = remember(hits) { hits.groupBy { it.page } }
    var settle by remember { mutableIntStateOf(0) }
    @OptIn(FlowPreview::class)
    LaunchedEffect(zoom, list) {
        snapshotFlow {
            listOf(zoom.zoom, zoom.panX, list.firstVisibleItemIndex.toFloat(), list.firstVisibleItemScrollOffset.toFloat())
        }.distinctUntilChanged().debounce(SETTLE_MS).collect { settle++ }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                zoom.viewport = it
                zoom.reclampPan()
            }
            .pdfGestures(zoom, list, scope, motion, drag, onTap, onScrollForward, onZoomSettled)
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.translation.x
                translationY = zoom.translation.y
                transformOrigin = if (size.width > 0f && size.height > 0f) {
                    TransformOrigin(zoom.pivot.x / size.width, zoom.pivot.y / size.height)
                } else {
                    TransformOrigin.Center
                }
            },
    ) {
        Layout(
            content = {
                val widthPx = (zoom.viewport.width * zoom.zoom - 2f * marginPx).coerceAtLeast(1f)
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(top = topInset, bottom = bottomInset),
                    verticalArrangement = Arrangement.spacedBy(LectorSpacing.pageGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(count = pageCount, key = { it }) { index ->
                        PdfPageItem(
                            index = index,
                            widthPx = widthPx.roundToInt(),
                            pages = pages,
                            night = night,
                            hits = hitsByPage[index].orEmpty(),
                            currentHit = currentHit?.takeIf { it.page == index },
                            settle = settle,
                            visibleRegion = { w, h -> visibleRegion(index, w, h, zoom, list, marginPx) },
                        )
                    }
                }
            },
            modifier = Modifier.graphicsLayer { translationX = zoom.panX },
        ) { measurables, constraints ->
            val width = (constraints.maxWidth * zoom.zoom).roundToInt().coerceAtLeast(1)
            val placeable = measurables.first().measure(Constraints.fixed(width, constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
        }
    }
}

/** Región (fracción de la página) que está a la vista; null si la página no se ve. */
private fun visibleRegion(
    index: Int,
    pageWidthPx: Int,
    pageHeightPx: Int,
    zoom: PdfZoomState,
    list: LazyListState,
    marginPx: Float,
): RectF? {
    val info = list.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    val padding = -info.viewportStartOffset
    val top = (item.offset + padding).toFloat()
    val viewportH = zoom.viewport.height.toFloat()
    val viewportW = zoom.viewport.width.toFloat()
    val x0 = (-zoom.panX - marginPx).coerceIn(0f, pageWidthPx.toFloat())
    val x1 = (-zoom.panX + viewportW - marginPx).coerceIn(0f, pageWidthPx.toFloat())
    val y0 = (-top).coerceIn(0f, pageHeightPx.toFloat())
    val y1 = (viewportH - top).coerceIn(0f, pageHeightPx.toFloat())
    if (x1 - x0 < 2f || y1 - y0 < 2f) return null
    return RectF(x0 / pageWidthPx, y0 / pageHeightPx, x1 / pageWidthPx, y1 / pageHeightPx)
}

@Composable
private fun PdfPageItem(
    index: Int,
    widthPx: Int,
    pages: PageSource,
    night: Boolean,
    hits: List<SearchHit>,
    currentHit: SearchHit?,
    settle: Int,
    visibleRegion: (widthPx: Int, heightPx: Int) -> RectF?,
) {
    val density = LocalDensity.current
    val aspect = pages.aspects.aspect(index)
    val heightPx = (widthPx / aspect).roundToInt().coerceAtLeast(1)
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    // El bitmap anterior se conserva (escalado) mientras llega el nuevo: nunca un hueco vacío.
    var base by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, widthPx, heightPx) {
        val cached = pages.cached(index, widthPx, heightPx, null)
        if (cached != null) {
            base = cached
        } else {
            pages.render(index, widthPx, heightPx, null)?.let { base = it }
        }
    }

    // Capa de alta resolución: solo si el bitmap base quedó limitado por el tope de memoria.
    val baseIsCapped = widthPx.toLong() * heightPx * BYTES_PER_PIXEL > FrameworkPdfEngine.maxBitmapBytes()
    var overlay by remember(index) { mutableStateOf<Pair<RectF, Bitmap>?>(null) }
    val currentVisibleRegion by rememberUpdatedState(visibleRegion)
    LaunchedEffect(index, widthPx, heightPx, settle, baseIsCapped) {
        if (!baseIsCapped) {
            overlay = null
            return@LaunchedEffect
        }
        val region = currentVisibleRegion(widthPx, heightPx) ?: return@LaunchedEffect
        val bitmap = pages.cached(index, widthPx, heightPx, region) ?: pages.render(index, widthPx, heightPx, region)
        if (bitmap != null) overlay = region to bitmap
    }

    val c = LectorTheme.colors
    val filter = if (night) ColorFilter.colorMatrix(InvertMatrix) else null
    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .clip(LectorShapes.Page)
            .background(LectorPaper.white),
    ) {
        val bitmap = base
        if (bitmap == null) {
            PageSkeleton(Modifier.fillMaxSize())
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        overlay?.let { (region, regionBitmap) ->
            val regionW = with(density) { ((region.right - region.left) * widthPx).toDp() }
            val regionH = with(density) { ((region.bottom - region.top) * heightPx).toDp() }
            Image(
                bitmap = regionBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = filter,
                modifier = Modifier
                    .offset { IntOffset((region.left * widthPx).roundToInt(), (region.top * heightPx).roundToInt()) }
                    .size(regionW, regionH),
            )
        }
        if (hits.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                for (hit in hits) {
                    val alpha = if (hit == currentHit) HIT_CURRENT_ALPHA else HIT_ALPHA
                    for (rect in hit.bounds) drawHit(rect, c.accent.copy(alpha = alpha))
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHit(rect: FractionRect, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(rect.left * size.width, rect.top * size.height),
        size = Size((rect.right - rect.left) * size.width, (rect.bottom - rect.top) * size.height),
    )
}

private const val SETTLE_MS = 150L
private const val BYTES_PER_PIXEL = 4
private const val HIT_ALPHA = 0.3f
private const val HIT_CURRENT_ALPHA = 0.6f
