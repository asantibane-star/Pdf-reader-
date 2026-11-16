package dev.lector.app.ui.viewer.web

import androidx.compose.runtime.Composable

@Composable
fun WebViewerScreen(viewModel: WebViewerViewModel, onBack: () -> Unit, onOpenWithOther: () -> Unit) {
    // Se implementa en la Fase 6.
    onBack()
    onOpenWithOther()
    viewModel.toString()
}
