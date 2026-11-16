package dev.lector.app.data.intake

import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.domain.model.DocumentRef
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Único camino de entrada de archivos (PLAN §3.3):
 * `Uri` → copia en `cacheDir/open/<uuid>.bin` → `MagicSniffer` → `ZipInspector` → [DocumentRef].
 */
class UriIntake(
    private val source: UriSource,
    private val cacheDir: File,
    private val dispatchers: AppDispatchers,
    /** Se espera antes de tocar la caché: garantiza que la limpieza de arranque ya terminó. */
    private val awaitCacheReady: suspend () -> Unit = {},
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {
    suspend fun copyToCache(uri: String): Outcome<DocumentRef> = withContext(dispatchers.io) {
        if (!uri.startsWith("content://", ignoreCase = true)) {
            return@withContext Outcome.Failure(DocError.UnsupportedSource)
        }
        awaitCacheReady()

        val declared = source.declaredSize(uri)
        if (declared != null && declared > IntakeLimits.MAX_PDF_BYTES) {
            return@withContext Outcome.Failure(DocError.TooLarge)
        }
        if (freeSpace(cacheDir) < (declared ?: 0L) + IntakeLimits.FREE_SPACE_MARGIN) {
            return@withContext Outcome.Failure(DocError.NoSpace)
        }

        val openDir = File(cacheDir, OPEN_DIR)
        if (!openDir.isDirectory && !openDir.mkdirs() && !openDir.isDirectory) {
            return@withContext Outcome.Failure(DocError.NoSpace)
        }
        val id = UUID.randomUUID().toString()
        val target = File(openDir, "$id.bin")
        var keep = false
        try {
            val copied = copyLimited(uri, target)
            if (copied is Outcome.Failure) return@withContext copied

            val displayName = source.displayName(uri).orEmpty()
            val kind = when (val sniffed = MagicSniffer.detect(target, displayName)) {
                is Outcome.Failure -> return@withContext sniffed
                is Outcome.Success -> sniffed.value
            }
            val size = target.length()
            if (size > IntakeLimits.maxBytesFor(kind)) return@withContext Outcome.Failure(DocError.TooLarge)
            if (isZipBased(kind)) {
                val zip = ZipInspector.validate(target)
                if (zip is Outcome.Failure) return@withContext zip
            }
            keep = true
            Outcome.Success(
                DocumentRef(
                    id = id,
                    cachePath = target.absolutePath,
                    kind = kind,
                    displayName = FileNameSanitizer.displayTitle(displayName),
                    sourceUri = uri,
                    sizeBytes = size,
                ),
            )
        } finally {
            // Cancelación o fallo: nunca se deja un parcial en caché.
            if (!keep) target.delete()
        }
    }

    /** Copia contando los bytes mientras se escriben; aborta al superar el máximo. */
    private suspend fun copyLimited(uri: String, target: File): Outcome<Unit> {
        try {
            source.open(uri).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > IntakeLimits.MAX_PDF_BYTES) return Outcome.Failure(DocError.TooLarge)
                        output.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            return Outcome.Failure(DocError.AccessLost)
        } catch (_: FileNotFoundException) {
            return Outcome.Failure(DocError.AccessLost)
        } catch (_: IOException) {
            val low = freeSpace(cacheDir) < LOW_SPACE_BYTES
            return Outcome.Failure(if (low) DocError.NoSpace else DocError.AccessLost)
        } catch (_: IllegalStateException) {
            return Outcome.Failure(DocError.AccessLost)
        }
        return Outcome.Success(Unit)
    }

    private fun isZipBased(kind: DocumentKind): Boolean = when (kind) {
        DocumentKind.Docx, DocumentKind.Xlsx, DocumentKind.Pptx,
        DocumentKind.Odt, DocumentKind.Ods, DocumentKind.Odp,
        -> true
        else -> false
    }

    companion object {
        const val OPEN_DIR = "open"
        private const val BUFFER = 64 * 1024
        private const val LOW_SPACE_BYTES = 1024L * 1024L
    }
}
