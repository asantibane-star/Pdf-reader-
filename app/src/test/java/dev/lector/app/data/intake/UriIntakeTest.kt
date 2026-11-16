package dev.lector.app.data.intake

import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.testing.Fixtures
import dev.lector.app.testing.TestDispatchers
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UriIntakeTest {
    private lateinit var cache: File

    @Before fun setUp() {
        cache = Files.createTempDirectory("intake-test").toFile()
    }

    @After fun tearDown() {
        cache.deleteRecursively()
    }

    private class FakeSource(
        var stream: () -> InputStream,
        var name: String? = "Documento.pdf",
        var size: Long? = null,
    ) : UriSource {
        override fun open(uri: String): InputStream = stream()
        override fun displayName(uri: String): String? = name
        override fun declaredSize(uri: String): Long? = size
    }

    private fun TestScope0.intake(source: UriSource, freeSpace: Long = Long.MAX_VALUE) =
        UriIntake(source, cache, TestDispatchers(StandardTestDispatcher(testScheduler)), freeSpace = { freeSpace })

    private fun openDirFiles(): List<File> = File(cache, "open").listFiles()?.toList().orEmpty()

    @Test fun validPdfProducesDocumentRefOnCacheCopy() = runTest {
        val source = FakeSource({ ByteArrayInputStream(Fixtures.bytes("simple.pdf")) })
        val result = intake(source).copyToCache("content://x/1")
        val ref = (result as Outcome.Success).value
        assertEquals(DocumentKind.Pdf, ref.kind)
        assertEquals("Documento", ref.displayName)
        assertTrue(File(ref.cachePath).isFile)
        assertTrue(File(ref.cachePath).parentFile == File(cache, "open"))
        assertEquals("content://x/1", ref.sourceUri)
    }

    @Test fun nonContentSchemesAreRejected() = runTest {
        val source = FakeSource({ ByteArrayInputStream(ByteArray(10)) })
        listOf("file:///sdcard/a.pdf", "http://example.com/a.pdf", "/sdcard/a.pdf", "javascript:alert(1)").forEach {
            assertEquals(Outcome.Failure(DocError.UnsupportedSource), intake(source).copyToCache(it))
        }
        assertTrue(openDirFiles().isEmpty())
    }

    @Test fun declaredSizeOverLimitFailsBeforeCopying() = runTest {
        var opened = false
        val source = FakeSource({ opened = true; ByteArrayInputStream(ByteArray(1)) }, size = IntakeLimits.MAX_PDF_BYTES + 1)
        assertEquals(Outcome.Failure(DocError.TooLarge), intake(source).copyToCache("content://x/1"))
        assertTrue(!opened)
    }

    @Test fun notEnoughFreeSpaceFails() = runTest {
        val source = FakeSource({ ByteArrayInputStream(Fixtures.bytes("simple.pdf")) }, size = 1000)
        assertEquals(Outcome.Failure(DocError.NoSpace), intake(source, freeSpace = 1000).copyToCache("content://x/1"))
    }

    @Test fun actualBytesAreCountedNotTheDeclaredSize() = runTest {
        // Declara 10 bytes pero entrega más de 5 MB de texto: el texto no puede pasar de 5 MB.
        val big = ByteArray((IntakeLimits.MAX_TEXT_BYTES + 1024).toInt()) { 'a'.code.toByte() }
        val source = FakeSource({ ByteArrayInputStream(big) }, name = "grande.txt", size = 10)
        assertEquals(Outcome.Failure(DocError.TooLarge), intake(source).copyToCache("content://x/1"))
        assertTrue("el archivo rechazado no queda en caché", openDirFiles().isEmpty())
    }

    @Test fun officeOverFiftyMegabytesIsTooLarge() = runTest {
        // ZIP válido de ofimática con un blob incompresible: supera 50 MB en disco.
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write("<w:document/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("word/media/blob.bin"))
            val random = java.util.Random(7)
            val chunk = ByteArray(1024 * 1024)
            repeat(51) {
                random.nextBytes(chunk)
                zip.write(chunk)
            }
            zip.closeEntry()
        }
        val source = FakeSource({ ByteArrayInputStream(out.toByteArray()) }, name = "x.docx")
        assertEquals(Outcome.Failure(DocError.TooLarge), intake(source).copyToCache("content://x/1"))
        assertTrue(openDirFiles().isEmpty())
    }

    @Test fun zipBombIsRejectedAndNothingRemains() = runTest {
        val source = FakeSource({ ByteArrayInputStream(Fixtures.bytes("zipbomb.docx")) }, name = "b.docx")
        assertEquals(Outcome.Failure(DocError.ZipBomb), intake(source).copyToCache("content://x/1"))
        assertTrue(openDirFiles().isEmpty())
    }

    @Test fun unknownBinaryIsUnsupported() = runTest {
        val source = FakeSource({ ByteArrayInputStream(Fixtures.bytes("binary.bin")) }, name = "x.bin")
        assertEquals(Outcome.Failure(DocError.UnsupportedFormat), intake(source).copyToCache("content://x/1"))
        assertTrue(openDirFiles().isEmpty())
    }

    @Test fun revokedAccessMapsToAccessLost() = runTest {
        assertEquals(
            Outcome.Failure(DocError.AccessLost),
            intake(FakeSource({ throw SecurityException("revocado") })).copyToCache("content://x/1"),
        )
        assertEquals(
            Outcome.Failure(DocError.AccessLost),
            intake(FakeSource({ throw FileNotFoundException() })).copyToCache("content://x/1"),
        )
    }

    @Test fun cancellationDeletesThePartialCopy() = runTest {
        var jobRef: Job? = null
        val stream = object : InputStream() {
            private var calls = 0
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                calls++
                if (calls > 1) return -1
                "%PDF-1.4 parcial".toByteArray().copyInto(b, off)
                jobRef?.cancel() // el usuario cancela a mitad de la copia
                return 16
            }
        }
        val job = launch { intake(FakeSource({ stream })).copyToCache("content://x/1") }
        jobRef = job
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertTrue("la copia parcial se borra al cancelar", openDirFiles().isEmpty())
    }
}

private typealias TestScope0 = kotlinx.coroutines.test.TestScope
