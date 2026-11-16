package dev.lector.app.ui.common

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.lector.app.R
import dev.lector.app.core.result.DocError
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Mensaje claro y sin tecnicismos de cada error (PLAN §9, DESIGN §7). */
@StringRes
fun DocError.messageRes(passwordSupported: Boolean = true): Int = when (this) {
    DocError.UnsupportedSource -> R.string.err_unsupported_source
    DocError.UnsupportedFormat -> R.string.err_unsupported
    DocError.LegacyOfficeFormat -> R.string.err_legacy_office
    DocError.TooLarge -> R.string.err_too_large
    DocError.NoSpace -> R.string.err_no_space
    DocError.AccessLost -> R.string.err_access_lost
    DocError.Corrupt -> R.string.err_corrupt
    DocError.PasswordRequired -> if (passwordSupported) R.string.err_password_required else R.string.err_password_unsupported
    DocError.WrongPassword -> R.string.err_password_wrong
    DocError.ZipBomb -> R.string.err_zip_bomb
    DocError.RenderTimeout -> R.string.err_render_timeout
    DocError.RendererCrashed -> R.string.err_renderer_crashed
    DocError.WebViewUnavailable -> R.string.err_webview
    DocError.OutOfMemory -> R.string.err_out_of_memory
    DocError.ConverterUnavailable -> R.string.err_converter_unavailable
    DocError.ConverterCrashed -> R.string.err_converter_crashed
    DocError.ConversionTimeout -> R.string.err_conversion_timeout
    DocError.ScannerUnavailable -> R.string.err_scanner
    DocError.SaveFailed -> R.string.err_save
    DocError.Closed -> R.string.err_closed
    DocError.Unknown -> R.string.err_unknown
}

/** "Hace 2 h", "Ayer", "12 mar 2026". */
@Composable
fun relativeTime(timestamp: Long, now: Long): String {
    val context = LocalContext.current
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff).toInt()
    val hours = TimeUnit.MILLISECONDS.toHours(diff).toInt()
    val days = TimeUnit.MILLISECONDS.toDays(diff).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.time_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes)
        hours < 24 -> stringResource(R.string.time_hours_ago, hours)
        days < 2 -> stringResource(R.string.time_yesterday)
        days < 7 -> weekdayName(timestamp)
        else -> DateFormat.getMediumDateFormat(context).format(Date(timestamp))
    }
}

fun fileSizeText(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

/** "Martes", "Lunes": nombre completo del día, en el idioma de la interfaz. */
private fun weekdayName(timestamp: Long): String {
    val day = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).dayOfWeek
    val locale = Locale.getDefault()
    return day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
}
