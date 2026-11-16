package dev.lector.app.data.office

import dev.lector.app.core.result.DocError

/** Mensajes que el visor JS puede enviar a Kotlin: lista cerrada, longitud máxima 64 (PLAN §4.3). */
sealed interface JsMessage {
    data object Ready : JsMessage
    data object Rendered : JsMessage
    data class Failed(val error: DocError) : JsMessage
    data class Sheets(val count: Int) : JsMessage
}

object ViewerMessages {
    const val MAX_LENGTH = 64
    private const val MAX_SHEETS = 50

    /** Devuelve null para todo lo que no esté en la lista cerrada (se descarta sin más). */
    fun parse(raw: String?): JsMessage? {
        if (raw == null || raw.length > MAX_LENGTH) return null
        return when {
            raw == "ready" -> JsMessage.Ready
            raw == "rendered" -> JsMessage.Rendered
            raw.startsWith("error:") -> JsMessage.Failed(errorFor(raw.removePrefix("error:")) ?: return null)
            raw.startsWith("sheets:") -> {
                val count = raw.removePrefix("sheets:").toIntOrNull()?.takeIf { it in 1..MAX_SHEETS } ?: return null
                JsMessage.Sheets(count)
            }
            else -> null
        }
    }

    private fun errorFor(code: String): DocError? = when (code) {
        "corrupt", "render" -> DocError.Corrupt
        "unsupported" -> DocError.UnsupportedFormat
        "timeout" -> DocError.RenderTimeout
        "memory" -> DocError.OutOfMemory
        "toolarge" -> DocError.TooLarge
        else -> null
    }
}

/** Nombres de hoja devueltos por `lectorApp.sheetNames()` (cadena JSON doblemente codificada por `evaluateJavascript`). */
object SheetNames {
    private const val MAX_NAMES = 50
    private const val MAX_LENGTH = 60

    fun parse(evaluateResult: String?): List<String> {
        if (evaluateResult.isNullOrBlank() || evaluateResult == "null") return emptyList()
        return try {
            val decoded = org.json.JSONTokener(evaluateResult).nextValue() as? String ?: return emptyList()
            val array = org.json.JSONArray(decoded)
            (0 until minOf(array.length(), MAX_NAMES)).map { array.optString(it).take(MAX_LENGTH) }
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }
}
