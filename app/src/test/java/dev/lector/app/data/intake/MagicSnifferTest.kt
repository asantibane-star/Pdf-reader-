package dev.lector.app.data.intake

import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class MagicSnifferTest {
    private fun kind(name: String, hint: String? = null): Outcome<DocumentKind> =
        MagicSniffer.detect(Fixtures.file(name), hint)

    private fun assertKind(expected: DocumentKind, name: String, hint: String? = null) =
        assertEquals("fixture $name", Outcome.Success(expected), kind(name, hint))

    private fun assertFailure(expected: DocError, name: String) =
        assertEquals("fixture $name", Outcome.Failure(expected), kind(name))

    @Test fun pdfs() {
        listOf("simple.pdf", "long.pdf", "mixed-sizes.pdf", "encrypted.pdf", "truncated.pdf")
            .forEach { assertKind(DocumentKind.Pdf, it) }
    }

    @Test fun ooxml() {
        assertKind(DocumentKind.Docx, "simple.docx")
        assertKind(DocumentKind.Docx, "images.docx")
        assertKind(DocumentKind.Xlsx, "simple.xlsx")
        assertKind(DocumentKind.Xlsx, "big.xlsx")
        assertKind(DocumentKind.Pptx, "simple.pptx")
        assertKind(DocumentKind.Pptx, "theme-gradients.pptx")
        assertKind(DocumentKind.Pptx, "charts-tables.pptx")
        assertKind(DocumentKind.Pptx, "fonts-calibri.pptx")
        assertKind(DocumentKind.Pptx, "external-link.pptx")
        assertKind(DocumentKind.Pptx, "macro.pptm")
    }

    @Test fun legacyOle2() {
        assertKind(DocumentKind.Doc, "legacy.doc")
        assertKind(DocumentKind.Xls, "legacy.xls")
        assertKind(DocumentKind.Ppt, "legacy.ppt")
    }

    @Test fun text() {
        assertKind(DocumentKind.Text, "utf8.txt")
        assertKind(DocumentKind.Text, "latin1.txt")
        assertKind(DocumentKind.Csv, "sample.csv", hint = "datos.CSV")
        assertKind(DocumentKind.Text, "sample.csv", hint = "datos.txt")
    }

    @Test fun invalidFilesNeverCrash() {
        assertFailure(DocError.Corrupt, "empty.pdf")
        assertFailure(DocError.UnsupportedFormat, "fake.pdf")
        assertFailure(DocError.UnsupportedFormat, "binary.bin")
    }

    @Test fun extensionIsIgnored() {
        // Un PDF renombrado como .txt sigue siendo PDF: manda el contenido.
        assertKind(DocumentKind.Pdf, "simple.pdf", hint = "nota.txt")
    }

    @Test fun maliciousZipsAreStillSniffedAsTheirContainerOrRejected() {
        assertKind(DocumentKind.Docx, "zipbomb.docx")
        assertKind(DocumentKind.Docx, "traversal.docx")
        assertFailure(DocError.ZipBomb, "manyentries.docx")
    }

    @Test fun rtfIsDetectedByHeader() {
        val file = java.io.File.createTempFile("rtf", ".bin")
        try {
            file.writeText("{\\rtf1\\ansi Hola}")
            assertEquals(Outcome.Success(DocumentKind.Rtf), MagicSniffer.detect(file))
        } finally {
            file.delete()
        }
    }

    @Test fun pdfMagicMayAppearAfterLeadingBytes() {
        val file = java.io.File.createTempFile("lead", ".bin")
        try {
            file.writeBytes(ByteArray(200) { 0x20 } + Fixtures.bytes("simple.pdf"))
            assertEquals(Outcome.Success(DocumentKind.Pdf), MagicSniffer.detect(file))
        } finally {
            file.delete()
        }
    }
}
