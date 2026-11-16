package dev.lector.app.ui.viewer.web

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.data.intake.CacheCleaner
import dev.lector.app.data.office.JsMessage
import dev.lector.app.data.office.ViewerRequestInterceptor
import dev.lector.app.data.office.ViewerUrl
import dev.lector.app.di.AppServices
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.ui.services
import dev.lector.app.ui.viewer.DocumentResolver
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WebUiState {
    data object Loading : WebUiState

    /** Documento listo para el WebView. [nonce] de 128 bits válido solo mientras vive este ViewModel. */
    data class Ready(val kind: DocumentKind, val jsKind: String, val title: String, val nonce: String) : WebUiState

    /** Formato que el motor B no muestra: "No compatible en este dispositivo" + abrir con otra app. */
    data class Unsupported(val title: String) : WebUiState

    data class Error(val error: DocError) : WebUiState
}

data class RenderUi(
    val rendered: Boolean = false,
    val error: DocError? = null,
    val sheetCount: Int = 0,
    val activeSheet: Int = 0,
)

class WebViewerViewModel(
    private val savedState: SavedStateHandle,
    private val services: AppServices,
) : ViewModel() {
    private var ref: DocumentRef? = null
    private var nonce: String = ""

    private val _state = MutableStateFlow<WebUiState>(WebUiState.Loading)
    val state: StateFlow<WebUiState> = _state.asStateFlow()

    private val _render = MutableStateFlow(RenderUi())
    val render: StateFlow<RenderUi> = _render.asStateFlow()

    /** Se incrementa para recrear el WebView ("Reintentar" tras un fallo del proceso de render). */
    private val _reloadToken = MutableStateFlow(0)
    val reloadToken: StateFlow<Int> = _reloadToken.asStateFlow()

    val sourceUri: String? get() = ref?.sourceUri ?: savedState.get<String>(KEY_SOURCE_URI)
    val title: String get() = ref?.displayName ?: savedState.get<String>(KEY_NAME).orEmpty()
    val savedScrollY: Int get() = savedState.get<Int>(KEY_SCROLL) ?: 0

    init {
        load()
    }

    fun load() {
        _state.value = WebUiState.Loading
        _render.value = RenderUi()
        viewModelScope.launch {
            val docId = savedState.get<String>(KEY_DOC_ID).orEmpty()
            val resolved = DocumentResolver(services).resolve(docId, savedState.get<String>(KEY_SOURCE_URI))
            if (resolved is Outcome.Failure) {
                _state.value = WebUiState.Error(resolved.error)
                return@launch
            }
            val document = (resolved as Outcome.Success).value
            ref = document
            savedState[KEY_DOC_ID] = document.id
            savedState[KEY_SOURCE_URI] = document.sourceUri
            savedState[KEY_NAME] = document.displayName
            services.recents.record(document)

            val jsKind = ViewerUrl.jsKind(document.kind)
            if (jsKind == null) {
                _state.value = WebUiState.Unsupported(document.displayName)
                return@launch
            }
            nonce = newNonce()
            _state.value = WebUiState.Ready(document.kind, jsKind, document.displayName, nonce)
            // Red de seguridad: si el visor JS no responde, se informa en lugar de dejar el esqueleto para siempre.
            delay(NATIVE_TIMEOUT_MS)
            _render.update { if (it.rendered || it.error != null) it else it.copy(error = DocError.RenderTimeout) }
        }
    }

    /** Respuesta de `/doc/<nonce>`: el archivo solo si el nonce coincide con el de esta sesión. */
    fun documentFor(candidate: String): File? {
        val document = ref ?: return null
        if (nonce.isEmpty() || !ViewerRequestInterceptor.nonceMatches(nonce, candidate)) return null
        return File(document.cachePath)
    }

    fun onMessage(message: JsMessage) {
        when (message) {
            JsMessage.Ready -> Unit
            JsMessage.Rendered -> _render.update { it.copy(rendered = true) }
            is JsMessage.Failed -> _render.update { if (it.rendered) it else it.copy(error = message.error) }
            is JsMessage.Sheets -> _render.update { it.copy(sheetCount = message.count) }
        }
    }

    fun onRenderProcessGone() {
        _render.value = RenderUi(error = DocError.RendererCrashed)
    }

    fun onWebViewUnavailable() {
        _render.value = RenderUi(error = DocError.WebViewUnavailable)
    }

    fun selectSheet(index: Int) = _render.update { it.copy(activeSheet = index) }

    fun onScroll(y: Int) {
        savedState[KEY_SCROLL] = y
    }

    /** "Reintentar": nuevo nonce y WebView nuevo. */
    fun retry() {
        _render.value = RenderUi()
        _reloadToken.update { it + 1 }
        val current = _state.value
        if (current is WebUiState.Ready) {
            nonce = newNonce()
            _state.value = current.copy(nonce = nonce)
        } else {
            load()
        }
    }

    /** Nombre sugerido para "Guardar copia": el original, con su extensión. */
    fun suggestedFileName(): String {
        val kind = ref?.kind ?: return title
        return "${title.ifBlank { "Documento" }}.${kind.extension}"
    }

    /** Compartir entrega el archivo ORIGINAL (PLAN §7.4). */
    fun share(context: android.content.Context) {
        val document = ref ?: return
        if (!_busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val shared = services.exporter.prepareShare(document.cachePath, document.displayName, document.kind.extension)
                if (shared is Outcome.Success) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = document.kind.mimeType
                        putExtra(android.content.Intent.EXTRA_STREAM, shared.value)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (_: android.content.ActivityNotFoundException) {
                // Sin app compatible: no hay nada más que hacer.
            } finally {
                _busy.value = false
            }
        }
    }

    fun saveCopy(destination: android.net.Uri) {
        val document = ref ?: return
        if (!_busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                services.exporter.copyTo(document.cachePath, destination)
            } finally {
                _busy.value = false
            }
        }
    }

    private val _busy = MutableStateFlow(false)

    override fun onCleared() {
        val document = ref
        if (document != null) {
            services.appScope.launch {
                CacheCleaner.deleteOpenFile(services.cacheDir, document.cachePath)
                services.documents.remove(document.id)
            }
        }
    }

    private fun newNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KEY_DOC_ID = "docId"
        const val KEY_SOURCE_URI = "sourceUri"
        const val KEY_NAME = "displayName"
        const val KEY_SCROLL = "scrollY"
        private const val NONCE_BYTES = 16
        private const val NATIVE_TIMEOUT_MS = 45_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { WebViewerViewModel(createSavedStateHandle(), services()) }
        }
    }
}
