package dev.lector.app.core.crash

import java.io.File

/**
 * Guarda el último fallo no capturado en `filesDir/crash/last.txt` para "Ver último error" en Ajustes.
 * Solo se escriben clases y marcos de pila: sin mensajes (pueden contener rutas o nombres de archivo),
 * sin extras de intents.
 */
class CrashRecorder(private val dir: File) : Thread.UncaughtExceptionHandler {
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install() {
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            dir.mkdirs()
            File(dir, FILE_NAME).writeText(format(error))
        } catch (_: Exception) {
            // Registrar el fallo nunca debe provocar otro.
        }
        previous?.uncaughtException(thread, error)
    }

    fun last(): String? = try {
        File(dir, FILE_NAME).takeIf { it.isFile }?.readText()
    } catch (_: Exception) {
        null
    }

    fun clear() {
        File(dir, FILE_NAME).delete()
    }

    companion object {
        const val FILE_NAME = "last.txt"

        fun format(error: Throwable): String = buildString {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < MAX_CAUSES) {
                if (depth > 0) append("Caused by: ")
                append(current.javaClass.name).append('\n')
                current.stackTrace.take(MAX_FRAMES).forEach { append("    at ").append(it).append('\n') }
                current = current.cause
                depth++
            }
        }

        private const val MAX_CAUSES = 5
        private const val MAX_FRAMES = 40
    }
}
