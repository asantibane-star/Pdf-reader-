package dev.lector.app.ui.viewer.text

import androidx.compose.runtime.Immutable
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
import dev.lector.app.data.text.TextLoader
import dev.lector.app.di.AppServices
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.ui.services
import dev.lector.app.ui.viewer.DocumentResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TextUiState {
    data object Loading : TextUiState

    @Immutable
    data class Content(val title: String, val lines: List<String>) : TextUiState

    data class Error(val error: DocError) : TextUiState
}

class TextViewerViewModel(
    private val savedState: SavedStateHandle,
    private val services: AppServices,
) : ViewModel() {
    private var ref: DocumentRef? = null
    private val _state = MutableStateFlow<TextUiState>(TextUiState.Loading)
    val state: StateFlow<TextUiState> = _state.asStateFlow()

    /** Índice del tamaño de texto (guardado para sobrevivir a rotaciones y a la muerte del proceso). */
    private val _sizeStep = MutableStateFlow(savedState.get<Int>(KEY_SIZE) ?: DEFAULT_STEP)
    val sizeStep: StateFlow<Int> = _sizeStep.asStateFlow()

    val title: String get() = ref?.displayName ?: savedState.get<String>(KEY_NAME).orEmpty()
    val sourceUri: String? get() = ref?.sourceUri ?: savedState.get<String>(KEY_SOURCE_URI)

    init {
        load()
    }

    fun load() {
        _state.value = TextUiState.Loading
        viewModelScope.launch {
            val docId = savedState.get<String>(KEY_DOC_ID).orEmpty()
            val resolved = DocumentResolver(services).resolve(docId, savedState.get<String>(KEY_SOURCE_URI))
            if (resolved is Outcome.Failure) {
                _state.value = TextUiState.Error(resolved.error)
                return@launch
            }
            val document = (resolved as Outcome.Success).value
            ref = document
            savedState[KEY_DOC_ID] = document.id
            savedState[KEY_SOURCE_URI] = document.sourceUri
            savedState[KEY_NAME] = document.displayName
            services.recents.record(document)
            _state.value = when (val loaded = TextLoader(services.dispatchers).load(document.cachePath)) {
                is Outcome.Success -> TextUiState.Content(document.displayName, loaded.value)
                is Outcome.Failure -> TextUiState.Error(loaded.error)
            }
        }
    }

    fun larger() = setStep(_sizeStep.value + 1)

    fun smaller() = setStep(_sizeStep.value - 1)

    private fun setStep(step: Int) {
        val clamped = step.coerceIn(0, SIZES_SP.lastIndex)
        _sizeStep.value = clamped
        savedState[KEY_SIZE] = clamped
    }

    override fun onCleared() {
        val document = ref
        if (document != null) {
            services.appScope.launch {
                CacheCleaner.deleteOpenFile(services.cacheDir, document.cachePath)
                services.documents.remove(document.id)
            }
        }
    }

    companion object {
        const val KEY_DOC_ID = "docId"
        const val KEY_SOURCE_URI = "sourceUri"
        const val KEY_NAME = "displayName"
        const val KEY_SIZE = "textSize"
        const val DEFAULT_STEP = 1

        /** Tamaños de lectura en sp; el paso por defecto es 16 sp (DESIGN §6.5). */
        val SIZES_SP = listOf(14, 16, 18, 20, 24)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TextViewerViewModel(createSavedStateHandle(), services()) }
        }
    }
}
