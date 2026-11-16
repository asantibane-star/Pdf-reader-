package dev.lector.app.data.intake

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Sanea los nombres que escribe el usuario (PLAN §4.2). Los nombres recibidos de otras apps solo se muestran. */
object FileNameSanitizer {
    const val MAX_LENGTH = 100
    private val forbidden = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")

    fun defaultScanName(now: LocalDateTime): String = "Escaneo ${stamp.format(now)}"

    fun sanitize(raw: String, now: LocalDateTime = LocalDateTime.now()): String {
        val cleaned = raw
            .filterNot { it in forbidden || it.isISOControl() }
            .trim()
            .trimStart('.')
            .trim()
            .take(MAX_LENGTH)
            .trim()
        return cleaned.ifEmpty { defaultScanName(now) }
    }

    /** Nombre para mostrar de un archivo ajeno: sin caracteres de control y sin extensión. */
    fun displayTitle(raw: String): String {
        val noControl = raw.filterNot { it.isISOControl() }.trim()
        val dot = noControl.lastIndexOf('.')
        val base = if (dot > 0 && noControl.length - dot <= 6) noControl.substring(0, dot) else noControl
        return base.take(200).ifEmpty { "Documento" }
    }
}
