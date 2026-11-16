package dev.lector.app.data.office

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JsResult
import android.webkit.JsPromptResult
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.lector.app.BuildConfig
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome

/** Lo que el visor necesita saber del WebView. Todo llega ya validado. */
interface ViewerCallbacks {
    fun onMessage(message: JsMessage)

    /** Enlace `http(s)` tocado por el usuario: se confirma con un diálogo; nunca se abre solo. */
    fun onExternalLink(url: String)

    /** El proceso de render del WebView murió: se destruye y se ofrece "Reintentar". */
    fun onRenderProcessGone()
}

/**
 * ÚNICO punto de creación de WebView de toda la app (PLAN §4.3). Prohibido `addJavascriptInterface`.
 * Solo carga `https://appassets.androidplatform.net/viewer/index.html` y responde vía [ViewerRequestInterceptor].
 */
object SecureWebViewFactory {
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, interceptor: ViewerRequestInterceptor, callbacks: ViewerCallbacks): Outcome<WebView> = try {
        val webView = WebView(context)
        with(webView.settings) {
            javaScriptEnabled = true // solo ejecuta assets propios; ver CSP
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            domStorageEnabled = false
            @Suppress("DEPRECATION")
            databaseEnabled = false
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            builtInZoomControls = true
            displayZoomControls = false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            // Los colores ya vienen de los tokens del tema: el sistema no debe oscurecerlos de nuevo.
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.webViewClient = SecureClient(interceptor, callbacks)
        webView.webChromeClient = SecureChromeClient(callbacks)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, setOf(ViewerUrl.ORIGIN)) { _, message, sourceOrigin, isMainFrame, _ ->
                if (isMainFrame && sourceOrigin.toString() == ViewerUrl.ORIGIN) {
                    ViewerMessages.parse(message.data)?.let(callbacks::onMessage)
                }
            }
        }
        Outcome.Success(webView)
    } catch (_: Throwable) {
        // WebView ausente, deshabilitado o roto: se informa, no se cae la app.
        Outcome.Failure(DocError.WebViewUnavailable)
    }

    private const val BRIDGE_NAME = "lectorBridge"

    private class SecureClient(
        private val interceptor: ViewerRequestInterceptor,
        private val callbacks: ViewerCallbacks,
    ) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val url = request?.url ?: return null
            return interceptor.intercept(request.method, url.scheme, url.host, url.path)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url
            if (url != null && request.hasGesture() && isWebLink(url)) callbacks.onExternalLink(url.toString())
            // Siempre se bloquea la navegación dentro del WebView; cualquier otro esquema se ignora.
            return true
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            callbacks.onRenderProcessGone()
            return true
        }

        private fun isWebLink(uri: Uri) = uri.scheme.equals("http", true) || uri.scheme.equals("https", true)
    }

    private class SecureChromeClient(private val callbacks: ViewerCallbacks) : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.deny()
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            return false
        }

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            result?.cancel()
            return true
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            result?.cancel()
            return true
        }

        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
            result?.cancel()
            return true
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            // Respaldo cuando WEB_MESSAGE_LISTENER no existe: el visor escribe el mensaje en document.title.
            ViewerMessages.parse(title)?.let(callbacks::onMessage)
        }

        override fun getDefaultVideoPoster(): Bitmap? = null

        override fun getVideoLoadingProgressView(): View? = null
    }
}
