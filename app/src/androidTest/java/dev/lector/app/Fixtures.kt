package dev.lector.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Copia un fixture de `assets/fixtures/` (compartidos con las pruebas JVM) a la caché del dispositivo. */
object Fixtures {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().context
    val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun file(name: String): File {
        val out = File(target.cacheDir, "fixtures-$name")
        context.assets.open("fixtures/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }
}
