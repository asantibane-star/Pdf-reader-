package dev.lector.app.domain.repo

import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.domain.model.RecentDocument
import kotlinx.coroutines.flow.Flow

interface RecentsRepository {
    fun observe(): Flow<List<RecentDocument>>

    /** Upsert por `uri`: conserva la última página y el total si el documento ya estaba. */
    suspend fun record(ref: DocumentRef, now: Long = System.currentTimeMillis()): Outcome<RecentDocument>

    suspend fun updateProgress(id: Long, page: Int, pageCount: Int): Outcome<Unit>

    suspend fun remove(id: Long): Outcome<Unit>

    /** Deshacer un borrado: vuelve a insertar la fila con su id. */
    suspend fun restore(recent: RecentDocument): Outcome<Unit>

    suspend fun clear(): Outcome<Unit>

    suspend fun find(id: Long): RecentDocument?
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings): Outcome<Unit>
}

/** Escaneos a PDF: importación del resultado de ML Kit o de imágenes, guardado y pendientes huérfanos. */
interface ScanRepository {
    /** Copia YA el PDF temporal del escáner a `filesDir/pending_scan/` (el archivo de ML Kit es temporal). */
    suspend fun importScannerPdf(uri: String, pageCount: Int): Outcome<dev.lector.app.domain.model.PendingScan>

    /** Crea un PDF (un JPEG por página) a partir de imágenes. [onProgress] recibe (hechas, total). */
    suspend fun buildFromImages(sources: List<String>, onProgress: (Int, Int) -> Unit = { _, _ -> }): Outcome<dev.lector.app.domain.model.PendingScan>

    /** Guarda en `Documentos/Lector` por MediaStore y devuelve el `content://` del archivo. */
    suspend fun saveToDocuments(pending: dev.lector.app.domain.model.PendingScan, name: String): Outcome<String>

    /** Guarda en el `Uri` que eligió el usuario ("Guardar en otra ubicación"). */
    suspend fun saveTo(pending: dev.lector.app.domain.model.PendingScan, destination: String): Outcome<Unit>

    suspend fun discard(id: String)

    suspend fun pending(): List<dev.lector.app.domain.model.PendingScan>
}
