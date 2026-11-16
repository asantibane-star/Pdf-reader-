package dev.lector.app.data.scan

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Guarda un PDF en `Documentos/Lector` con MediaStore (PLAN §8.2), sin pedir ningún permiso:
 * `insert(IS_PENDING=1)` → copiar bytes → `update(IS_PENDING=0)`. Si algo falla, borra la fila creada.
 */
class MediaStoreSaver(private val resolver: ContentResolver, private val dispatchers: AppDispatchers) {

    suspend fun save(source: File, displayName: String): Outcome<Uri> = withContext(dispatchers.io) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        var created: Uri? = null
        try {
            val uri = resolver.insert(collection, values) ?: return@withContext Outcome.Failure(DocError.SaveFailed)
            created = uri
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("Sin flujo de salida")
            out.use { output -> source.inputStream().use { it.copyTo(output) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, done, null, null) < 1) throw IOException("No se pudo publicar el archivo")
            Outcome.Success(uri)
        } catch (e: CancellationException) {
            created?.let { withContext(NonCancellable) { deleteQuietly(it) } }
            throw e
        } catch (_: IOException) {
            created?.let(::deleteQuietly)
            Outcome.Failure(DocError.SaveFailed)
        } catch (_: SecurityException) {
            created?.let(::deleteQuietly)
            Outcome.Failure(DocError.SaveFailed)
        } catch (_: IllegalStateException) {
            created?.let(::deleteQuietly)
            Outcome.Failure(DocError.SaveFailed)
        } catch (_: IllegalArgumentException) {
            created?.let(::deleteQuietly)
            Outcome.Failure(DocError.SaveFailed)
        }
    }

    private fun deleteQuietly(uri: Uri) {
        try {
            resolver.delete(uri, null, null)
        } catch (_: RuntimeException) {
            // Mejor esfuerzo: la fila pendiente se limpia sola más tarde.
        }
    }

    companion object {
        const val RELATIVE_PATH = "Documents/Lector"
    }
}
