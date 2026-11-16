package dev.lector.app.data.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.PageSize
import dev.lector.app.domain.model.SearchHit
import java.io.File
import kotlinx.coroutines.flow.Flow

interface PdfEngine {
    /** `true` si el motor puede abrir PDF con contraseña en esta versión de Android (API ≥ 35). */
    val supportsPassword: Boolean

    suspend fun open(file: File, password: String? = null): Outcome<PdfSession>
}

interface PdfSession {
    val pageCount: Int
    val canSearch: Boolean

    /** Tamaño de la página en puntos PDF. */
    suspend fun pageSize(index: Int): Outcome<PageSize>

    /**
     * Renderiza la página al tamaño total [widthPx]×[heightPx]. Con [region] (fracción [0..1] de la página)
     * el bitmap devuelto contiene solo esa región, a la misma densidad.
     */
    suspend fun render(index: Int, widthPx: Int, heightPx: Int, region: RectF? = null): Outcome<Bitmap>

    /** Un [SearchHit] por coincidencia, en orden de página. Vacío si `!canSearch`. */
    fun search(query: String): Flow<SearchHit>

    suspend fun close()
}
