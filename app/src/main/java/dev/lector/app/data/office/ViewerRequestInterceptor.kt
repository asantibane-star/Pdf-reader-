package dev.lector.app.data.office

import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

/**
 * Responde SOLO (a) los assets bajo `/viewer/` y (b) `/doc/<nonce>` con el archivo en caché de la sesión.
 * Todo lo demás recibe un 403 vacío (PLAN §4.3). Cada respuesta lleva la CSP como cabecera.
 */
class ViewerRequestInterceptor(
    private val assets: AssetManager,
    /** Archivo asociado a un nonce, o null si no es el de la sesión actual. */
    private val documentFor: (nonce: String) -> File?,
) {
    fun intercept(method: String, scheme: String?, host: String?, path: String?): WebResourceResponse {
        if (method != "GET" || scheme != "https" || host != ViewerUrl.HOST || path == null) return forbidden()
        return when {
            path.startsWith(VIEWER_PREFIX) -> asset(path.removePrefix(VIEWER_PREFIX))
            path.startsWith(DOC_PREFIX) -> document(path.removePrefix(DOC_PREFIX))
            else -> forbidden()
        }
    }

    private fun asset(relative: String): WebResourceResponse {
        if (relative.isEmpty() || relative.contains("..") || relative.contains('\\') || relative.startsWith("/")) return forbidden()
        return try {
            response(mimeFor(relative), assets.open("viewer/$relative"))
        } catch (_: FileNotFoundException) {
            forbidden()
        } catch (_: IOException) {
            forbidden()
        }
    }

    private fun document(nonce: String): WebResourceResponse {
        if (!NONCE.matches(nonce)) return forbidden()
        val file = documentFor(nonce) ?: return forbidden()
        return try {
            response("application/octet-stream", FileInputStream(file))
        } catch (_: IOException) {
            forbidden()
        }
    }

    private fun response(mime: String, data: java.io.InputStream) =
        WebResourceResponse(mime, ENCODING, STATUS_OK, "OK", HEADERS, data)

    private fun forbidden() =
        WebResourceResponse("text/plain", ENCODING, STATUS_FORBIDDEN, "Forbidden", HEADERS, ByteArrayInputStream(ByteArray(0)))

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    companion object {
        const val VIEWER_PREFIX = "/viewer/"
        const val DOC_PREFIX = "/doc/"
        private const val ENCODING = "utf-8"
        private const val STATUS_OK = 200
        private const val STATUS_FORBIDDEN = 403
        private val NONCE = Regex("[0-9a-f]{32}")

        /** CSP idéntica a la de `index.html` (PLAN §4.3), también como cabecera. */
        const val CSP = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src blob: data:; " +
            "font-src blob: data:; connect-src 'self'; base-uri 'none'; form-action 'none'"

        val HEADERS: Map<String, String> = mapOf(
            "Content-Security-Policy" to CSP,
            "X-Content-Type-Options" to "nosniff",
            "Cache-Control" to "no-store",
            "Referrer-Policy" to "no-referrer",
        )

        /** Comparación del nonce en tiempo constante. */
        fun nonceMatches(expected: String, actual: String): Boolean =
            MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))
    }
}
