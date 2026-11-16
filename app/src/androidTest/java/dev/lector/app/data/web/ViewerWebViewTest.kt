package dev.lector.app.data.web

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lector.app.Fixtures
import dev.lector.app.core.dispatch.DefaultAppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.data.intake.UriIntake
import dev.lector.app.data.intake.UriSource
import dev.lector.app.data.office.JsMessage
import dev.lector.app.data.office.SecureWebViewFactory
import dev.lector.app.data.office.ViewerCallbacks
import dev.lector.app.data.office.ViewerRequestInterceptor
import dev.lector.app.data.office.ViewerTheme
import dev.lector.app.data.office.ViewerUrl
import java.io.File
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** PLAN §11.3: el visor web real, con la CSP real, en un WebView real. */
@RunWith(AndroidJUnit4::class)
class ViewerWebViewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nonce = "00112233445566778899aabbccddeeff"
    private val theme = ViewerTheme("F4F5F6", "FCFCFD", "E9EBEE", "15171A", "5C636B", "15171A", "0E6B56")

    private class Session(val webView: WebView, val messages: CopyOnWriteArrayList<JsMessage>, val rendered: CountDownLatch, val failed: CountDownLatch)

    private fun open(file: File, jsKind: String): Session {
        val messages = CopyOnWriteArrayList<JsMessage>()
        val rendered = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val callbacks = object : ViewerCallbacks {
            override fun onMessage(message: JsMessage) {
                messages += message
                if (message == JsMessage.Rendered) rendered.countDown()
                if (message is JsMessage.Failed) failed.countDown()
            }
            override fun onExternalLink(url: String) = Unit
            override fun onRenderProcessGone() = Unit
        }
        val interceptor = ViewerRequestInterceptor(context.assets) { if (it == nonce) file else null }
        var webView: WebView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = (SecureWebViewFactory.create(context, interceptor, callbacks) as Outcome.Success).value.also {
                it.loadUrl(ViewerUrl.build(jsKind, nonce, theme, 72, 96, "aviso", "es"))
            }
        }
        return Session(webView!!, messages, rendered, failed)
    }

    private fun eval(webView: WebView, script: String): String {
        val result = CountDownLatch(1)
        var value = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(script) { value = it ?: ""; result.countDown() }
        }
        assertTrue(result.await(10, TimeUnit.SECONDS))
        return value
    }

    @Test fun spreadsheetRendersAndEmitsRendered() {
        val s = open(Fixtures.file("simple.xlsx"), "xlsx")
        assertTrue("rendered", s.rendered.await(30, TimeUnit.SECONDS))
        assertTrue(s.messages.contains(JsMessage.Sheets(3)))
        assertTrue(eval(s.webView, "document.body.innerText").contains("Ingresos"))
    }

    @Test fun legacyXlsAndCsvRender() {
        for ((name, kind) in listOf("legacy.xls" to "xls", "sample.csv" to "csv")) {
            val s = open(Fixtures.file(name), kind)
            assertTrue(name, s.rendered.await(30, TimeUnit.SECONDS))
        }
    }

    @Test fun networkAndWrongNonceRequestsAreBlocked() {
        val s = open(Fixtures.file("simple.xlsx"), "xlsx")
        assertTrue(s.rendered.await(30, TimeUnit.SECONDS))
        val script = """
            (async () => {
              const out = [];
              for (const url of ['https://example.com/', '/doc/ffffffffffffffffffffffffffffffff']) {
                try { const r = await fetch(url, {cache: 'no-store'}); out.push(r.status); } catch (e) { out.push('blocked'); }
              }
              window.__net = out.join(',');
            })(); 'ok'
        """.trimIndent()
        eval(s.webView, script)
        Thread.sleep(1500)
        val net = eval(s.webView, "window.__net")
        assertTrue("example.com y nonce incorrecto bloqueados: $net", net.contains("blocked") || net.contains("403"))
        assertTrue(!net.contains("200"))
    }

    @Test fun brokenDocumentsEmitAnErrorNotACrash() {
        val s = open(Fixtures.file("truncated.pdf"), "docx")
        assertTrue(s.failed.await(30, TimeUnit.SECONDS))
        assertTrue(s.messages.any { it is JsMessage.Failed })
    }

    @Test fun zipBombIsRejectedBeforeReachingTheWebView() = runBlocking {
        val bomb = Fixtures.file("zipbomb.docx")
        val intake = UriIntake(
            source = object : UriSource {
                override fun open(uri: String): InputStream = bomb.inputStream()
                override fun displayName(uri: String) = "bomba.docx"
                override fun declaredSize(uri: String): Long? = null
            },
            cacheDir = File(context.cacheDir, "intake-test").apply { mkdirs() },
            dispatchers = DefaultAppDispatchers(),
        )
        assertEquals(Outcome.Failure(DocError.ZipBomb), intake.copyToCache("content://x/bomba"))
    }

    @Test fun presentationsRenderWithTheApproximateViewer() {
        val s = open(Fixtures.file("charts-tables.pptx"), "pptx")
        assertTrue(s.rendered.await(60, TimeUnit.SECONDS))
        assertTrue(eval(s.webView, "document.body.innerText").contains("Gráfico"))
    }
}
