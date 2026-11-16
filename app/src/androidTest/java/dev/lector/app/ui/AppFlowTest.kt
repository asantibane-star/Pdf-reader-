package dev.lector.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lector.app.Fixtures
import dev.lector.app.MainActivity
import dev.lector.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PLAN §11.3: inicio vacío, abrir un fixture por intent VIEW, rotar conserva la página y recientes. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun await(text: String) = compose.waitUntil(timeoutMillis = 30_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun viewIntent(name: String, mime: String): Intent {
        val file = Fixtures.file(name)
        val shared = java.io.File(context.cacheDir, "share").apply { mkdirs() }.resolve(name).also { file.copyTo(it, overwrite = true) }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
        return Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test fun emptyHomeIsShownFirst() {
        // El emulador puede estar en inglés: los textos se leen de los recursos de la app, no se escriben a mano.
        val open = Fixtures.target.getString(R.string.home_open)
        val scan = Fixtures.target.getString(R.string.home_scan)
        ActivityScenario.launch(MainActivity::class.java).use {
            await(open)
            compose.onNodeWithText(scan).assertIsDisplayed()
        }
    }

    @Test fun aPdfOpensByViewIntentAndAppearsInRecents() {
        ActivityScenario.launch<MainActivity>(viewIntent("simple.pdf", "application/pdf")).use { scenario ->
            await("simple")
            scenario.recreate() // rotar / "No conservar actividades": el documento y la página siguen ahí
            await("simple")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            await("simple")
        }
    }

    @Test fun aTextFileOpensAndSurvivesRecreation() {
        ActivityScenario.launch<MainActivity>(viewIntent("utf8.txt", "text/plain")).use { scenario ->
            await("Última línea.")
            scenario.recreate()
            await("Última línea.")
        }
    }
}
