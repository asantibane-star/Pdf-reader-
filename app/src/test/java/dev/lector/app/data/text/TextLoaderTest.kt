package dev.lector.app.data.text

import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.testing.Fixtures
import dev.lector.app.testing.TestDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLoaderTest {
    private fun loader(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        TextLoader(TestDispatchers(StandardTestDispatcher(testScheduler)))

    @Test fun decodesUtf8() = runTest {
        val lines = (loader(testScheduler).load(Fixtures.file("utf8.txt").path) as Outcome.Success).value
        assertEquals("Línea con acentos: canción, año, ñandú.", lines[0])
        assertTrue(lines[1].contains("日本語") && lines[1].contains("🙂"))
        assertEquals("Última línea.", lines.last())
    }

    @Test fun fallsBackToLatin1() = runTest {
        val lines = (loader(testScheduler).load(Fixtures.file("latin1.txt").path) as Outcome.Success).value
        assertEquals("Línea Latin-1: canción, año, ñandú.", lines[0])
    }

    @Test fun missingFileIsAccessLost() = runTest {
        assertEquals(Outcome.Failure(DocError.AccessLost), loader(testScheduler).load("/no/existe.txt"))
    }

    @Test fun stripsBomAndSplitsLineEndings() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "a\r\nb\rc\nd\n".toByteArray()
        assertEquals(listOf("a", "b", "c", "d"), TextLoader.toLines(TextLoader.decode(bytes)))
    }

    @Test fun veryLongLinesAreSplit() {
        val lines = TextLoader.toLines("x".repeat(TextLoader.MAX_LINE_CHARS * 2 + 5))
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.length <= TextLoader.MAX_LINE_CHARS })
    }
}
