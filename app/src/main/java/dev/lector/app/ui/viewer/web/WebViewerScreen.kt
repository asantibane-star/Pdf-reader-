package dev.lector.app.ui.viewer.web

import android.content.Intent
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lector.app.R
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.data.office.JsMessage
import dev.lector.app.data.office.SecureWebViewFactory
import dev.lector.app.data.office.SheetNames
import dev.lector.app.data.office.ViewerCallbacks
import dev.lector.app.data.office.ViewerRequestInterceptor
import dev.lector.app.data.office.ViewerTheme
import dev.lector.app.data.office.ViewerUrl
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.ui.common.FloatingPill
import dev.lector.app.ui.common.FloatingTopBar
import dev.lector.app.ui.common.IconCircleButton
import dev.lector.app.ui.common.LectorDialog
import dev.lector.app.ui.common.LectorSheet
import dev.lector.app.ui.common.PrimaryButton
import dev.lector.app.ui.common.SearchBar
import dev.lector.app.ui.common.SheetRow
import dev.lector.app.ui.common.SkeletonBox
import dev.lector.app.ui.common.ErrorIllustration
import dev.lector.app.ui.common.StatePane
import dev.lector.app.ui.common.TertiaryButton
import dev.lector.app.ui.common.messageRes
import dev.lector.app.ui.theme.LectorColors
import dev.lector.app.ui.theme.LectorShapes
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

@Composable
fun WebViewerScreen(viewModel: WebViewerViewModel, onBack: () -> Unit, onOpenWithOther: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Las presentaciones se ven sobre oscuro forzado, sea cual sea el tema (DESIGN §6.3).
    val forceDark = (state as? WebUiState.Ready)?.kind == DocumentKind.Pptx
    LectorTheme(forceDark = forceDark) {
        WebViewerContent(viewModel, state, onBack, onOpenWithOther)
    }
}

@Composable
private fun WebViewerContent(vm: WebViewerViewModel, state: WebUiState, onBack: () -> Unit, onOpenWithOther: () -> Unit) {
    val c = LectorTheme.colors
    val render by vm.render.collectAsStateWithLifecycle()
    val reloadToken by vm.reloadToken.collectAsStateWithLifecycle()
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }

    val isPresentation = (state as? WebUiState.Ready)?.kind == DocumentKind.Pptx
    Box(Modifier.fillMaxSize().background(if (isPresentation) c.canvas else c.surface)) {
        val error = render.error
        when {
            state is WebUiState.Loading -> LoadingSkeleton()
            state is WebUiState.Unsupported -> StatePane(
                title = stringResource(R.string.viewer_unavailable_title),
                body = stringResource(R.string.viewer_unavailable_body),
                primaryLabel = stringResource(R.string.action_open_with),
                onPrimary = onOpenWithOther,
                secondaryLabel = stringResource(R.string.action_back),
                onSecondary = onBack,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
            state is WebUiState.Error -> ErrorPane(state.error, vm::load, onBack, onOpenWithOther)
            error != null -> ErrorPane(error, vm::retry, onBack, onOpenWithOther)
            state is WebUiState.Ready -> WebContent(
                vm = vm, ready = state, render = render, reloadToken = reloadToken,
                searching = searching, onCloseSearch = { searching = false },
            )
        }

        if (!searching) {
            FloatingTopBar(
                title = vm.title,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = LectorSpacing.barTopGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin),
            ) {
                if (state is WebUiState.Ready && render.error == null) {
                    IconCircleButton(R.drawable.ic_magnifying_glass, stringResource(R.string.viewer_search), { searching = true })
                }
                if (state is WebUiState.Ready || state is WebUiState.Unsupported) {
                    IconCircleButton(R.drawable.ic_dots_three_vertical, stringResource(R.string.viewer_more), { showMenu = true })
                }
            }
        }
    }

    if (showMenu) {
        val context = LocalContext.current
        val saveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("*/*"),
        ) { uri -> if (uri != null) vm.saveCopy(uri) }
        LectorSheet(onDismiss = { showMenu = false }) {
            SheetRow(R.drawable.ic_share_network, stringResource(R.string.action_share), { showMenu = false; vm.share(context) })
            SheetRow(R.drawable.ic_download_simple, stringResource(R.string.viewer_save_copy), {
                showMenu = false
                saveLauncher.launch(vm.suggestedFileName())
            })
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    SkeletonBox(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = LectorSizes.barHeight + LectorSpacing.xl, start = LectorSpacing.screenMargin, end = LectorSpacing.screenMargin, bottom = LectorSpacing.xl),
    )
}

@Composable
private fun ErrorPane(error: DocError, onRetry: () -> Unit, onBack: () -> Unit, onOpenWithOther: () -> Unit) {
    val retryable = error != DocError.WebViewUnavailable && error != DocError.AccessLost
    StatePane(
        title = stringResource(R.string.err_title),
        illustration = { ErrorIllustration() },
        body = stringResource(error.messageRes()),
        primaryLabel = if (retryable) stringResource(R.string.action_retry) else stringResource(R.string.action_back),
        onPrimary = if (retryable) onRetry else onBack,
        secondaryLabel = stringResource(R.string.action_open_with),
        onSecondary = onOpenWithOther,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

@Composable
private fun WebContent(
    vm: WebViewerViewModel,
    ready: WebUiState.Ready,
    render: RenderUi,
    reloadToken: Int,
    searching: Boolean,
    onCloseSearch: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = LectorTheme.colors
    val language = LocalConfiguration.current.locales.get(0)?.language ?: DEFAULT_LANGUAGE
    var link by remember { mutableStateOf<String?>(null) }
    var sheetNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var found by remember { mutableStateOf(FindState()) }

    val interceptor = remember(vm, context) { ViewerRequestInterceptor(context.assets, vm::documentFor) }
    val callbacks = remember(vm) {
        object : ViewerCallbacks {
            override fun onMessage(message: JsMessage) = vm.onMessage(message)
            override fun onExternalLink(url: String) {
                link = url
            }
            override fun onRenderProcessGone() = vm.onRenderProcessGone()
        }
    }
    val created = remember(ready.nonce, reloadToken) { SecureWebViewFactory.create(context, interceptor, callbacks) }
    val webView: WebView? = (created as? Outcome.Success)?.value
    DisposableEffect(webView) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
        }
    }
    LaunchedEffect(created) { if (created is Outcome.Failure) vm.onWebViewUnavailable() }
    if (webView == null) return

    val topDp = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }.value.toInt() +
        (LectorSpacing.barTopGap + LectorSizes.barHeight + LectorSpacing.m).value.toInt()
    val bottomDp = if (render.sheetCount > 1) (LectorSizes.barHeight + LectorSpacing.barBottomGap + LectorSpacing.xl).value.toInt() else LectorSpacing.xl.value.toInt()
    val noticeText = stringResource(R.string.viewer_sheet_truncated, SHEET_MAX_ROWS, SHEET_MAX_COLS)
    val url = remember(ready.nonce, reloadToken, colors.isDark, colors.accent, topDp, bottomDp, language) {
        ViewerUrl.build(ready.jsKind, ready.nonce, colors.toViewerTheme(), topDp, bottomDp, noticeText, language)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView.onResume() }

    key(webView) {
        AndroidView(
            factory = { webView.also { it.loadUrl(url) } },
            modifier = Modifier.fillMaxSize().alpha(if (render.rendered) 1f else 0f),
        )
    }
    LaunchedEffect(render.rendered) {
        if (render.rendered) {
            webView.setOnScrollChangeListener { _, _, scrollY, _, _ -> vm.onScroll(scrollY) }
            webView.post { webView.scrollTo(0, vm.savedScrollY) }
        }
    }
    LaunchedEffect(render.sheetCount, render.rendered) {
        if (render.rendered && render.sheetCount > 1) {
            webView.evaluateJavascript("lectorApp.sheetNames()") { sheetNames = SheetNames.parse(it) }
        }
    }
    LaunchedEffect(webView) {
        webView.setFindListener { ordinal, total, _ -> found = FindState(current = ordinal, total = total) }
    }

    if (!render.rendered) LoadingSkeleton()

    // Vista aproximada: aviso permanente en .docx y .pptx (PLAN §7.6).
    if (ready.kind == DocumentKind.Docx || ready.kind == DocumentKind.Pptx) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .padding(top = LectorSpacing.barTopGap + LectorSizes.barHeight + LectorSpacing.s)
                    .clip(LectorShapes.Pill)
                    .background(colors.accentSoft)
                    .padding(horizontal = LectorSpacing.l, vertical = LectorSpacing.xs),
            ) {
                val approx = stringResource(R.string.viewer_approx_note)
                val detail = stringResource(R.string.viewer_approx_detail)
                Text(
                    approx,
                    style = LectorTheme.type.meta,
                    color = colors.onAccentSoft,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = "$approx. $detail" },
                )
            }
        }
    }

    if (render.sheetCount > 1 && render.rendered) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.BottomCenter) {
            SheetTabs(
                count = render.sheetCount,
                names = sheetNames,
                selected = render.activeSheet,
                onSelect = { index ->
                    vm.selectSheet(index)
                    webView.evaluateJavascript("lectorApp.showSheet($index)", null)
                },
                modifier = Modifier.padding(bottom = LectorSpacing.barBottomGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin),
            )
        }
    }

    if (searching) {
        var query by remember { mutableStateOf("") }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            SearchBar(
                query = query,
                current = (found.current).coerceAtLeast(0),
                total = found.total,
                searching = false,
                onQueryChange = { value ->
                    query = value
                    if (value.isBlank()) webView.clearMatches() else webView.findAllAsync(value)
                },
                onPrevious = { webView.findNext(false) },
                onNext = { webView.findNext(true) },
                onClose = {
                    webView.clearMatches()
                    onCloseSearch()
                },
                modifier = Modifier.padding(top = LectorSpacing.barTopGap, start = LectorSpacing.barMargin, end = LectorSpacing.barMargin),
            )
        }
    }

    link?.let { target ->
        LectorDialog(
            onDismiss = { link = null },
            title = stringResource(R.string.link_open_title),
            body = target,
            confirm = {
                PrimaryButton(stringResource(R.string.link_open_browser), onClick = {
                    link = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                    } catch (_: android.content.ActivityNotFoundException) {
                        android.widget.Toast.makeText(context, R.string.err_launch_failed, android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismiss = { TertiaryButton(stringResource(R.string.action_cancel), onClick = { link = null }) },
        )
    }
}

@Composable
private fun SheetTabs(count: Int, names: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LectorTheme.colors
    FloatingPill(modifier = modifier.fillMaxWidth().height(LectorSizes.barHeight)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = LectorSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(LectorSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(List(count) { it }) { index, _ ->
                val isSelected = index == selected
                val label = names.getOrNull(index)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.viewer_sheet_label, index + 1)
                Box(
                    Modifier
                        .height(LectorSizes.iconButton)
                        .clip(LectorShapes.Pill)
                        .background(if (isSelected) c.accentSoft else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = LectorSpacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = LectorTheme.type.button, color = if (isSelected) c.onAccentSoft else c.textMuted, maxLines = 1)
                }
            }
        }
    }
}

private data class FindState(val current: Int = 0, val total: Int = 0)

private fun LectorColors.toViewerTheme() = ViewerTheme(
    bg = bg.hex(), surface = surface.hex(), sunken = sunken.hex(), text = text.hex(),
    muted = textMuted.hex(), line = text.hex(), accent = accent.hex(),
)

private fun Color.hex(): String = "%06X".format(toArgb() and 0xFFFFFF)

private const val DEFAULT_LANGUAGE = "es"
private const val SHEET_MAX_ROWS = "2000"
private const val SHEET_MAX_COLS = "100"
