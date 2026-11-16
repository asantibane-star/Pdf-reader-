package dev.lector.app.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.data.intake.FileNameSanitizer
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

/**
 * Compartir y guardar copia. Compartir copia a `cache/share/` (único directorio expuesto por el
 * `FileProvider`, junto con `cache/camera/`); guardar copia escribe en el `Uri` que devolvió el selector.
 */
interface DocumentExporter {
    /** Copia el documento a `cache/share/` y devuelve su `content://` del FileProvider. */
    suspend fun prepareShare(sourcePath: String, displayName: String, extension: String): Outcome<Uri>

    /** Escribe el documento en el `Uri` que eligió el usuario (guardar copia). */
    suspend fun copyTo(sourcePath: String, destination: Uri): Outcome<Unit>
}

class FileProviderExporter(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : DocumentExporter {
    override suspend fun prepareShare(sourcePath: String, displayName: String, extension: String): Outcome<Uri> =
        withContext(dispatchers.io) {
            try {
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                val safe = FileNameSanitizer.sanitize(displayName).ifBlank { "Documento" }
                val target = File(dir, "$safe.$extension")
                File(sourcePath).inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                Outcome.Success(FileProvider.getUriForFile(context, authority(), target))
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                Outcome.Failure(DocError.SaveFailed)
            } catch (_: IllegalArgumentException) {
                Outcome.Failure(DocError.SaveFailed)
            }
        }

    override suspend fun copyTo(sourcePath: String, destination: Uri): Outcome<Unit> = withContext(dispatchers.io) {
        try {
            val out = context.contentResolver.openOutputStream(destination, "wt") ?: return@withContext Outcome.Failure(DocError.SaveFailed)
            out.use { output -> File(sourcePath).inputStream().use { it.copyTo(output) } }
            Outcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Outcome.Failure(DocError.SaveFailed)
        } catch (_: SecurityException) {
            Outcome.Failure(DocError.SaveFailed)
        }
    }

    private fun authority() = "${context.packageName}.fileprovider"

    companion object {
        const val SHARE_DIR = "share"
    }
}
