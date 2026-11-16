package dev.lector.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.di.AppServices
import dev.lector.app.domain.model.DocumentKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface OpenState {
    data object Idle : OpenState

    /** Ingiriendo el archivo a la caché. */
    data object Opening : OpenState

    data class Failed(val error: DocError, val uri: String, val recentId: Long?) : OpenState
}

sealed interface MainEvent {
    /** [external] = el archivo llegó por un intent de otra app: "atrás" vuelve a esa app, no a Inicio. */
    data class ShowDocument(val docId: String, val kind: DocumentKind, val external: Boolean) : MainEvent
}

/**
 * Abre documentos desde cualquier origen (intent VIEW/SEND, selector, recientes) por el único camino
 * `UriIntake → visor`. Sobrevive a rotaciones: la ingesta no se cancela al girar.
 */
class MainViewModel(private val services: AppServices) : ViewModel() {
    private val _state = MutableStateFlow<OpenState>(OpenState.Idle)
    val state: StateFlow<OpenState> = _state.asStateFlow()

    private val events = Channel<MainEvent>(Channel.BUFFERED)
    val eventFlow: Flow<MainEvent> = events.receiveAsFlow()

    private var job: Job? = null

    /** Protegido contra el doble toque: si ya hay una apertura en curso se ignora. */
    fun open(uri: String, external: Boolean, recentId: Long? = null, replaces: Long? = null) {
        if (_state.value == OpenState.Opening) return
        _state.value = OpenState.Opening
        job = viewModelScope.launch {
            when (val result = services.uriIntake.copyToCache(uri)) {
                is Outcome.Success -> {
                    val ref = result.value
                    services.documents.register(ref)
                    // "Buscar archivo": la fila antigua (sin acceso) se sustituye por la nueva.
                    if (replaces != null) services.recents.remove(replaces)
                    _state.value = OpenState.Idle
                    events.send(MainEvent.ShowDocument(ref.id, ref.kind, external))
                }
                is Outcome.Failure -> _state.value = OpenState.Failed(result.error, uri, recentId)
            }
        }
    }

    fun cancelOpen() {
        job?.cancel()
        _state.value = OpenState.Idle
    }

    fun dismissError() {
        _state.value = OpenState.Idle
    }

    /** "Quitar de la lista" desde el diálogo de acceso perdido. */
    fun removeRecent(id: Long) {
        viewModelScope.launch { services.recents.remove(id) }
        _state.value = OpenState.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(services()) }
        }
    }
}
