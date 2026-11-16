package dev.lector.app.testing

import java.io.File

object Fixtures {
    /** Fixture versionado de `src/test/resources/fixtures/`. */
    fun file(name: String): File {
        val url = checkNotNull(Fixtures::class.java.classLoader?.getResource("fixtures/$name")) { "Falta el fixture $name" }
        return File(url.toURI())
    }

    fun bytes(name: String): ByteArray = file(name).readBytes()
}
