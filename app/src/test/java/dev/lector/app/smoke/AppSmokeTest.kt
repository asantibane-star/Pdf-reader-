package dev.lector.app.smoke

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.lector.app.MainActivity
import java.io.ByteArrayInputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La app real (Application, tema, fuentes, navegación, ingesta y visor de texto) bajo Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es")
class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Espera a que aparezca un texto (Room entrega los recientes desde otro hilo). */
    private fun awaitText(text: String) = compose.waitUntil(timeoutMillis = 20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun emptyHomeShowsTheEmptyStateAndTheDock() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Tus documentos aparecerán aquí")
            compose.onNodeWithText("Tus documentos aparecerán aquí").assertIsDisplayed()
            compose.onNodeWithText("Abrir").assertIsDisplayed()
            compose.onNodeWithText("Escanear").assertIsDisplayed()
        }
    }

    @Test fun settingsOpenFromHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Tus documentos aparecerán aquí")
            compose.onNodeWithContentDescription("Ajustes").performClick()
            compose.onNodeWithText("Apariencia").assertIsDisplayed()
            compose.onNodeWithText("Borrar recientes").assertIsDisplayed()
        }
    }

    @Test fun aTextFileSharedByViewIntentOpensInTheViewer() {
        val uri = "content://dev.lector.test/utf8.txt".toUri()
        val bytes = javaClass.classLoader!!.getResourceAsStream("fixtures/utf8.txt")!!.readBytes()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithText("Última línea.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Última línea.").assertIsDisplayed()
        }
    }

    @Test fun anUnsupportedFileEndsInAnErrorPaneWithAWayOut() {
        val uri = "content://dev.lector.test/raro.bin".toUri()
        val bytes = javaClass.classLoader!!.getResourceAsStream("fixtures/binary.bin")!!.readBytes()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithText("Lector no puede abrir este tipo de archivo.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Volver").assertIsDisplayed()
        }
    }
}
