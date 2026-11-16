package dev.lector.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import dev.lector.app.ui.theme.LectorSpacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.lector.app.R
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.di.AppContainer
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.ui.common.LectorDialog
import dev.lector.app.ui.common.LectorSnackbarHost
import dev.lector.app.ui.common.LectorEasing
import dev.lector.app.ui.common.PrimaryButton
import dev.lector.app.ui.common.ErrorIllustration
import dev.lector.app.ui.common.StatePane
import dev.lector.app.ui.common.TertiaryButton
import dev.lector.app.ui.common.DestructiveButton
import dev.lector.app.ui.common.messageRes
import dev.lector.app.ui.home.HomeScreen
import dev.lector.app.ui.home.HomeViewModel
import dev.lector.app.ui.nav.Routes
import dev.lector.app.ui.scan.FallbackScanScreen
import dev.lector.app.ui.scan.FallbackScanViewModel
import dev.lector.app.ui.scan.NameScanSheet
import dev.lector.app.ui.scan.OrphanScanDialog
import dev.lector.app.ui.scan.SaveFailedDialog
import dev.lector.app.ui.scan.ScanEvent
import dev.lector.app.ui.scan.ScanState
import dev.lector.app.ui.scan.ScanViewModel
import dev.lector.app.ui.settings.LastErrorScreen
import dev.lector.app.ui.settings.LicensesScreen
import dev.lector.app.ui.settings.SettingsScreen
import dev.lector.app.ui.settings.SettingsViewModel
import dev.lector.app.ui.theme.LectorMotion
import dev.lector.app.ui.theme.LectorTheme
import dev.lector.app.ui.theme.LocalMotionEnabled
import dev.lector.app.ui.viewer.pdf.PdfViewerScreen
import dev.lector.app.ui.viewer.pdf.PdfViewerViewModel
import dev.lector.app.ui.viewer.text.TextViewerScreen
import dev.lector.app.ui.viewer.text.TextViewerViewModel
import dev.lector.app.ui.viewer.web.WebViewerScreen
import dev.lector.app.ui.viewer.web.WebViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tipos que ofrece el selector del sistema (PLAN §1.1). */
private val pickerMimeTypes = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
    "application/vnd.ms-powerpoint",
    "application/vnd.oasis.opendocument.presentation",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "application/vnd.oasis.opendocument.text",
    "application/rtf",
    "text/rtf",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/vnd.oasis.opendocument.spreadsheet",
    "text/csv",
    "text/plain",
    "text/markdown",
    "application/octet-stream",
)

@Composable
fun LectorRoot(viewModel: MainViewModel, settings: AppSettings, container: AppContainer) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val nav = rememberNavController()
    val openState by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var replaceRecentId by remember { mutableStateOf<Long?>(null) }

    // Un documento listo → su visor. Desde otra app es la raíz de la pila ("atrás" vuelve a esa app).
    LaunchedEffect(viewModel, nav) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is MainEvent.ShowDocument -> {
                    val route = Routes.viewerFor(event.docId, event.kind)
                    nav.navigate(route) {
                        launchSingleTop = true
                        if (event.external) popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Algunos proveedores no ofrecen permiso persistente: el documento se abre igual.
            }
            val replaces = replaceRecentId
            replaceRecentId = null
            viewModel.open(uri.toString(), external = false, replaces = replaces)
        }
    }

    // ---- Escáner: ML Kit (Google Play Services) y, si no está disponible, el modo simple.
    val scanVm: ScanViewModel = viewModel(factory = ScanViewModel.Factory)
    val scanState by scanVm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val savedLabel = stringResource(R.string.scan_saved)
    val shareLabel = stringResource(R.string.action_share)
    val fallbackNotice = stringResource(R.string.scan_fallback_notice)
    var failedName by remember { mutableStateOf("") }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // RESULT_CANCELED: no se hace nada.
        if (result.resultCode == Activity.RESULT_OK) {
            container.scanner.resultPdf(result.data)?.let { scanVm.onScannerResult(it.uri, it.pageCount) }
        }
    }
    val saveElsewhere = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scanVm.saveElsewhere(uri.toString(), failedName)
    }
    val startScan: () -> Unit = {
        val activity = context.findActivity()
        scope.launch {
            val sender = if (activity != null) container.scanner.startIntent(activity) else Outcome.Failure(DocError.ScannerUnavailable)
            when (sender) {
                is Outcome.Success -> try {
                    scannerLauncher.launch(IntentSenderRequest.Builder(sender.value).build())
                } catch (_: android.content.ActivityNotFoundException) {
                    nav.navigate(Routes.SCAN_SIMPLE) { launchSingleTop = true }
                }
                is Outcome.Failure -> {
                    // Sin Google Play Services: modo simple con un aviso.
                    nav.navigate(Routes.SCAN_SIMPLE) { launchSingleTop = true }
                    snackbar.showSnackbar(fallbackNotice)
                }
            }
        }
    }
    LaunchedEffect(scanVm, nav) {
        scanVm.eventFlow.collect { event ->
            when (event) {
                is ScanEvent.Snack -> scope.launch { snackbar.showSnackbar(resources.getString(event.messageRes)) }
                is ScanEvent.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    // Se sale del modo simple (si estaba) y se abre el PDF recién guardado en el visor.
                    if (nav.currentBackStackEntry?.destination?.route == Routes.SCAN_SIMPLE) nav.popBackStack(Routes.HOME, inclusive = false)
                    viewModel.open(event.uri, external = false)
                    scope.launch {
                        val result = snackbar.showSnackbar(savedLabel, actionLabel = shareLabel)
                        if (result == SnackbarResult.ActionPerformed) {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, event.uri.toUri())
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            launchSafely(context, Intent.createChooser(send, null))
                        }
                    }
                }
            }
        }
    }

    val goBack: () -> Unit = {
        if (nav.previousBackStackEntry != null) nav.popBackStack() else (context as? Activity)?.finish()
    }

    Box(Modifier.fillMaxSize().background(LectorTheme.colors.bg)) {
        LectorNavHost(
            nav = nav,
            settings = settings,
            goBack = goBack,
            onOpen = { picker.launch(pickerMimeTypes) },
            onScan = startScan,
            onCreateScan = scanVm::createFromImages,
            onOpenRecent = { recent -> viewModel.open(recent.uri, external = false, recentId = recent.id) },
            onShareRecent = { recent ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, recent.uri.toUri())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                launchSafely(context, Intent.createChooser(send, null))
            },
            onSettingsChange = { transform -> scope.launch { container.settings.update(transform) } },
        )

        OpeningOverlay(visible = openState == OpenState.Opening, onCancel = viewModel::cancelOpen)
        LectorSnackbarHost(
            state = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LectorSpacing.listBottomForDock),
        )
        ScanOverlays(
            state = scanState,
            onSave = scanVm::save,
            onDiscard = scanVm::discard,
            onAcceptOrphan = scanVm::acceptOrphan,
            onRetry = { scanVm.save(failedName.ifBlank { "" }) },
            onElsewhere = { saveElsewhere.launch("${failedName.ifBlank { "Escaneo" }}.pdf") },
            onNameKnown = { failedName = it },
        )
        (openState as? OpenState.Failed)?.let { failed ->
            if (failed.error == DocError.AccessLost && failed.recentId != null) {
                LectorDialog(
                    onDismiss = viewModel::dismissError,
                    title = stringResource(R.string.lost_access_title),
                    body = stringResource(R.string.err_access_lost),
                    confirm = {
                        PrimaryButton(stringResource(R.string.action_find_file), onClick = {
                            replaceRecentId = failed.recentId
                            viewModel.dismissError()
                            picker.launch(pickerMimeTypes)
                        })
                    },
                    dismiss = { DestructiveButton(stringResource(R.string.lost_access_remove), onClick = { viewModel.removeRecent(failed.recentId) }) },
                )
            } else {
                OpenErrorPane(
                    error = failed.error,
                    onBack = viewModel::dismissError,
                    onOpenWith = { launchSafely(context, openWithIntent(failed.uri)) },
                )
            }
        }
    }
}

@Composable
private fun LectorNavHost(
    nav: NavHostController,
    settings: AppSettings,
    goBack: () -> Unit,
    onOpen: () -> Unit,
    onScan: () -> Unit,
    onCreateScan: (List<String>) -> Unit,
    onOpenRecent: (dev.lector.app.domain.model.RecentDocument) -> Unit,
    onShareRecent: (dev.lector.app.domain.model.RecentDocument) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val motion = LocalMotionEnabled.current
    val offsetPx = with(LocalDensity.current) { LectorMotion.navOffset.roundToPx() }
    val enter: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (motion) fadeIn(tween(NAV_MS, easing = LectorEasing)) + slideInVertically(tween(NAV_MS, easing = LectorEasing)) { offsetPx } else EnterTransition.None
    }
    val exit: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (motion) fadeOut(tween(NAV_MS, easing = LectorEasing)) else ExitTransition.None
    }
    val popExit: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (motion) fadeOut(tween(NAV_MS, easing = LectorEasing)) + slideOutVertically(tween(NAV_MS, easing = LectorEasing)) { offsetPx } else ExitTransition.None
    }
    val docArg = listOf(navArgument(Routes.ARG_DOC_ID) { type = NavType.StringType })

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = { if (motion) fadeIn(tween(NAV_MS, easing = LectorEasing)) else EnterTransition.None },
        popExitTransition = popExit,
    ) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            HomeScreen(
                viewModel = vm,
                onOpen = onOpen,
                onScan = onScan,
                onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenRecent = onOpenRecent,
                onShareRecent = onShareRecent,
            )
        }
        composable(Routes.PDF, arguments = docArg) {
            val vm: PdfViewerViewModel = viewModel(factory = PdfViewerViewModel.Factory)
            PdfViewerScreen(
                viewModel = vm,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onBack = goBack,
                onOpenWithOther = { vm.sourceUri?.let { launchSafely(context, openWithIntent(it)) } },
            )
        }
        composable(Routes.WEB, arguments = docArg) {
            val vm: WebViewerViewModel = viewModel(factory = WebViewerViewModel.Factory)
            WebViewerScreen(viewModel = vm, onBack = goBack, onOpenWithOther = { vm.sourceUri?.let { launchSafely(context, openWithIntent(it)) } })
        }
        composable(Routes.TEXT, arguments = docArg) {
            val vm: TextViewerViewModel = viewModel(factory = TextViewerViewModel.Factory)
            TextViewerScreen(viewModel = vm, onBack = goBack, onOpenWithOther = { vm.sourceUri?.let { launchSafely(context, openWithIntent(it)) } })
        }
        composable(Routes.SCAN_SIMPLE) {
            val vm: FallbackScanViewModel = viewModel(factory = FallbackScanViewModel.Factory)
            FallbackScanScreen(viewModel = vm, onBack = goBack, onCreate = onCreateScan)
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            SettingsScreen(
                viewModel = vm,
                onBack = goBack,
                onLicenses = { nav.navigate(Routes.LICENSES) { launchSingleTop = true } },
                onLastError = { nav.navigate(Routes.LAST_ERROR) { launchSingleTop = true } },
            )
        }
        composable(Routes.LICENSES) { LicensesScreen(onBack = goBack) }
        composable(Routes.LAST_ERROR) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            LastErrorScreen(viewModel = vm, onBack = goBack)
        }
    }
}

/** Hoja "Nombrar", progreso de creación, errores de guardado y pendientes huérfanos. */
@Composable
private fun ScanOverlays(
    state: ScanState,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
    onAcceptOrphan: () -> Unit,
    onRetry: () -> Unit,
    onElsewhere: () -> Unit,
    onNameKnown: (String) -> Unit,
) {
    when (state) {
        ScanState.Idle -> Unit
        is ScanState.Working -> Box(Modifier.fillMaxSize().background(LectorTheme.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            StatePane(
                title = stringResource(state.label),
                body = if (state.total > 0) stringResource(R.string.viewer_page_counter, state.done, state.total) else stringResource(R.string.viewer_opening_body),
            )
        }
        is ScanState.Naming -> NameScanSheet(state, onSave = { name -> onNameKnown(name); onSave(name) }, onDiscard = onDiscard)
        is ScanState.Saving -> Unit
        is ScanState.SaveFailed -> SaveFailedDialog(state, onRetry = onRetry, onElsewhere = onElsewhere, onDiscard = onDiscard)
        is ScanState.OrphanPrompt -> OrphanScanDialog(onSave = onAcceptOrphan, onDiscard = onDiscard)
    }
}

/** Pantalla de apertura: aparece solo si la copia tarda más de 150 ms, con **Cancelar**. */
@Composable
private fun OpeningOverlay(visible: Boolean, onCancel: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(OVERLAY_DELAY_MS)
            show = true
        } else {
            show = false
        }
    }
    if (visible && show) {
        Box(Modifier.fillMaxSize().background(LectorTheme.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            StatePane(
                title = stringResource(R.string.viewer_opening_title),
                body = stringResource(R.string.viewer_opening_body),
                secondaryLabel = stringResource(R.string.action_cancel),
                onSecondary = onCancel,
            )
        }
    }
}

@Composable
private fun OpenErrorPane(error: DocError, onBack: () -> Unit, onOpenWith: () -> Unit) {
    val canOpenWith = error == DocError.UnsupportedFormat || error == DocError.LegacyOfficeFormat ||
        error == DocError.ConverterUnavailable || error == DocError.Corrupt
    Box(Modifier.fillMaxSize().background(LectorTheme.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        StatePane(
            title = stringResource(R.string.err_title),
            illustration = { ErrorIllustration() },
            body = stringResource(error.messageRes()),
            primaryLabel = stringResource(R.string.action_back),
            onPrimary = onBack,
            secondaryLabel = if (canOpenWith) stringResource(R.string.action_open_with) else null,
            onSecondary = if (canOpenWith) onOpenWith else null,
        )
    }
}

/** `ACTION_VIEW` sobre el `Uri` original, con permiso de lectura, para "Abrir con otra app". */
internal fun openWithIntent(uri: String): Intent =
    Intent.createChooser(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri.toUri(), "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        null,
    )

internal fun launchSafely(context: android.content.Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, R.string.err_launch_failed, android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        android.widget.Toast.makeText(context, R.string.err_launch_failed, android.widget.Toast.LENGTH_SHORT).show()
    }
}

private const val NAV_MS = 240
private const val OVERLAY_DELAY_MS = 150L
