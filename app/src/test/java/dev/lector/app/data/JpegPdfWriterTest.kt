package dev.lector.app.data

import dev.lector.app.data.scan.JpegInfo
import dev.lector.app.data.scan.JpegPdfWriter
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegPdfWriterTest {
    private fun jpeg(w: Int, h: Int, gray: Boolean = false): ByteArray {
        val image = BufferedImage(w, h, if (gray) BufferedImage.TYPE_BYTE_GRAY else BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(14, 107, 86)
        g.fillRect(0, 0, w, h)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    private fun build(vararg pages: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        JpegPdfWriter(out).use { writer ->
            pages.forEach(writer::addPage)
            writer.finish()
        }
        return out.toByteArray()
    }

    @Test fun structureIsAValidPdf14() {
        val bytes = build(jpeg(640, 480), jpeg(300, 900), jpeg(800, 800))
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4\n%"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        // 1 catálogo + 1 Pages + 3 objetos por página
        assertEquals(2 + 3 * 3, Regex("""\d+ 0 obj""").findAll(text).count())
        assertTrue(text.contains("/Count 3"))
        assertTrue(text.contains("/Filter /DCTDecode") && text.contains("/ColorSpace /DeviceRGB") && text.contains("/BitsPerComponent 8"))
    }

    @Test fun everyXrefOffsetPointsAtItsObject() {
        val bytes = build(jpeg(640, 480), jpeg(300, 900))
        val text = String(bytes, Charsets.ISO_8859_1)
        val xrefStart = text.lastIndexOf("startxref\n").let { text.substring(it + 10).substringBefore('\n').toInt() }
        assertTrue(text.substring(xrefStart).startsWith("xref\n"))
        val entries = Regex("""(\d{10}) (\d{5}) ([nf]) \n""").findAll(text.substring(xrefStart)).toList()
        assertEquals(2 + 3 * 2 + 1, entries.size)
        assertEquals("f", entries.first().groupValues[3])
        entries.drop(1).forEachIndexed { index, match ->
            val offset = match.groupValues[1].toInt()
            assertTrue("objeto ${index + 1} en $offset", text.startsWith("${index + 1} 0 obj\n", offset))
        }
        assertTrue(text.contains("/Size ${entries.size}"))
    }

    @Test fun landscapeImagesGetLandscapePagesAndAreCentered() {
        val text = String(build(jpeg(800, 400), jpeg(400, 800)), Charsets.ISO_8859_1)
        assertTrue("apaisada", text.contains("/MediaBox [0 0 842 595]"))
        assertTrue("vertical", text.contains("/MediaBox [0 0 595 842]"))
        // 800×400 en 842×595: escala 1,0525 → 842 × 421 centrada en y = 87
        assertTrue(text.contains("q 842 0 0 421 0 87 cm /Im0 Do Q"))
    }

    @Test fun jpegHeaderIsParsedWithoutDecoding() {
        val info = JpegInfo.parse(jpeg(321, 123))
        assertEquals(JpegInfo(321, 123, 3, 8), info)
        assertNull(JpegInfo.parse(byteArrayOf(1, 2, 3, 4, 5)))
        assertNull(JpegInfo.parse(ByteArray(0)))
        assertNull(JpegInfo.parse(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())))
        assertNotNull(JpegInfo.parse(jpeg(2, 2)))
    }

    @Test fun rejectsAnythingThatIsNotARgbJpeg() {
        val writer = JpegPdfWriter(ByteArrayOutputStream())
        val failures = listOf(ByteArray(10), jpeg(10, 10, gray = true), "%PDF-1.4".toByteArray())
        failures.forEach { bad ->
            val error = runCatching { writer.addPage(bad) }.exceptionOrNull()
            assertTrue("debía rechazar", error is IllegalArgumentException)
        }
        assertEquals(0, writer.pageCount)
    }

    /** Con `LECTOR_DUMP_PDF=<ruta>` deja un PDF de muestra para validarlo con herramientas externas (`pdfinfo`, `qpdf`). */
    @Test fun dumpForExternalValidation() {
        val target = System.getenv("LECTOR_DUMP_PDF") ?: return
        java.io.File(target).writeBytes(build(jpeg(1240, 1754), jpeg(1754, 1240), jpeg(500, 500)))
    }

    @Test fun emptyDocumentsAreRejected() {
        val error = runCatching { JpegPdfWriter(ByteArrayOutputStream()).finish() }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
