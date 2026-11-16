package dev.lector.app.core

import dev.lector.app.core.crash.CrashRecorder
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRecorderTest {
    @Test fun recordsStackFramesButNeverTheMessage() {
        val dir = Files.createTempDirectory("crash").toFile()
        val recorder = CrashRecorder(dir)
        val error = IllegalStateException("content://secret/documento-privado.pdf", RuntimeException("/storage/emulated/0/x"))
        val text = CrashRecorder.format(error)
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("Caused by: java.lang.RuntimeException"))
        assertFalse("no debe incluir mensajes (pueden traer rutas o nombres)", text.contains("secret") || text.contains("storage"))
        assertNull(recorder.last())
        java.io.File(dir, CrashRecorder.FILE_NAME).writeText(text)
        assertNotNull(recorder.last())
        recorder.clear()
        assertNull(recorder.last())
        dir.deleteRecursively()
    }
}
