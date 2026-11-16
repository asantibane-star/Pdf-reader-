package dev.lector.app.data.office

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewerWebTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nonce = "0123456789abcdef0123456789abcdef"
    private val doc: File = File.createTempFile("doc", ".bin").apply { writeText("contenido"); deleteOnExit() }
    private val interceptor = ViewerRequestInterceptor(context.assets) { candidate -> if (candidate == nonce) doc else null }

    private fun get(path: String, host: String = ViewerUrl.HOST, scheme: String = "https", method: String = "GET") =
        interceptor.intercept(method, scheme, host, path)

    @Test fun servesViewerAssetsWithCspHeader() {
        val response = get("/viewer/index.html")
        assertEquals(200, response.statusCode)
        assertEquals("text/html", response.mimeType)
        assertEquals(ViewerRequestInterceptor.CSP, response.responseHeaders["Content-Security-Policy"])
        assertEquals("nosniff", response.responseHeaders["X-Content-Type-Options"])
        assertTrue(response.data.readBytes().isNotEmpty())
    }

    @Test fun jsAssetsUseJavascriptMime() {
        assertEquals("text/javascript", get("/viewer/viewer.js").mimeType)
        assertEquals("text/javascript", get("/viewer/vendor/pptx-renderer.browser.es.js").mimeType)
        assertEquals("text/css", get("/viewer/viewer.css").mimeType)
    }

    @Test fun servesTheDocumentOnlyForTheSessionNonce() {
        val ok = get("/doc/$nonce")
        assertEquals(200, ok.statusCode)
        assertEquals("contenido", ok.data.readBytes().decodeToString())
        assertEquals(403, get("/doc/ffffffffffffffffffffffffffffffff").statusCode)
        assertEquals(403, get("/doc/").statusCode)
        assertEquals(403, get("/doc/$nonce/extra").statusCode)
        assertEquals(403, get("/doc/${nonce.uppercase()}").statusCode)
    }

    @Test fun everythingElseIsAnEmptyForbidden() {
        val cases = listOf(
            get("/viewer/../AndroidManifest.xml"),
            get("/viewer/missing.js"),
            get("/viewer/"),
            get("/etc/passwd"),
            get("/", host = "example.com"),
            get("/viewer/index.html", host = "example.com"),
            get("/viewer/index.html", scheme = "http"),
            get("/viewer/index.html", method = "POST"),
            get("/viewer/%2e%2e/x"),
            interceptor.intercept("GET", "https", ViewerUrl.HOST, null),
        )
        cases.forEach {
            assertEquals(403, it.statusCode)
            assertEquals(0, it.data.readBytes().size)
            assertNotNull(it.responseHeaders["Content-Security-Policy"])
        }
    }

    @Test fun urlCarriesKindNonceAndTheme() {
        val theme = ViewerTheme("F4F5F6", "FCFCFD", "E9EBEE", "15171A", "5C636B", "15171A", "0E6B56")
        val url = ViewerUrl.build("xlsx", nonce, theme, topInsetDp = 90, bottomInsetDp = 700, notice = "aviso", language = "ES")
        assertTrue(url.startsWith("https://appassets.androidplatform.net/viewer/index.html?"))
        assertTrue(url.contains("kind=xlsx") && url.contains("nonce=$nonce") && url.contains("accent=0E6B56"))
        assertTrue("el inset se acota", url.contains("bi=400"))
        assertTrue(url.contains("lang=es"))
    }

    @Test fun sheetNamesParsesTheDoubleEncodedResult() {
        assertEquals(listOf("Resumen", "Ventas"), SheetNames.parse("\"[\\\"Resumen\\\",\\\"Ventas\\\"]\""))
        assertEquals(emptyList<String>(), SheetNames.parse("null"))
        assertEquals(emptyList<String>(), SheetNames.parse(null))
        assertEquals(emptyList<String>(), SheetNames.parse("\"no es json\""))
    }

    @Test fun jsKindsCoverTheWebEngineFormats() {
        import_kinds()
    }

    private fun import_kinds() {
        val supported = dev.lector.app.domain.model.DocumentKind.entries.filter { ViewerUrl.jsKind(it) != null }
        assertEquals(
            setOf("Xlsx", "Xls", "Ods", "Csv", "Docx", "Pptx"),
            supported.map { it.name }.toSet(),
        )
    }
}
