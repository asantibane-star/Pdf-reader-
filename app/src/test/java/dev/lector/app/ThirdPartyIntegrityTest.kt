package dev.lector.app

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recalcula el SHA-256 de cada archivo de terceros listado en `THIRD_PARTY.md` (PLAN §2). */
class ThirdPartyIntegrityTest {
    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "THIRD_PARTY.md").isFile }

    @Test fun everyListedFileMatchesItsRecordedHash() {
        val rows = File(repoRoot, "THIRD_PARTY.md").readLines().filter { it.startsWith("| `") }
        assertTrue("THIRD_PARTY.md debe listar archivos", rows.size >= 8)
        for (row in rows) {
            val cells = row.split("|").map { it.trim() }
            val path = cells[1].trim('`')
            val expected = cells.last { it.startsWith("`") }.trim('`')
            val file = File(repoRoot, path)
            assertTrue("falta $path", file.isFile)
            assertEquals("SHA-256 de $path", expected, sha256(file))
        }
    }

    @Test fun everyVendoredScriptIsListed() {
        val listed = File(repoRoot, "THIRD_PARTY.md").readText()
        File(repoRoot, "app/src/main/assets/viewer/vendor").listFiles()!!.forEach {
            assertTrue("${it.name} no está en THIRD_PARTY.md", listed.contains("viewer/vendor/${it.name}"))
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
