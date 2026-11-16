package dev.lector.app.data.intake

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Extrae el `content://` de un intent VIEW / SEND. Cualquier otro esquema se ignora. */
object IntentUris {
    fun extract(intent: Intent?): String? {
        if (intent == null) return null
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> streamExtra(intent)
            else -> null
        }
        return uri?.takeIf { it.scheme.equals("content", ignoreCase = true) }?.toString()
    }

    private fun streamExtra(intent: Intent): Uri? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    } catch (_: RuntimeException) {
        null
    }
}
