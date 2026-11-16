package dev.lector.app.smoke

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.lector.app.LectorApp
import dev.lector.app.MainActivity
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.domain.model.DocumentRef
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captura las pantallas reales a PNG para compararlas con `docs/design/mockup.png`.
 * Solo escribe si se define `-Dlector.screens.dir=<carpeta>` (o la variable `LECTOR_SCREENS_DIR`); si no, solo comprueba que dibujan.
 */
abstract class ScreenshotBase {
    @get:Rule val compose = createEmptyComposeRule()
    protected val context: Context = ApplicationProvider.getApplicationContext()

    private val outDir: File? = (System.getenv("LECTOR_SCREENS_DIR") ?: System.getProperty("lector.screens.dir"))?.let(::File)

    protected fun await(text: String) = compose.waitUntil(timeoutMillis = 20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    protected fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        outDir?.let {
            it.mkdirs()
            File(it, "$name.png").outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
    }

    protected fun seedRecents() = runBlocking {
        val container = (context as LectorApp).container
        val hour = 3_600_000L
        val now = System.currentTimeMillis()
        data class Row(val name: String, val kind: DocumentKind, val size: Long, val ago: Long, val page: Int = 0, val pages: Int = 0)
        listOf(
            Row("Contrato de arriendo 2026", DocumentKind.Pdf, 2_300_000, hour / 2, 11, 48),
            Row("Presentación trimestral", DocumentKind.Pptx, 8_300_000, 2 * hour),
            Row("Factura 00418", DocumentKind.Pdf, 212_000, 5 * hour),
            Row("Carta de recomendación", DocumentKind.Docx, 48_000, 3 * 24 * hour),
            Row("Presupuesto hogar", DocumentKind.Xlsx, 96_000, 4 * 24 * hour),
            Row("Escaneo 2026-11-09 1832", DocumentKind.Pdf, 1_400_000, 5 * 24 * hour),
        ).forEachIndexed { i, r ->
            val ref = DocumentRef("id$i", "/x", r.kind, r.name, "content://seed/$i", r.size)
            val saved = (container.recents.record(ref, now - r.ago) as dev.lector.app.core.result.Outcome.Success).value
            if (r.pages > 0) container.recents.updateProgress(saved.id, r.page, r.pages)
        }
    }

    protected fun launchView(name: String, fixture: String) {
        val uri = "content://dev.lector.test/$name".toUri()
        val bytes = javaClass.classLoader!!.getResourceAsStream("fixtures/$fixture")!!.readBytes()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-w412dp-h892dp-notnight-xxhdpi")
class LightScreensTest : ScreenshotBase() {
    @Test fun homeEmpty() = ActivityScenario.launch(MainActivity::class.java).use {
        await("Tus documentos aparecerán aquí"); shot("home-empty-light")
    }

    @Test fun homeWithRecents() {
        seedRecents()
        ActivityScenario.launch(MainActivity::class.java).use { await("Contrato de arriendo 2026"); shot("home-light") }
    }

    @Test fun errorPane() {
        launchView("raro.bin", "binary.bin")
        await("Lector no puede abrir este tipo de archivo."); shot("error-light")
    }

    @Test fun settings() = ActivityScenario.launch(MainActivity::class.java).use {
        await("Tus documentos aparecerán aquí")
        compose.onNodeWithContentDescription("Ajustes").performClick()
        await("Apariencia"); shot("settings-light")
    }

    @Test fun textViewer() {
        launchView("utf8.txt", "utf8.txt")
        await("Última línea."); shot("text-light")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-w412dp-h892dp-night-xxhdpi")
class DarkScreensTest : ScreenshotBase() {
    @Test fun homeEmpty() = ActivityScenario.launch(MainActivity::class.java).use {
        await("Tus documentos aparecerán aquí"); shot("home-empty-dark")
    }

    @Test fun homeWithRecents() {
        seedRecents()
        ActivityScenario.launch(MainActivity::class.java).use { await("Contrato de arriendo 2026"); shot("home-dark") }
    }

    @Test fun errorPane() {
        launchView("raro.bin", "binary.bin")
        await("Lector no puede abrir este tipo de archivo."); shot("error-dark")
    }

    @Test fun settings() = ActivityScenario.launch(MainActivity::class.java).use {
        await("Tus documentos aparecerán aquí")
        compose.onNodeWithContentDescription("Ajustes").performClick()
        await("Apariencia"); shot("settings-dark")
    }
}
