package dev.lector.app.data.intake

import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.DocumentKind
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Detecta el tipo de documento por sus bytes, no por extensión ni MIME (PLAN §4.2). */
object MagicSniffer {
    private const val PDF_SCAN_BYTES = 1024
    private const val TEXT_SAMPLE_BYTES = 64 * 1024
    private const val ODF_MIME_MAX_BYTES = 128
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val RTF_MAGIC = "{\\rtf".toByteArray(Charsets.US_ASCII)

    /** [nameHint] solo desempata entre texto plano y CSV. */
    fun detect(file: File, nameHint: String? = null): Outcome<DocumentKind> {
        val length = file.length()
        if (length <= 0L) return Outcome.Failure(DocError.Corrupt)
        val head = try {
            file.inputStream().use { input ->
                val buffer = ByteArray(minOf(length, TEXT_SAMPLE_BYTES.toLong()).toInt())
                var read = 0
                while (read < buffer.size) {
                    val n = input.read(buffer, read, buffer.size - read)
                    if (n < 0) break
                    read += n
                }
                buffer.copyOf(read)
            }
        } catch (_: IOException) {
            return Outcome.Failure(DocError.AccessLost)
        }

        if (indexOf(head, PDF_MAGIC, PDF_SCAN_BYTES) >= 0) return Outcome.Success(DocumentKind.Pdf)
        if (startsWith(head, ZIP_MAGIC)) return sniffZip(file)
        if (OleDirectory.hasSignature(head)) return sniffOle(file)
        if (startsWith(head, RTF_MAGIC)) return Outcome.Success(DocumentKind.Rtf)
        if (looksLikeText(head)) {
            val ext = nameHint?.substringAfterLast('.', "")?.lowercase().orEmpty()
            val kind = if (ext == "csv" || ext == "tsv") DocumentKind.Csv else DocumentKind.Text
            return Outcome.Success(kind)
        }
        return Outcome.Failure(DocError.UnsupportedFormat)
    }

    private fun sniffZip(file: File): Outcome<DocumentKind> {
        val names = HashSet<String>()
        var odfMime = ""
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    if (names.size >= IntakeLimits.ZIP_MAX_ENTRIES) return Outcome.Failure(DocError.ZipBomb)
                    val entry = entries.nextElement()
                    names += entry.name
                    if (entry.name == "mimetype") {
                        odfMime = zip.getInputStream(entry).use { input ->
                            val buffer = ByteArray(ODF_MIME_MAX_BYTES)
                            val n = input.read(buffer).coerceAtLeast(0)
                            String(buffer, 0, n, Charsets.US_ASCII).trim()
                        }
                    }
                }
            }
        } catch (_: ZipException) {
            return Outcome.Failure(DocError.Corrupt)
        } catch (_: IOException) {
            return Outcome.Failure(DocError.Corrupt)
        }

        val matches = buildList {
            if ("word/document.xml" in names) add(DocumentKind.Docx)
            if ("xl/workbook.xml" in names) add(DocumentKind.Xlsx)
            if ("ppt/presentation.xml" in names) add(DocumentKind.Pptx)
            when (odfMime) {
                "application/vnd.oasis.opendocument.text" -> add(DocumentKind.Odt)
                "application/vnd.oasis.opendocument.spreadsheet" -> add(DocumentKind.Ods)
                "application/vnd.oasis.opendocument.presentation" -> add(DocumentKind.Odp)
            }
        }
        return if (matches.size == 1) Outcome.Success(matches.first()) else Outcome.Failure(DocError.UnsupportedFormat)
    }

    private fun sniffOle(file: File): Outcome<DocumentKind> {
        val names = OleDirectory.streamNames(file) ?: return Outcome.Failure(DocError.Corrupt)
        val kind = when {
            "WordDocument" in names -> DocumentKind.Doc
            "PowerPoint Document" in names -> DocumentKind.Ppt
            "Workbook" in names || "Book" in names -> DocumentKind.Xls
            else -> null
        }
        return if (kind != null) Outcome.Success(kind) else Outcome.Failure(DocError.UnsupportedFormat)
    }

    /** Texto si no hay bytes NUL y casi no hay caracteres de control (UTF-8 o Latin-1 decodifican cualquier byte válido). */
    internal fun looksLikeText(sample: ByteArray): Boolean {
        if (sample.isEmpty()) return false
        var control = 0
        for (b in sample) {
            val v = b.toInt() and 0xFF
            if (v == 0) return false
            if (v < 0x20 && v != 0x09 && v != 0x0A && v != 0x0C && v != 0x0D && v != 0x1B) control++
            if (v == 0x7F) control++
        }
        return control * 100 <= sample.size
    }

    private fun startsWith(data: ByteArray, magic: ByteArray): Boolean =
        data.size >= magic.size && magic.indices.all { data[it] == magic[it] }

    private fun indexOf(data: ByteArray, needle: ByteArray, limit: Int): Int {
        val end = minOf(data.size, limit) - needle.size
        for (i in 0..end) {
            if (needle.indices.all { data[i + it] == needle[it] }) return i
        }
        return -1
    }
}
