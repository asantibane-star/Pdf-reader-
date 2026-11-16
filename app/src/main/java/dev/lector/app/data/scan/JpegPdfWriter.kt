package dev.lector.app.data.scan

import java.io.Closeable
import java.io.OutputStream
import java.util.Locale

/** Dimensiones y componentes de un JPEG, leídos de su marcador SOF. */
data class JpegInfo(val width: Int, val height: Int, val components: Int, val precision: Int) {
    companion object {
        /** Null si no es un JPEG válido. Solo se lee la cabecera; no se decodifican píxeles. */
        fun parse(bytes: ByteArray): JpegInfo? {
            if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
            var i = 2
            while (i + 3 < bytes.size) {
                if (bytes[i] != 0xFF.toByte()) return null
                var marker = bytes[i + 1].toInt() and 0xFF
                while (marker == 0xFF && i + 2 < bytes.size) { // relleno 0xFF
                    i++
                    marker = bytes[i + 1].toInt() and 0xFF
                }
                i += 2
                if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) continue // marcadores sin longitud
                if (marker == 0xD9 || marker == 0xDA) return null // fin de imagen o inicio de datos sin SOF
                if (i + 1 >= bytes.size) return null
                val length = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
                val isSof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
                if (isSof) {
                    if (i + 7 >= bytes.size) return null
                    val precision = bytes[i + 2].toInt() and 0xFF
                    val height = ((bytes[i + 3].toInt() and 0xFF) shl 8) or (bytes[i + 4].toInt() and 0xFF)
                    val width = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                    val components = bytes[i + 7].toInt() and 0xFF
                    return JpegInfo(width, height, components, precision)
                }
                if (length < 2) return null
                i += length
            }
            return null
        }
    }
}

/**
 * Escribe un PDF 1.4 mínimo, en streaming, con una imagen JPEG (DCTDecode) por página (PLAN §8.4).
 * Cuenta los desplazamientos byte a byte para la tabla `xref`. No usa `PdfDocument` (guarda bitmaps sin pérdida).
 * Solo acepta JPEG de 3 componentes y 8 bits, como los que produce `Bitmap.compress`.
 */
class JpegPdfWriter(private val out: OutputStream) : Closeable {
    private var position = 0L
    private val offsets = HashMap<Int, Long>()
    private val pageIds = ArrayList<Int>()
    private var nextId = FIRST_PAGE_OBJECT
    private var finished = false

    init {
        write(byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte(), '-'.code.toByte(), '1'.code.toByte(), '.'.code.toByte(), '4'.code.toByte(), '\n'.code.toByte()))
        // Línea binaria: indica a las herramientas que el archivo contiene datos de 8 bits.
        write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
        beginObject(CATALOG_ID)
        writeAscii("<< /Type /Catalog /Pages $PAGES_ID 0 R >>\nendobj\n")
    }

    val pageCount: Int get() = pageIds.size

    /** Añade una página con la imagen centrada, conservando su relación de aspecto. */
    fun addPage(jpeg: ByteArray) {
        check(!finished) { "El PDF ya está cerrado" }
        val info = requireNotNull(JpegInfo.parse(jpeg)) { "No es un JPEG válido" }
        require(info.components == 3 && info.precision == 8 && info.width > 0 && info.height > 0) { "JPEG no compatible (3 componentes, 8 bits)" }

        val landscape = info.width > info.height
        val pageW = if (landscape) A4_LONG else A4_SHORT
        val pageH = if (landscape) A4_SHORT else A4_LONG
        val scale = minOf(pageW / info.width, pageH / info.height)
        val w = info.width * scale
        val h = info.height * scale
        val x = (pageW - w) / 2
        val y = (pageH - h) / 2

        val pageId = nextId++
        val contentId = nextId++
        val imageId = nextId++

        beginObject(pageId)
        writeAscii(
            "<< /Type /Page /Parent $PAGES_ID 0 R /MediaBox [0 0 ${num(pageW)} ${num(pageH)}] " +
                "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n",
        )
        val content = "q ${num(w)} 0 0 ${num(h)} ${num(x)} ${num(y)} cm /Im0 Do Q\n".toByteArray(Charsets.US_ASCII)
        beginObject(contentId)
        writeAscii("<< /Length ${content.size} >>\nstream\n")
        write(content)
        writeAscii("endstream\nendobj\n")
        beginObject(imageId)
        writeAscii(
            "<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} /ColorSpace /DeviceRGB " +
                "/BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n",
        )
        write(jpeg)
        writeAscii("\nendstream\nendobj\n")
        pageIds += pageId
    }

    /** Escribe `Pages`, la tabla `xref` y el `trailer`. No cierra el flujo de salida. */
    fun finish() {
        if (finished) return
        require(pageIds.isNotEmpty()) { "Un PDF necesita al menos una página" }
        finished = true
        beginObject(PAGES_ID)
        writeAscii("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageIds.size} >>\nendobj\n")
        val xrefOffset = position
        val size = nextId
        val xref = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
        for (id in 1 until size) {
            xref.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.getValue(id)))
        }
        xref.append("trailer\n<< /Size $size /Root $CATALOG_ID 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        writeAscii(xref.toString())
        out.flush()
    }

    override fun close() {
        out.close()
    }

    private fun beginObject(id: Int) {
        offsets[id] = position
        writeAscii("$id 0 obj\n")
    }

    private fun writeAscii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

    private fun write(bytes: ByteArray) {
        out.write(bytes)
        position += bytes.size
    }

    /** Número PDF: punto decimal siempre, hasta 3 decimales y sin ceros sobrantes. */
    private fun num(value: Double): String = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

    companion object {
        const val A4_SHORT = 595.0
        const val A4_LONG = 842.0
        private const val CATALOG_ID = 1
        private const val PAGES_ID = 2
        private const val FIRST_PAGE_OBJECT = 3
    }
}
