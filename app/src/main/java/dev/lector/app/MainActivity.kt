package dev.lector.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lector.app.data.intake.IntentUris
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.ui.LectorRoot
import dev.lector.app.ui.MainViewModel
import dev.lector.app.ui.theme.LectorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Tras recrear la actividad (rotación, proceso muerto) el intent ya se procesó una vez.
        if (savedInstanceState == null) handleIntent(intent)

        val container = (application as LectorApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            LaunchedEffect(settings.hideInRecents) {
                // "Ocultar contenido en apps recientes" → FLAG_SECURE.
                if (settings.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            LectorTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                LectorRoot(viewModel = viewModel, settings = settings, container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Un mismo intent no se procesa dos veces: se marca con un extra propio. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.getBooleanExtra(EXTRA_HANDLED, false)) return
        val uri = IntentUris.extract(intent) ?: return
        intent.putExtra(EXTRA_HANDLED, true)
        viewModel.open(uri, external = true)
    }

    private companion object {
        const val EXTRA_HANDLED = "dev.lector.app.extra.HANDLED"
    }
}
