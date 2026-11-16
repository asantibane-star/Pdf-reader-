package dev.lector.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lector.app.R
import dev.lector.app.domain.model.RecentDocument
import dev.lector.app.domain.usecase.RecentGroupKind
import dev.lector.app.domain.usecase.groupRecents
import dev.lector.app.ui.common.ContinueCard
import dev.lector.app.ui.common.EmptyIllustration
import dev.lector.app.ui.common.FloatingChrome
import dev.lector.app.ui.common.FloatingDock
import dev.lector.app.ui.common.HomeSkeleton
import dev.lector.app.ui.common.IconCircleButton
import dev.lector.app.ui.common.LectorEasing
import dev.lector.app.ui.common.LectorSheet
import dev.lector.app.ui.common.LectorSnackbarHost
import dev.lector.app.ui.common.RecentRow
import dev.lector.app.ui.common.SheetRow
import dev.lector.app.ui.common.StatePane
import dev.lector.app.ui.common.SwipeToRemove
import dev.lector.app.ui.theme.LectorMotion
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme
import dev.lector.app.ui.theme.LocalMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpen: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    onShareRecent: (RecentDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LectorTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val removedMessage = stringResource(R.string.recent_removed)
    val undoLabel = stringResource(R.string.action_undo)
    var menuFor by remember { mutableStateOf<RecentDocument?>(null) }

    // El esqueleto solo aparece si la lectura tarda más de 150 ms.
    var showSkeleton by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (state.loading) {
            delay(SKELETON_DELAY_MS)
            showSkeleton = true
        } else {
            showSkeleton = false
        }
    }

    val removeWithUndo: (RecentDocument) -> Unit = { recent ->
        viewModel.remove(recent)
        scope.launch {
            val result = snackbar.showSnackbar(removedMessage, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(recent)
        }
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> if (showSkeleton) {
                    Column(Modifier.widthIn(max = LectorSizes.maxContentWidth)) {
                        HomeHeader(onSettings)
                        HomeSkeleton()
                    }
                }
                state.recents.isEmpty() -> Column(Modifier.widthIn(max = LectorSizes.maxContentWidth).fillMaxSize()) {
                    HomeHeader(onSettings)
                    StatePane(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        illustration = { EmptyIllustration() },
                    )
                }
                else -> RecentsList(
                    recents = state.recents,
                    list = list,
                    viewModel = viewModel,
                    onSettings = onSettings,
                    onOpenRecent = onOpenRecent,
                    onLongClick = { menuFor = it },
                    onRemove = removeWithUndo,
                )
            }
        }

        // Al desplazar, el título se contrae a `title` en una barra superior con fondo `bg`.
        val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > COLLAPSE_OFFSET_PX } }
        FloatingChrome(visible = collapsed && state.recents.isNotEmpty(), fromTop = true, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = LectorSpacing.screenMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.home_title), style = LectorTheme.type.title, color = c.text, modifier = Modifier.weight(1f))
                IconCircleButton(R.drawable.ic_gear, stringResource(R.string.home_settings), onSettings, outlined = true)
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LectorSnackbarHost(snackbar)
            Spacer(Modifier.height(LectorSpacing.s))
            FloatingDock(onOpen = onOpen, onScan = onScan, modifier = Modifier.padding(bottom = LectorSpacing.dockBottomGap))
        }
    }

    menuFor?.let { recent ->
        LectorSheet(onDismiss = { menuFor = null }) {
            SheetRow(R.drawable.ic_share_network, stringResource(R.string.recent_share), { menuFor = null; onShareRecent(recent) })
            SheetRow(R.drawable.ic_trash, stringResource(R.string.recent_remove), { menuFor = null; removeWithUndo(recent) }, destructive = true)
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    val c = LectorTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LectorSpacing.screenMargin, vertical = LectorSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.home_title), style = LectorTheme.type.display, color = c.text, modifier = Modifier.weight(1f))
        IconCircleButton(R.drawable.ic_gear, stringResource(R.string.home_settings), onSettings, outlined = true)
    }
}

@Composable
private fun RecentsList(
    recents: List<RecentDocument>,
    list: androidx.compose.foundation.lazy.LazyListState,
    viewModel: HomeViewModel,
    onSettings: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    onLongClick: (RecentDocument) -> Unit,
    onRemove: (RecentDocument) -> Unit,
) {
    val now = remember(recents) { System.currentTimeMillis() }
    val featured = recents.first()
    val groups = remember(recents) { groupRecents(recents.drop(1), now) }
    var entranceDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ENTRANCE_WINDOW_MS)
        entranceDone = true
    }
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, featured.id) {
        value = viewModel.thumbnail(featured.id)?.asImageBitmap()
    }

    LazyColumn(
        state = list,
        modifier = Modifier.widthIn(max = LectorSizes.maxContentWidth).fillMaxSize(),
        contentPadding = PaddingValues(bottom = LectorSpacing.listBottomForDock),
    ) {
        item(key = "header") { HomeHeader(onSettings) }
        item(key = "continue") {
            Box(Modifier.padding(horizontal = LectorSpacing.screenMargin)) {
                ContinueCard(featured, thumbnail, onClick = { onOpenRecent(featured) })
            }
        }
        var rowIndex = 0
        groups.forEach { group ->
            item(key = "group-${group.kind}") {
                Text(
                    text = stringResource(group.kind.labelRes()),
                    style = LectorTheme.type.metaGroup,
                    color = LectorTheme.colors.textMuted,
                    modifier = Modifier.padding(start = LectorSpacing.screenMargin, end = LectorSpacing.screenMargin, top = LectorSpacing.group, bottom = LectorSpacing.xs),
                )
            }
            items(group.items, key = { it.id }) { recent ->
                val index = rowIndex++
                Box(
                    Modifier
                        .padding(horizontal = LectorSpacing.screenMargin)
                        .animateEntrance(index, play = !entranceDone),
                ) {
                    SwipeToRemove(onRemove = { onRemove(recent) }) {
                        Box(Modifier.background(LectorTheme.colors.bg)) {
                            RecentRow(recent, now, onClick = { onOpenRecent(recent) }, onLongClick = { onLongClick(recent) })
                        }
                    }
                }
            }
        }
    }
}

private fun RecentGroupKind.labelRes(): Int = when (this) {
    RecentGroupKind.Today -> R.string.group_today
    RecentGroupKind.Week -> R.string.group_week
    RecentGroupKind.Month -> R.string.group_month
    RecentGroupKind.Older -> R.string.group_older
}

/** Entrada de la lista en el primer dibujo: fundido + 8 dp, escalonado 30 ms, máximo 8 filas. */
@Composable
private fun Modifier.animateEntrance(index: Int, play: Boolean): Modifier {
    val motion = LocalMotionEnabled.current
    val animate = play && motion && index < ENTRANCE_MAX_ROWS
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val offsetPx = with(LocalDensity.current) { LectorMotion.entranceOffset.toPx() }
    LaunchedEffect(animate) {
        if (animate) {
            delay(index * ENTRANCE_STAGGER_MS)
            progress.animateTo(1f, tween(ENTRANCE_MS, easing = LectorEasing))
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

private const val SKELETON_DELAY_MS = 150L
private const val COLLAPSE_OFFSET_PX = 120
private const val ENTRANCE_WINDOW_MS = 700L
private const val ENTRANCE_MAX_ROWS = 8
private const val ENTRANCE_STAGGER_MS = 30L
private const val ENTRANCE_MS = 240
