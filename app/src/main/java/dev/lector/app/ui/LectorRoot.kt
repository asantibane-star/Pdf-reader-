package dev.lector.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import dev.lector.app.di.AppContainer
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.ui.common.LectorDialog
import dev.lector.app.ui.common.LectorEasing
import dev.lector.app.ui.common.PrimaryButton
import dev.lector.app.ui.common.StatePane
import dev.lector.app.ui.common.TertiaryButton
import dev.lector.app.ui.common.DestructiveButton
import dev.lector.app.ui.common.messageRes
import dev.lector.app.ui.home.HomeScreen
import dev.lector.app.ui.home.HomeViewModel
import dev.lector.app.ui.nav.Routes
import dev.lector.app.ui.settings.LastErrorScreen
import dev.lector.app.ui.settings.LicensesScreen
import dev.lector.app.ui.settings.SettingsScreen
import dev.lector.app.ui.settings.SettingsViewModel
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

    val goBack: () -> Unit = {
        if (nav.previousBackStackEntry != null) nav.popBackStack() else (context as? Activity)?.finish()
    }

    Box(Modifier.fillMaxSize().background(LectorTheme.colors.bg)) {
        LectorNavHost(
            nav = nav,
            settings = settings,
            goBack = goBack,
            onOpen = { picker.launch(pickerMimeTypes) },
            onScan = { nav.navigate(Routes.SCAN_SIMPLE) { launchSingleTop = true } },
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
    onOpenRecent: (dev.lector.app.domain.model.RecentDocument) -> Unit,
    onShareRecent: (dev.lector.app.domain.model.RecentDocument) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val motion = LocalMotionEnabled.current
    val offsetPx = with(LocalDensity.current) { 12.dp.roundToPx() }
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
