package dev.lector.app.data.scan

import android.content.Context
import androidx.core.net.toUri
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.PendingScan
import dev.lector.app.domain.repo.ScanRepository
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Escaneos pendientes en `filesDir/pending_scan/<uuid>.pdf` (+ `<uuid>.pages` con el número de páginas). */
class FileScanRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val images: ImageImport,
    private val saver: MediaStoreSaver,
) : ScanRepository {
    private val dir: File get() = File(context.filesDir, PENDING_DIR)

    override suspend fun importScannerPdf(uri: String, pageCount: Int): Outcome<PendingScan> = withContext(dispatchers.io) {
        val (id, file) = newPending()
        var keep = false
        try {
            val input = context.contentResolver.openInputStream(uri.toUri()) ?: return@withContext Outcome.Failure(DocError.ScannerUnavailable)
            input.use { source -> file.outputStream().use { source.copyTo(it) } }
            if (file.length() == 0L) return@withContext Outcome.Failure(DocError.ScannerUnavailable)
            writePageCount(id, pageCount)
            keep = true
            Outcome.Success(PendingScan(id, file.absolutePath, pageCount))
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Outcome.Failure(DocError.ScannerUnavailable)
        } catch (_: SecurityException) {
            Outcome.Failure(DocError.ScannerUnavailable)
        } finally {
            if (!keep) discardFiles(id)
        }
    }

    override suspend fun buildFromImages(sources: List<String>, onProgress: (Int, Int) -> Unit): Outcome<PendingScan> =
        withContext(dispatchers.io) {
            if (sources.isEmpty()) return@withContext Outcome.Failure(DocError.Corrupt)
            val (id, file) = newPending()
            var keep = false
            try {
                JpegPdfWriter(BufferedOutputStream(file.outputStream())).use { writer ->
                    sources.forEachIndexed { index, source ->
                        currentCoroutineContext().ensureActive()
                        when (val jpeg = images.toJpeg(source)) {
                            is Outcome.Success -> writer.addPage(jpeg.value)
                            is Outcome.Failure -> return@withContext jpeg
                        }
                        onProgress(index + 1, sources.size)
                    }
                    writer.finish()
                }
                writePageCount(id, sources.size)
                keep = true
                Outcome.Success(PendingScan(id, file.absolutePath, sources.size))
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                Outcome.Failure(DocError.NoSpace)
            } catch (_: IllegalArgumentException) {
                Outcome.Failure(DocError.Corrupt)
            } finally {
                if (!keep) discardFiles(id)
            }
        }

    override suspend fun saveToDocuments(pending: PendingScan, name: String): Outcome<String> {
        val source = File(pending.path)
        if (!source.isFile) return Outcome.Failure(DocError.SaveFailed)
        return when (val saved = saver.save(source, name)) {
            is Outcome.Success -> Outcome.Success(saved.value.toString())
            is Outcome.Failure -> saved
        }
    }

    override suspend fun saveTo(pending: PendingScan, destination: String): Outcome<Unit> = withContext(dispatchers.io) {
        try {
            val out = context.contentResolver.openOutputStream(destination.toUri(), "wt") ?: return@withContext Outcome.Failure(DocError.SaveFailed)
            out.use { output -> File(pending.path).inputStream().use { it.copyTo(output) } }
            Outcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Outcome.Failure(DocError.SaveFailed)
        } catch (_: SecurityException) {
            Outcome.Failure(DocError.SaveFailed)
        }
    }

    override suspend fun discard(id: String) = withContext(dispatchers.io) { discardFiles(id) }

    override suspend fun pending(): List<PendingScan> = withContext(dispatchers.io) {
        dir.listFiles { f -> f.extension == "pdf" && f.length() > 0 }.orEmpty().sortedBy { it.lastModified() }.map { file ->
            val id = file.nameWithoutExtension
            PendingScan(id, file.absolutePath, readPageCount(id))
        }
    }

    private fun newPending(): Pair<String, File> {
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        return id to File(dir, "$id.pdf")
    }

    private fun discardFiles(id: String) {
        File(dir, "$id.pdf").delete()
        File(dir, "$id.pages").delete()
    }

    private fun writePageCount(id: String, count: Int) {
        File(dir, "$id.pages").writeText(count.toString())
    }

    private fun readPageCount(id: String): Int = try {
        File(dir, "$id.pages").readText().trim().toInt()
    } catch (_: IOException) {
        0
    } catch (_: NumberFormatException) {
        0
    }

    companion object {
        const val PENDING_DIR = "pending_scan"
    }
}
