package dev.lector.app.data.intake

import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Valida un contenedor ZIP (OOXML/ODF) contando los bytes REALES al descomprimir, sin fiarse de las
 * cabeceras (PLAN §4.2). Nunca extrae a disco.
 */
object ZipInspector {
    private const val BUFFER = 64 * 1024

    suspend fun validate(file: File): Outcome<Unit> {
        var entries = 0
        var total = 0L
        val buffer = ByteArray(BUFFER)
        try {
            ZipInputStream(BufferedInputStream(file.inputStream(), BUFFER)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > IntakeLimits.ZIP_MAX_ENTRIES) return Outcome.Failure(DocError.ZipBomb)
                    if (!isSafeName(entry.name)) return Outcome.Failure(DocError.Corrupt)
                    var entryBytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = zip.read(buffer)
                        if (n < 0) break
                        entryBytes += n
                        total += n
                        if (entryBytes > IntakeLimits.ZIP_MAX_ENTRY_BYTES || total > IntakeLimits.ZIP_MAX_TOTAL_BYTES) {
                            return Outcome.Failure(DocError.ZipBomb)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return Outcome.Failure(DocError.Corrupt)
        } catch (_: IllegalArgumentException) {
            return Outcome.Failure(DocError.Corrupt)
        }
        return if (entries == 0) Outcome.Failure(DocError.Corrupt) else Outcome.Success(Unit)
    }

    /** Rechaza rutas absolutas, con `\`, con unidad (`C:`) o con un segmento `..`. */
    internal fun isSafeName(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\\') || name.contains('\u0000')) return false
        if (name.length >= 2 && name[1] == ':' && name[0].isLetter()) return false
        return name.split('/').none { it == ".." }
    }
}
