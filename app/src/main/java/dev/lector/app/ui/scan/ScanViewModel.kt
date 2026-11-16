package dev.lector.app.ui.scan

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lector.app.R
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.data.intake.FileNameSanitizer
import dev.lector.app.di.AppServices
import dev.lector.app.domain.model.PendingScan
import dev.lector.app.ui.services
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ScanState {
    data object Idle : ScanState

    /** Copiando el resultado del escáner o creando el PDF de las imágenes. */
    data class Working(@StringRes val label: Int, val done: Int = 0, val total: Int = 0) : ScanState

    /** Hoja "Nombrar": miniatura de la primera página, número de páginas y campo de nombre. */
    data class Naming(val pending: PendingScan, val thumbnail: Bitmap?, val suggestedName: String) : ScanState

    data class Saving(val pending: PendingScan) : ScanState

    /** El guardado falló: se conserva el pendiente y se ofrece Reintentar / Guardar en otra ubicación. */
    data class SaveFailed(val pending: PendingScan, val error: DocError) : ScanState

    /** Quedó un escaneo sin guardar de una sesión anterior. */
    data class OrphanPrompt(val pending: PendingScan) : ScanState
}

sealed interface ScanEvent {
    /** Guardado: se avisa, se abre el visor y se ofrece compartir. */
    data class Saved(val uri: String, val name: String) : ScanEvent
    data class Snack(@StringRes val messageRes: Int) : ScanEvent
}

/**
 * Orquesta el flujo común del escáner (ML Kit o modo simple): importar → nombrar → guardar en `Documentos/Lector`.
 * El `uuid` del pendiente vive en `SavedStateHandle`: si el proceso muere con el escáner abierto, el flujo continúa.
 */
class ScanViewModel(
    private val savedState: SavedStateHandle,
    private val services: AppServices,
) : ViewModel() {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val events = Channel<ScanEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ScanEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch { restore() }
    }

    private suspend fun restore() {
        val pending = services.scans.pending()
        val savedId = savedState.get<String>(KEY_PENDING_ID)
        val active = pending.firstOrNull { it.id == savedId }
        _state.value = when {
            active != null -> naming(active)
            pending.isNotEmpty() -> ScanState.OrphanPrompt(pending.first())
            else -> ScanState.Idle
        }
    }

    /** Resultado `RESULT_OK` de ML Kit: se copia YA el PDF temporal. */
    fun onScannerResult(uri: String, pageCount: Int) {
        if (_state.value is ScanState.Working) return
        _state.value = ScanState.Working(R.string.scan_working)
        viewModelScope.launch {
            when (val imported = services.scans.importScannerPdf(uri, pageCount)) {
                is Outcome.Success -> _state.value = naming(imported.value)
                is Outcome.Failure -> fail(imported.error)
            }
        }
    }

    /** Modo simple: crea el PDF a partir de las imágenes elegidas. */
    fun createFromImages(sources: List<String>) {
        if (sources.isEmpty() || _state.value is ScanState.Working) return
        _state.value = ScanState.Working(R.string.scan_working, 0, sources.size)
        viewModelScope.launch {
            val built = services.scans.buildFromImages(sources) { done, total ->
                _state.value = ScanState.Working(R.string.scan_working, done, total)
            }
            when (built) {
                is Outcome.Success -> _state.value = naming(built.value)
                is Outcome.Failure -> fail(built.error)
            }
        }
    }

    fun save(rawName: String) {
        val current = _state.value
        val pending = when (current) {
            is ScanState.Naming -> current.pending
            is ScanState.SaveFailed -> current.pending
            else -> return
        }
        val name = FileNameSanitizer.sanitize(rawName)
        _state.value = ScanState.Saving(pending)
        viewModelScope.launch {
            when (val saved = services.scans.saveToDocuments(pending, name)) {
                is Outcome.Success -> finish(pending, saved.value, name)
                is Outcome.Failure -> _state.value = ScanState.SaveFailed(pending, saved.error)
            }
        }
    }

    /** "Guardar en otra ubicación…" tras un fallo: el usuario eligió un `Uri` con `ACTION_CREATE_DOCUMENT`. */
    fun saveElsewhere(destination: String, name: String) {
        val current = _state.value as? ScanState.SaveFailed ?: return
        val pending = current.pending
        _state.value = ScanState.Saving(pending)
        viewModelScope.launch {
            when (services.scans.saveTo(pending, destination)) {
                is Outcome.Success -> finish(pending, destination, name)
                is Outcome.Failure -> _state.value = ScanState.SaveFailed(pending, DocError.SaveFailed)
            }
        }
    }

    fun discard() {
        val pending = when (val current = _state.value) {
            is ScanState.Naming -> current.pending
            is ScanState.SaveFailed -> current.pending
            is ScanState.OrphanPrompt -> current.pending
            else -> return
        }
        _state.value = ScanState.Idle
        savedState.remove<String>(KEY_PENDING_ID)
        viewModelScope.launch { services.scans.discard(pending.id) }
    }

    /** El pendiente huérfano se acepta: pasa a la hoja "Nombrar". */
    fun acceptOrphan() {
        val prompt = _state.value as? ScanState.OrphanPrompt ?: return
        viewModelScope.launch { _state.value = naming(prompt.pending) }
    }

    fun dismissError() {
        if (_state.value is ScanState.Working) _state.value = ScanState.Idle
    }

    private suspend fun finish(pending: PendingScan, uri: String, name: String) {
        services.scans.discard(pending.id)
        savedState.remove<String>(KEY_PENDING_ID)
        _state.value = ScanState.Idle
        events.send(ScanEvent.Saved(uri, name))
    }

    private fun fail(error: DocError) {
        _state.value = ScanState.Idle
        val message = when (error) {
            DocError.NoSpace -> R.string.err_no_space
            DocError.Corrupt, DocError.AccessLost -> R.string.scan_failed_page
            else -> R.string.err_scanner_save
        }
        events.trySend(ScanEvent.Snack(message))
    }

    private suspend fun naming(pending: PendingScan): ScanState.Naming {
        savedState[KEY_PENDING_ID] = pending.id
        return ScanState.Naming(pending, firstPageThumbnail(pending), FileNameSanitizer.defaultScanName(java.time.LocalDateTime.now()))
    }

    /** Miniatura de la primera página con el mismo motor del visor; null si no se puede abrir. */
    private suspend fun firstPageThumbnail(pending: PendingScan): Bitmap? {
        val opened = services.pdfEngine.open(File(pending.path))
        val session = (opened as? Outcome.Success)?.value ?: return null
        try {
            val size = (session.pageSize(0) as? Outcome.Success)?.value ?: return null
            val ratio = size.aspectRatio
            val h = THUMB_HEIGHT_PX
            return (session.render(0, (h * ratio).roundToInt().coerceAtLeast(1), h) as? Outcome.Success)?.value
        } finally {
            session.close()
        }
    }

    companion object {
        const val KEY_PENDING_ID = "pendingScanId"
        private const val THUMB_HEIGHT_PX = 264

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ScanViewModel(createSavedStateHandle(), services()) }
        }
    }
}
