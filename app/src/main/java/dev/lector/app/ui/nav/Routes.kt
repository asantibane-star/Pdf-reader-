package dev.lector.app.ui.nav

import dev.lector.app.domain.model.DocumentFamily
import dev.lector.app.domain.model.DocumentKind

/** Rutas tipadas de la app. Los argumentos viajan como `docId` (el documento vive en `DocumentRegistry`). */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val LAST_ERROR = "last_error"
    const val SCAN_SIMPLE = "scan_simple"

    const val ARG_DOC_ID = "docId"
    const val PDF = "pdf/{$ARG_DOC_ID}"
    const val WEB = "web/{$ARG_DOC_ID}"
    const val TEXT = "text/{$ARG_DOC_ID}"

    fun pdf(docId: String) = "pdf/$docId"
    fun web(docId: String) = "web/$docId"
    fun text(docId: String) = "text/$docId"

    /** Visor que atiende a cada tipo. */
    fun viewerFor(docId: String, kind: DocumentKind): String = when (kind.family) {
        DocumentFamily.Pdf -> pdf(docId)
        DocumentFamily.Text -> if (kind == DocumentKind.Csv) web(docId) else text(docId)
        DocumentFamily.Sheet, DocumentFamily.Presentation, DocumentFamily.Word -> web(docId)
    }
}
