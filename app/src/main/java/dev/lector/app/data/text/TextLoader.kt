package dev.lector.app.data.text

import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Carga archivos de texto: detecta BOM/UTF-8 y, si no es UTF-8 válido, usa ISO-8859-1. */
class TextLoader(private val dispatchers: AppDispatchers) {
    suspend fun load(path: String): Outcome<List<String>> = withContext(dispatchers.io) {
        try {
            val bytes = File(path).readBytes()
            Outcome.Success(toLines(decode(bytes)))
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Outcome.Failure(DocError.AccessLost)
        } catch (_: OutOfMemoryError) {
            Outcome.Failure(DocError.OutOfMemory)
        }
    }

    companion object {
        /** Las líneas más largas se parten para no construir un único texto gigante. */
        const val MAX_LINE_CHARS = 2_000

        fun decode(bytes: ByteArray): String {
            val (offset, charset) = when {
                bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> 3 to Charsets.UTF_8
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> 2 to Charsets.UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> 2 to Charsets.UTF_16BE
                else -> 0 to Charsets.UTF_8
            }
            val buffer = ByteBuffer.wrap(bytes, offset, bytes.size - offset)
            return strict(charset, buffer) ?: String(bytes, Charsets.ISO_8859_1)
        }

        private fun strict(charset: Charset, buffer: ByteBuffer): String? = try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(buffer)
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

        fun toLines(text: String): List<String> {
            val result = ArrayList<String>()
            for (raw in text.split("\r\n", "\n", "\r")) {
                if (raw.length <= MAX_LINE_CHARS) {
                    result += raw
                } else {
                    raw.chunked(MAX_LINE_CHARS).forEach { result += it }
                }
            }
            if (result.size > 1 && result.last().isEmpty()) result.removeAt(result.lastIndex)
            return result
        }
    }
}
