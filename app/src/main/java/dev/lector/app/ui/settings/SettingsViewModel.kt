package dev.lector.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lector.app.di.AppServices
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.ui.services
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val services: AppServices) : ViewModel() {
    val settings: StateFlow<AppSettings> = services.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        viewModelScope.launch { _lastError.value = services.crashRecorder.last() }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { services.settings.update(transform) }
    }

    fun clearRecents() {
        viewModelScope.launch { services.recents.clear() }
    }

    fun clearLastError() {
        services.crashRecorder.clear()
        _lastError.value = null
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(services()) }
        }
    }
}
