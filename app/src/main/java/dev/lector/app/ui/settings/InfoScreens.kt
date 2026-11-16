package dev.lector.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lector.app.R
import dev.lector.app.ui.common.IconCircleButton
import dev.lector.app.ui.common.SecondaryButton
import dev.lector.app.ui.theme.LectorSizes
import dev.lector.app.ui.theme.LectorSpacing
import dev.lector.app.ui.theme.LectorTheme

/** Componentes de terceros incluidos en la app. El detalle (versión, origen, SHA-256) está en `THIRD_PARTY.md`. */
private data class Notice(val name: String, val license: String, val note: String)

private val notices = listOf(
    Notice("Geist y Geist Mono", "SIL Open Font License 1.1", "Tipografías de la interfaz."),
    Notice("Phosphor Icons (Light)", "MIT", "Iconos de la interfaz."),
    Notice("AndroidX, Jetpack Compose, Room, DataStore", "Apache 2.0", "Bibliotecas de Android."),
    Notice("Kotlin y kotlinx.coroutines", "Apache 2.0", "Lenguaje y concurrencia."),
    Notice("ML Kit Document Scanner", "Términos de Google ML Kit", "Escáner de documentos (servicios de Google Play)."),
    Notice("SheetJS Community Edition", "Apache 2.0", "Lectura de hojas de cálculo."),
    Notice("docx-preview", "Apache 2.0", "Vista de documentos de Word."),
    Notice("JSZip", "MIT o GPL v3", "Lectura de archivos comprimidos en el visor."),
    Notice("pptx-preview", "MIT", "Vista aproximada de presentaciones."),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val c = LectorTheme.colors
    Box(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = LectorSizes.maxContentWidth).fillMaxSize()) {
            InfoHeader(stringResource(R.string.licenses_title), onBack)
            LazyColumn(contentPadding = PaddingValues(horizontal = LectorSpacing.screenMargin, vertical = LectorSpacing.l)) {
                items(notices) { notice ->
                    Column(Modifier.fillMaxWidth().padding(vertical = LectorSpacing.m)) {
                        Text(notice.name, style = LectorTheme.type.item, color = c.text)
                        Text(notice.license, style = LectorTheme.type.meta, color = c.textMuted)
                        Text(notice.note, style = LectorTheme.type.body, color = c.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun LastErrorScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val c = LectorTheme.colors
    val error by viewModel.lastError.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = LectorSizes.maxContentWidth).fillMaxSize()) {
            InfoHeader(stringResource(R.string.last_error_title), onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = LectorSpacing.screenMargin)) {
                SelectionContainer {
                    Text(error.orEmpty(), style = LectorTheme.type.mono, color = c.text)
                }
            }
            if (error != null) {
                Box(Modifier.padding(LectorSpacing.screenMargin)) {
                    SecondaryButton(stringResource(R.string.last_error_clear), onClick = { viewModel.clearLastError(); onBack() })
                }
            }
        }
    }
}

@Composable
private fun InfoHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = LectorSpacing.s, vertical = LectorSpacing.s), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(R.drawable.ic_arrow_left, stringResource(R.string.viewer_back), onBack)
        Text(title, style = LectorTheme.type.title, color = LectorTheme.colors.text, modifier = Modifier.padding(start = LectorSpacing.xs))
    }
}
