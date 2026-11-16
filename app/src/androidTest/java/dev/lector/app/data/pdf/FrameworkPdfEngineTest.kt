package dev.lector.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lector.app.Fixtures
import dev.lector.app.core.dispatch.DefaultAppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.data.scan.JpegPdfWriter
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** PLAN §11.3: `FrameworkPdfEngine` contra los fixtures, en un dispositivo real. */
@RunWith(AndroidJUnit4::class)
class FrameworkPdfEngineTest {
    private val engine = FrameworkPdfEngine(DefaultAppDispatchers())

    private fun open(name: String, password: String? = null): Outcome<PdfSession> = runBlocking { engine.open(Fixtures.file(name), password) }

    private fun session(name: String): PdfSession = (open(name) as Outcome.Success).value

    @Test fun validPdfsOpenAndRender() = runBlocking {
        for ((name, pages) in listOf("simple.pdf" to 3, "long.pdf" to 500, "mixed-sizes.pdf" to 4)) {
            val s = session(name)
            assertEquals(name, pages, s.pageCount)
            val bitmap = (s.render(0, 400, 566) as Outcome.Success).value
            assertEquals(Color.WHITE, bitmap.getPixel(2, 2))
            assertTrue("la página tiene contenido (texto)", (0 until bitmap.width step 3).any { x -> (0 until bitmap.height step 3).any { y -> bitmap.getPixel(x, y) != Color.WHITE } })
            s.close()
        }
    }

    @Test fun pageSizesAreInPdfPoints() = runBlocking {
        val s = session("mixed-sizes.pdf")
        assertEquals(842f, (s.pageSize(1) as Outcome.Success).value.width, 1f)
        assertEquals(300f, (s.pageSize(2) as Outcome.Success).value.width, 1f)
        s.close()
    }

    @Test fun regionRenderMatchesTheFullRenderScale() = runBlocking {
        val s = session("simple.pdf")
        val full = (s.render(0, 800, 1132) as Outcome.Success).value
        val region = (s.render(0, 800, 1132, android.graphics.RectF(0f, 0f, 0.5f, 0.5f)) as Outcome.Success).value
        assertEquals(400, region.width)
        assertEquals(566, region.height)
        assertEquals(full.getPixel(10, 10), region.getPixel(10, 10))
        s.close()
    }

    @Test fun brokenFilesAreCorruptNeverAnException() {
        listOf("truncated.pdf", "empty.pdf", "fake.pdf").forEach {
            assertEquals(it, Outcome.Failure(DocError.Corrupt), open(it))
        }
    }

    @Test fun encryptedNeedsAPasswordAndOpensWithIt() {
        assertEquals(Outcome.Failure(DocError.PasswordRequired), open("encrypted.pdf"))
        if (Build.VERSION.SDK_INT >= 35) {
            assertEquals(Outcome.Failure(DocError.WrongPassword), open("encrypted.pdf", "mal"))
            assertEquals(3, (open("encrypted.pdf", "test") as Outcome.Success).value.pageCount)
        }
    }

    @Test fun twoHundredConcurrentRendersAndCloseInTheMiddleDoNotCrash() = runBlocking {
        val s = session("long.pdf")
        val jobs = (0 until 200).map { i -> async { s.render(i % 500, 200, 283) } }
        launch {
            delay(30)
            s.close()
        }
        val results = jobs.awaitAll()
        assertTrue(results.all { it is Outcome.Success || (it as Outcome.Failure).error == DocError.Closed })
        assertEquals(Outcome.Failure(DocError.Closed), s.render(0, 100, 100))
    }

    @Test fun searchWorksOnAndroid15AndNewer() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        val s = session("simple.pdf")
        val hits = mutableListOf<dev.lector.app.domain.model.SearchHit>()
        s.search("Pagina").collect { hits += it }
        assertEquals(3, hits.size)
        assertEquals(listOf(0, 1, 2), hits.map { it.page })
        s.close()
    }

    @Test fun aPdfWrittenByJpegPdfWriterOpensWithTheExpectedPages() = runBlocking {
        val out = File(Fixtures.target.cacheDir, "writer-test.pdf")
        JpegPdfWriter(out.outputStream().buffered()).use { writer ->
            repeat(3) { index ->
                val bitmap = Bitmap.createBitmap(600 + index * 100, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(14, 107, 86)) }
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                writer.addPage(bytes)
            }
            writer.finish()
        }
        val s = (engine.open(out) as Outcome.Success).value
        assertEquals(3, s.pageCount)
        val bitmap = (s.render(0, 300, 424) as Outcome.Success).value
        assertTrue("la imagen verde se ve", bitmap.getPixel(150, 212) != Color.WHITE)
        s.close()
    }
}
