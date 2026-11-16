package dev.lector.app.ui.viewer.pdf

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lector.app.R
import dev.lector.app.core.result.DocError
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.ui.common.FloatingChrome
import dev.lector.app.ui.findActivity
import dev.lector.app.ui.common.FloatingPageBar
import dev.lector.app.ui.common.FloatingPill
import dev.lector.app.ui.common.FloatingTopBar
import dev.lector.app.ui.common.IconCircleButton
import dev.lector.app.ui.common.LectorDialog
import dev.lector.app.ui.common.LectorSheet
import dev.lector.app.ui.common.LectorSnackbarHost
import dev.lector.app.ui.common.LectorSwitch
import dev.lector.app.ui.common.LectorTextField
import dev.lector.app.ui.common.PageSkeleton
import dev.lector.app.ui.common.SearchBar
import dev.lector.app.ui.common.PrimaryButton
import dev.lector.app.ui.common.SheetRow
import dev.lector.app.ui.common.ErrorIllustration
import dev.lector.app.ui.common.StatePane
import dev.lector.app.ui.common.TertiaryButton
import dev.lector.app.ui.common.messageRes
import dev.lector.app.ui.theme.DarkLectorColors
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onOpenWithOther: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val night by viewModel.night.collectAsStateWithLifecycle()
    val canvas = if (night) DarkLectorColors.canvas else LectorTheme.colors.canvas
    Box(Modifier.fillMaxSize().background(canvas)) {
        when (val current = state) {
            PdfUiState.Loading -> LoadingPane(viewModel.title, onBack)
            is PdfUiState.Content -> PdfContent(viewModel, current, settings, onSettingsChange, night, onBack)
            is PdfUiState.PasswordNeeded -> {
                LoadingPane(viewModel.title, onBack)
                PasswordDialog(
                    wrong = current.wrong,
                    onSubmit = viewModel::submitPassword,
                    onCancel = onBack,
                )
            }
            is PdfUiState.Error -> ErrorPane(viewModel.title, current, onBack, viewModel::retry, onOpenWithOther)
        }
    }
}

@Composable
private fun LoadingPane(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // Esqueleto con la forma de una página (DESIGN §6.2): nunca un indicador circular.
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = LectorSizes.barHeight + LectorSpacing.barTopGap + LectorSpacing.pageGap)
                .padding(horizontal = LectorSpacing.pageSideMargin),
        ) { PageSkeleton(Modifier.fillMaxSize()) }
        FloatingTopBar(
            title = title,
            onBack = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = LectorSpacing.barTopGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin),
        )
    }
}

@Composable
private fun ErrorPane(
    title: String,
    error: PdfUiState.Error,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenWithOther: () -> Unit,
) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        StatePane(
            title = title.ifBlank { stringResource(R.string.app_name) },
            illustration = { ErrorIllustration() },
            body = stringResource(error.error.messageRes(error.passwordSupported)),
            primaryLabel = stringResource(R.string.action_retry).takeIf { error.error != DocError.PasswordRequired },
            onPrimary = onRetry.takeIf { error.error != DocError.PasswordRequired },
            secondaryLabel = stringResource(R.string.action_open_with),
            onSecondary = onOpenWithOther,
        )
        FloatingTopBar(
            title = "",
            onBack = onBack,
            modifier = Modifier.padding(top = LectorSpacing.barTopGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin),
        )
    }
}

@Composable
private fun PasswordDialog(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    LectorDialog(
        onDismiss = onCancel,
        title = stringResource(R.string.viewer_password_title),
        confirm = { PrimaryButton(stringResource(R.string.action_open), onClick = { onSubmit(value.text) }, enabled = value.text.isNotEmpty()) },
        dismiss = { TertiaryButton(stringResource(R.string.action_cancel), onClick = onCancel) },
    ) {
        LectorTextField(
            value = value,
            onValueChange = { value = it },
            label = stringResource(R.string.viewer_password_label),
            error = if (wrong) stringResource(R.string.viewer_password_wrong) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            requestFocus = true,
        )
    }
}

@Composable
private fun PdfContent(
    vm: PdfViewerViewModel,
    state: PdfUiState.Content,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    night: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val appName = stringResource(R.string.app_name)
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.initialPage)
    val zoom = rememberPdfZoomState(state.initialZoom)
    val page by vm.page.collectAsStateWithLifecycle()
    val aspects by vm.aspects.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var activity by remember { mutableIntStateOf(0) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showGoTo by rememberSaveable { mutableStateOf(false) }
    var sliderPage by remember { mutableStateOf<Int?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveCopy(uri)
    }

    // Insets estables: no cambian al ocultar las barras del sistema (evita saltos de la lista).
    val topSystem = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val bottomSystem = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    var stableTop by remember { mutableStateOf(topSystem) }
    var stableBottom by remember { mutableStateOf(bottomSystem) }
    if (topSystem > stableTop) stableTop = topSystem
    if (bottomSystem > stableBottom) stableBottom = bottomSystem
    val showPageBar = state.pageCount >= PAGE_BAR_MIN_PAGES
    val topInset = stableTop + LectorSpacing.barTopGap + LectorSizes.barHeight + LectorSpacing.pageGap
    val bottomInset = stableBottom + (if (showPageBar) LectorSpacing.barBottomGap + LectorSizes.barHeight else LectorSpacing.none) + LectorSpacing.pageGap

    // Página actual = la que contiene el centro de la vista.
    LaunchedEffect(list) {
        snapshotFlow {
            val info = list.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.firstOrNull { center >= it.offset && center < it.offset + it.size }?.index
                ?: list.firstVisibleItemIndex
        }.distinctUntilChanged().collect { vm.onPageChanged(it) }
    }

    // Saltos pedidos (deslizador, "Ir a página", resultados de búsqueda), mensajes y acciones externas.
    LaunchedEffect(vm, list) {
        vm.effectFlow.collect { effect ->
            when (effect) {
                is PdfEffect.ScrollToPage -> {
                    val pageWidth = zoom.viewport.width * zoom.zoom - 2f * with(density) { LectorSpacing.pageSideMargin.toPx() }
                    val pageHeight = pageWidth / aspects.aspect(effect.page)
                    val offset = (pageHeight * effect.yFraction - zoom.viewport.height / HIT_VIEW_DIVISOR).roundToInt()
                    list.scrollToItem(effect.page, offset.coerceAtLeast(0))
                }
                is PdfEffect.Snack -> scope.launch { snackbar.showSnackbar(resources.getString(effect.messageRes)) }
                is PdfEffect.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, effect.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    launchExternal(context, Intent.createChooser(send, null)) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_launch_failed)) }
                    }
                }
                is PdfEffect.Print -> if (!PdfPrinter.print(context, effect.path, effect.jobName)) {
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_save)) }
                }
            }
        }
    }

    // Auto-ocultar a los 3 s de inactividad (no durante la búsqueda ni con el menú abierto).
    LaunchedEffect(chromeVisible, activity, search.active, showMenu, showGoTo) {
        if (chromeVisible && !search.active && !showMenu && !showGoTo && sliderPage == null) {
            delay(AUTO_HIDE_MS)
            chromeVisible = false
        }
    }

    // Modo inmersivo: con las barras ocultas, las del sistema también.
    val window = remember(view) { view.context.findActivity()?.window }
    DisposableEffect(chromeVisible, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {}
    }
    DisposableEffect(window) {
        onDispose { window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = search.active) { vm.closeSearch() }

    val showControlsLabel = stringResource(R.string.viewer_show_controls)
    Box(
        Modifier
            .fillMaxSize()
            .semantics { customActions = listOf(CustomAccessibilityAction(showControlsLabel) { chromeVisible = true; true }) },
    ) {
        val pages = remember(aspects, vm) {
            PageSource(
                aspects = aspects,
                cached = { i, w, h, r -> vm.cachedBitmap(i, w, h, r) },
                render = { i, w, h, r -> vm.renderPage(i, w, h, r) },
            )
        }
        PdfPages(
            pages = pages,
            pageCount = state.pageCount,
            zoom = zoom,
            list = list,
            night = night,
            hits = search.hits,
            currentHit = search.hits.getOrNull(search.current),
            topInset = topInset,
            bottomInset = bottomInset,
            onTap = { chromeVisible = !chromeVisible; activity++ },
            onScrollForward = { chromeVisible = false },
            onZoomSettled = vm::onZoomChanged,
        )

        FloatingChrome(visible = chromeVisible, fromTop = true, modifier = Modifier.align(Alignment.TopCenter)) {
            val barModifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = LectorSpacing.barTopGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin)
            if (search.active) {
                SearchBar(
                    query = search.query,
                    current = search.current,
                    total = search.hits.size,
                    searching = search.searching,
                    onQueryChange = vm::onQueryChanged,
                    onPrevious = vm::previousHit,
                    onNext = vm::nextHit,
                    onClose = vm::closeSearch,
                    modifier = barModifier,
                )
            } else {
                FloatingTopBar(title = state.title, onBack = onBack, modifier = barModifier) {
                    if (state.canSearch) {
                        IconCircleButton(R.drawable.ic_magnifying_glass, stringResource(R.string.viewer_search), { vm.openSearch(); activity++ })
                    }
                    IconCircleButton(R.drawable.ic_dots_three_vertical, stringResource(R.string.viewer_more), { showMenu = true })
                }
            }
        }

        if (showPageBar) {
            FloatingChrome(visible = chromeVisible, fromTop = false, modifier = Modifier.align(Alignment.BottomCenter)) {
                FloatingPageBar(
                    page = sliderPage ?: page,
                    pageCount = state.pageCount,
                    onPageChange = { target ->
                        sliderPage = target
                        vm.requestPage(target)
                    },
                    onChangeFinished = { sliderPage = null; activity++ },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = LectorSpacing.barBottomGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin),
                )
            }
        }

        LectorSnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = if (showPageBar) LectorSpacing.barBottomGap + LectorSizes.barHeight + LectorSpacing.m else LectorSpacing.barBottomGap),
        )
    }

    if (showMenu) {
        LectorSheet(onDismiss = { showMenu = false }) {
            SheetRow(R.drawable.ic_hash, stringResource(R.string.viewer_go_to_page), { showMenu = false; showGoTo = true })
            SheetRow(R.drawable.ic_share_network, stringResource(R.string.action_share), { showMenu = false; vm.share() })
            SheetRow(R.drawable.ic_download_simple, stringResource(R.string.viewer_save_copy), {
                showMenu = false
                if (!busy) saveLauncher.launch("${state.title.ifBlank { appName }}.pdf")
            })
            SheetRow(R.drawable.ic_printer, stringResource(R.string.viewer_print), { showMenu = false; vm.print() })
            SheetRow(R.drawable.ic_moon, stringResource(R.string.viewer_night_mode), { vm.toggleNight() }, trailing = {
                LectorSwitch(checked = night, onCheckedChange = { vm.toggleNight() })
            })
            SheetRow(R.drawable.ic_sun, stringResource(R.string.viewer_keep_screen_on), {
                onSettingsChange { it.copy(keepScreenOn = !it.keepScreenOn) }
            }, trailing = {
                LectorSwitch(checked = settings.keepScreenOn, onCheckedChange = { value -> onSettingsChange { it.copy(keepScreenOn = value) } })
            })
        }
    }
    if (showGoTo) {
        GoToPageDialog(pageCount = state.pageCount, onDismiss = { showGoTo = false }, onGo = { vm.requestPage(it - 1); showGoTo = false })
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val target = value.text.toIntOrNull()?.takeIf { it in 1..pageCount }
    var showError by rememberSaveable { mutableStateOf(false) }
    LectorDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.viewer_go_to_page),
        confirm = {
            PrimaryButton(stringResource(R.string.viewer_go), onClick = { if (target != null) onGo(target) else showError = true })
        },
        dismiss = { TertiaryButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    ) {
        LectorTextField(
            value = value,
            onValueChange = { value = it.copy(text = it.text.filter(Char::isDigit).take(MAX_DIGITS)); showError = false },
            label = stringResource(R.string.viewer_go_to_page_label),
            error = if (showError) stringResource(R.string.viewer_go_to_page_error, pageCount) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            requestFocus = true,
        )
    }
}

private fun launchExternal(context: Context, intent: Intent, onMissing: () -> Unit) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onMissing()
    } catch (_: SecurityException) {
        onMissing()
    }
}

private const val AUTO_HIDE_MS = 3_000L
private const val PAGE_BAR_MIN_PAGES = 4
private const val MAX_DIGITS = 6
private const val HIT_VIEW_DIVISOR = 3
