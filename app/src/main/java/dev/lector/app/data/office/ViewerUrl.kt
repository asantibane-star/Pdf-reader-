package dev.lector.app.data.office

import android.net.Uri
import dev.lector.app.domain.model.DocumentKind

/** Colores del tema como `RRGGBB` (sin `#`), tomados de los tokens activos (DESIGN §6.5). */
data class ViewerTheme(
    val bg: String,
    val surface: String,
    val sunken: String,
    val text: String,
    val muted: String,
    val line: String,
    val accent: String,
)

/** Construye la única URL que carga el WebView: `https://appassets.androidplatform.net/viewer/index.html?…`. */
object ViewerUrl {
    const val HOST = "appassets.androidplatform.net"
    const val ORIGIN = "https://$HOST"

    /** Parámetro `kind` del visor JS para cada tipo; null si el motor B no lo muestra. */
    fun jsKind(kind: DocumentKind): String? = when (kind) {
        DocumentKind.Xlsx -> "xlsx"
        DocumentKind.Xls -> "xls"
        DocumentKind.Ods -> "ods"
        DocumentKind.Csv -> "csv"
        DocumentKind.Docx -> "docx"
        DocumentKind.Pptx -> "pptx"
        else -> null
    }

    fun build(
        jsKind: String,
        nonce: String,
        theme: ViewerTheme,
        topInsetDp: Int,
        bottomInsetDp: Int,
        notice: String,
        language: String,
    ): String = Uri.Builder()
        .scheme("https")
        .authority(HOST)
        .path("/viewer/index.html")
        .appendQueryParameter("kind", jsKind)
        .appendQueryParameter("nonce", nonce)
        .appendQueryParameter("bg", theme.bg)
        .appendQueryParameter("surface", theme.surface)
        .appendQueryParameter("sunken", theme.sunken)
        .appendQueryParameter("text", theme.text)
        .appendQueryParameter("muted", theme.muted)
        .appendQueryParameter("line", theme.line)
        .appendQueryParameter("accent", theme.accent)
        .appendQueryParameter("ti", topInsetDp.coerceIn(0, MAX_INSET).toString())
        .appendQueryParameter("bi", bottomInsetDp.coerceIn(0, MAX_INSET).toString())
        .appendQueryParameter("notice", notice.take(MAX_NOTICE))
        .appendQueryParameter("lang", language.take(2).lowercase())
        .build()
        .toString()

    private const val MAX_INSET = 400
    private const val MAX_NOTICE = 200
}
