package dev.lector.app.data.intake

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCleanerTest {
    private val cache = Files.createTempDirectory("cache").toFile()

    private fun touch(path: String) = File(cache, path).apply { parentFile?.mkdirs(); writeText("x") }

    @Test fun startCleanupRemovesTransientFoldersButKeepsConvertedCache() {
        touch("open/a.bin"); touch("share/b.pdf"); touch("camera/c.jpg"); val converted = touch("converted/d.pdf")
        CacheCleaner.cleanOnStart(cache)
        assertFalse(File(cache, "open").exists() || File(cache, "share").exists() || File(cache, "camera").exists())
        assertTrue(converted.exists())
        cache.deleteRecursively()
    }

    @Test fun deleteOpenFileOnlyTouchesFilesInsideOpen() {
        val inside = touch("open/a.bin"); val outside = touch("other/b.bin")
        CacheCleaner.deleteOpenFile(cache, outside.path)
        assertTrue(outside.exists())
        CacheCleaner.deleteOpenFile(cache, inside.path)
        assertFalse(inside.exists())
        cache.deleteRecursively()
    }
}
