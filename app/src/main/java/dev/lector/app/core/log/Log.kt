package dev.lector.app.core.log

import dev.lector.app.BuildConfig

/**
 * Envoltorio de logcat. No escribe nada en release y, en debug, el llamador solo debe pasar
 * mensajes técnicos: nunca nombres de archivo, URIs ni contenido de documentos.
 */
object Log {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) android.util.Log.d(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) android.util.Log.w(tag, message, error)
    }
}
