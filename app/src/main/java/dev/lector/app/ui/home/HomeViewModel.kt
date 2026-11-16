package dev.lector.app.ui.home

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lector.app.di.AppServices
import dev.lector.app.domain.model.RecentDocument
import dev.lector.app.ui.services
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeState(val loading: Boolean = true, val recents: List<RecentDocument> = emptyList())

class HomeViewModel(private val services: AppServices) : ViewModel() {
    val state: StateFlow<HomeState> = services.recents.observe()
        .map { HomeState(loading = false, recents = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState())

    suspend fun thumbnail(id: Long): Bitmap? = services.thumbnails.load(id)

    fun remove(recent: RecentDocument) {
        viewModelScope.launch { services.recents.remove(recent.id) }
    }

    fun undoRemove(recent: RecentDocument) {
        viewModelScope.launch { services.recents.restore(recent) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(services()) }
        }
    }
}
