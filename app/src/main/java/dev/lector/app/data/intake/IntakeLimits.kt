package dev.lector.app.data.intake

import dev.lector.app.domain.model.DocumentFamily
import dev.lector.app.domain.model.DocumentKind

/** Límites de ingesta (PLAN §4.2). */
object IntakeLimits {
    private const val MB = 1024L * 1024L

    const val MAX_PDF_BYTES = 500L * MB
    const val MAX_OFFICE_BYTES = 50L * MB
    const val MAX_TEXT_BYTES = 5L * MB
    const val FREE_SPACE_MARGIN = 50L * MB

    const val ZIP_MAX_ENTRIES = 5_000
    const val ZIP_MAX_TOTAL_BYTES = 300L * MB
    const val ZIP_MAX_ENTRY_BYTES = 100L * MB

    fun maxBytesFor(kind: DocumentKind): Long = when (kind.family) {
        DocumentFamily.Pdf -> MAX_PDF_BYTES
        DocumentFamily.Text -> MAX_TEXT_BYTES
        else -> MAX_OFFICE_BYTES
    }
}
