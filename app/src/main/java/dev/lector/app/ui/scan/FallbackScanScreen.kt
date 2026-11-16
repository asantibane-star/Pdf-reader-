package dev.lector.app.ui.scan

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lector.app.R
import dev.lector.app.ui.common.FloatingPill
import dev.lector.app.ui.common.FloatingTopBar
import dev.lector.app.ui.common.IconCircleButton
import dev.lector.app.ui.common.LectorSnackbarHost
import dev.lector.app.ui.common.PrimaryButton
import dev.lector.app.ui.common.SkeletonBox
import dev.lector.app.ui.common.StatePane
import dev.lector.app.ui.common.TextIconButton
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme
import kotlin.math.min
import kotlinx.coroutines.launch

/** Modo simple (DESIGN §6.6): cuadrícula de 2 columnas, arrastrar para reordenar, quitar página y dock inferior. */
@Composable
fun FallbackScanScreen(viewModel: FallbackScanViewModel, onBack: () -> Unit, onCreate: (List<String>) -> Unit) {
    val context = LocalContext.current
    val c = LectorTheme.colors
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noApp = stringResource(R.string.err_launch_failed)

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> viewModel.onCameraResult(ok) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxPickable())) { uris ->
        viewModel.addFromGallery(uris.map(Uri::toString))
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (pages.isEmpty()) {
            StatePane(
                title = stringResource(R.string.scan_empty_title),
                body = stringResource(R.string.scan_empty_body),
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        } else {
            PageGrid(pages, viewModel)
        }

        FloatingTopBar(
            title = stringResource(R.string.scan_simple_title),
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = LectorSpacing.barTopGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin),
        )

        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LectorSnackbarHost(snackbar)
            FloatingPill(Modifier.padding(LectorSpacing.dockPadding).padding(bottom = LectorSpacing.dockBottomGap)) {
                TextIconButton(stringResource(R.string.scan_camera), R.drawable.ic_camera, onClick = {
                    val file = viewModel.prepareCameraFile()
                    if (file != null) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        try {
                            camera.launch(uri)
                        } catch (_: android.content.ActivityNotFoundException) {
                            scope.launch { snackbar.showSnackbar(noApp) }
                        }
                    }
                })
                TextIconButton(stringResource(R.string.scan_gallery), R.drawable.ic_image, onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                PrimaryButton(
                    text = stringResource(R.string.scan_create_pdf),
                    onClick = { onCreate(viewModel.sources()) },
                    enabled = pages.isNotEmpty(),
                )
            }
        }
    }
}

private fun maxPickable(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        min(FallbackScanViewModel.MAX_PAGES, MediaStore.getPickImagesMaxLimit())
    } else {
        FallbackScanViewModel.MAX_PAGES
    }

@Composable
private fun PageGrid(pages: List<ScanPage>, viewModel: FallbackScanViewModel) {
    val grid = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val moveUp = stringResource(R.string.scan_move_up)
    val moveDown = stringResource(R.string.scan_move_down)
    val remove = stringResource(R.string.scan_remove_page)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = grid,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(
            start = LectorSpacing.screenMargin, end = LectorSpacing.screenMargin,
            top = LectorSizes.barHeight + LectorSpacing.barTopGap + LectorSpacing.xl,
            bottom = LectorSpacing.listBottomForDock + LectorSizes.buttonHeight,
        ),
        verticalArrangement = Arrangement.spacedBy(LectorSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(LectorSpacing.m),
    ) {
        items(pages, key = { it.id }) { page ->
            val index = pages.indexOfFirst { it.id == page.id }
            val dragging = draggingId == page.id
            Box(
                Modifier
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        if (dragging) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = DRAG_SCALE
                            scaleY = DRAG_SCALE
                        }
                    }
                    .semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUp) { viewModel.move(index, index - 1); true })
                            if (index < pages.lastIndex) add(CustomAccessibilityAction(moveDown) { viewModel.move(index, index + 1); true })
                            add(CustomAccessibilityAction(remove) { viewModel.remove(page.id); true })
                        }
                    }
                    .pointerInput(page.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = page.id
                                dragOffset = Offset.Zero
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, delta ->
                                change.consume()
                                dragOffset += delta
                                val visible = grid.layoutInfo.visibleItemsInfo
                                val current = visible.firstOrNull { it.key == page.id } ?: return@detectDragGesturesAfterLongPress
                                val center = current.offset.toOffset() + Offset(current.size.width / 2f, current.size.height / 2f) + dragOffset
                                val target = visible.firstOrNull { it.key != page.id && it.bounds().contains(center) }
                                if (target != null) {
                                    // El elemento cambia de hueco: se compensa para que siga bajo el dedo.
                                    dragOffset += (current.offset - target.offset).toOffset()
                                    viewModel.move(current.index, target.index)
                                }
                            },
                            onDragEnd = { draggingId = null; dragOffset = Offset.Zero },
                            onDragCancel = { draggingId = null; dragOffset = Offset.Zero },
                        )
                    },
            ) {
                PageCell(page, index + 1, viewModel) { viewModel.remove(page.id) }
            }
        }
    }
}

@Composable
private fun PageCell(page: ScanPage, number: Int, viewModel: FallbackScanViewModel, onRemove: () -> Unit) {
    val c = LectorTheme.colors
    val thumbnail by produceState<ImageBitmap?>(null, page.id) { value = viewModel.thumbnail(page)?.asImageBitmap() }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(LectorShapes.Thumbnail)
            .background(c.sunken),
    ) {
        val image = thumbnail
        if (image == null) {
            SkeletonBox(Modifier.fillMaxWidth().size(width = LectorSizes.thumbPortraitWidth * 2, height = LectorSizes.thumbPortraitHeight * 2), LectorShapes.Thumbnail)
        } else {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(LectorSpacing.s)
                .clip(LectorShapes.Pill)
                .background(c.surface.copy(alpha = BADGE_ALPHA))
                .padding(horizontal = LectorSpacing.m, vertical = LectorSpacing.xs),
        ) {
            Text(stringResource(R.string.scan_page_number, number), style = LectorTheme.type.mono, color = c.text, maxLines = 1)
        }
        IconCircleButton(
            icon = R.drawable.ic_x,
            contentDescription = stringResource(R.string.scan_remove_page),
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd),
            outlined = true,
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridItemInfo.bounds(): Rect = Rect(offset.toOffset(), size.toSize())

private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())

private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())

private const val DRAG_SCALE = 1.04f
private const val BADGE_ALPHA = 0.94f
