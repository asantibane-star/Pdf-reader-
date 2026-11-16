package dev.lector.app.ui.viewer

import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.di.AppServices
import dev.lector.app.domain.model.DocumentRef
import java.io.File

/**
 * Obtiene la copia en caché de un documento para un visor. Si el proceso murió (el registro en memoria
 * está vacío o la caché se limpió) la reingesta desde `sourceUri`; si falla, `AccessLost` (PLAN §5.4).
 */
class DocumentResolver(private val services: AppServices) {
    suspend fun resolve(docId: String, savedSourceUri: String?): Outcome<DocumentRef> {
        services.documents.get(docId)?.takeIf { File(it.cachePath).isFile }?.let { return Outcome.Success(it) }
        val uri = savedSourceUri ?: services.documents.get(docId)?.sourceUri ?: return Outcome.Failure(DocError.AccessLost)
        val ingested = services.uriIntake.copyToCache(uri)
        if (ingested is Outcome.Success) services.documents.register(ingested.value)
        return ingested
    }
}
