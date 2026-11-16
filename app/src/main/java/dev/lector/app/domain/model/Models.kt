package dev.lector.app.domain.model

/** Documento ya ingerido: SIEMPRE se trabaja sobre la copia en caché, nunca sobre el Uri original. */
data class DocumentRef(
    val id: String,
    val cachePath: String,
    val kind: DocumentKind,
    val displayName: String,
    val sourceUri: String,
    val sizeBytes: Long,
)

/** Tamaño de página en puntos PDF (1/72 de pulgada). */
data class PageSize(val width: Float, val height: Float) {
    val aspectRatio: Float get() = if (width > 0f && height > 0f) width / height else DEFAULT_ASPECT

    companion object {
        const val DEFAULT_ASPECT = 595f / 842f
        val A4 = PageSize(595f, 842f)
    }
}

/** Coincidencia de búsqueda en una página. Los rectángulos son fracciones [0..1] de la página. */
data class SearchHit(val page: Int, val bounds: List<FractionRect>)

data class FractionRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class RecentDocument(
    val id: Long,
    val uri: String,
    val displayName: String,
    val kind: DocumentKind,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
    val lastPage: Int,
    val pageCount: Int,
)

/** PDF escaneado pendiente de guardar (vive en `filesDir/pending_scan/`). */
data class PendingScan(val id: String, val path: String, val pageCount: Int)
