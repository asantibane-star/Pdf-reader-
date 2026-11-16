package dev.lector.app.ui.viewer.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class WebViewerViewModel : ViewModel() {
    val sourceUri: String? = null

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory { initializer { WebViewerViewModel() } }
    }
}
