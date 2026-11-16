package dev.lector.app.data.intake

import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipInspectorTest {
    @Test fun validFilesPass() = runTest {
        listOf("simple.docx", "simple.xlsx", "big.xlsx", "simple.pptx", "charts-tables.pptx", "macro.pptm").forEach {
            assertEquals(it, Outcome.Success(Unit), ZipInspector.validate(Fixtures.file(it)))
        }
    }

    @Test fun zipBombIsRejectedByRealDecompressedSize() = runTest {
        assertEquals(Outcome.Failure(DocError.ZipBomb), ZipInspector.validate(Fixtures.file("zipbomb.docx")))
    }

    @Test fun pathTraversalIsRejected() = runTest {
        assertEquals(Outcome.Failure(DocError.Corrupt), ZipInspector.validate(Fixtures.file("traversal.docx")))
    }

    @Test fun tooManyEntriesIsRejected() = runTest {
        assertEquals(Outcome.Failure(DocError.ZipBomb), ZipInspector.validate(Fixtures.file("manyentries.docx")))
    }

    @Test fun notAZipIsCorrupt() = runTest {
        assertEquals(Outcome.Failure(DocError.Corrupt), ZipInspector.validate(Fixtures.file("utf8.txt")))
    }

    @Test fun nameRules() {
        assertTrue(ZipInspector.isSafeName("word/document.xml"))
        assertTrue(ZipInspector.isSafeName("word/media/a..b.png"))
        assertFalse(ZipInspector.isSafeName("../x"))
        assertFalse(ZipInspector.isSafeName("a/../../x"))
        assertFalse(ZipInspector.isSafeName("/etc/passwd"))
        assertFalse(ZipInspector.isSafeName("a\\b"))
        assertFalse(ZipInspector.isSafeName("C:/x"))
        assertFalse(ZipInspector.isSafeName(""))
    }
}
