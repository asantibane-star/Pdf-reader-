package dev.lector.app.data.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.withContext

/**
 * Prepara imágenes para el PDF (PLAN §8.3): decodifica con `inSampleSize` hasta ≤ 2 480 px de lado mayor,
 * aplica la rotación EXIF y recomprime a JPEG calidad 85.
 */
class ImageImport(private val context: Context, private val dispatchers: AppDispatchers) {

    /** [source] es un `content://` o la ruta de un archivo propio. */
    suspend fun toJpeg(source: String): Outcome<ByteArray> = withContext(dispatchers.io) {
        try {
            val bitmap = decode(source, MAX_SIDE) ?: return@withContext Outcome.Failure(DocError.Corrupt)
            val upright = upright(bitmap, orientation(source))
            val flat = flatten(upright)
            val out = ByteArrayOutputStream()
            if (!flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) return@withContext Outcome.Failure(DocError.Corrupt)
            Outcome.Success(out.toByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Outcome.Failure(DocError.AccessLost)
        } catch (_: SecurityException) {
            Outcome.Failure(DocError.AccessLost)
        } catch (_: IllegalArgumentException) {
            Outcome.Failure(DocError.Corrupt)
        } catch (_: OutOfMemoryError) {
            Outcome.Failure(DocError.OutOfMemory)
        }
    }

    /** Miniatura pequeña para la cuadrícula del modo simple. Null si no se puede leer. */
    suspend fun thumbnail(source: String, maxSide: Int): Bitmap? = withContext(dispatchers.io) {
        try {
            decode(source, maxSide)?.let { upright(it, orientation(source)) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Copia los bytes de una imagen de la galería a un archivo propio: sobrevive a permisos revocados. */
    suspend fun copyToFile(source: String, target: File): Boolean = withContext(dispatchers.io) {
        try {
            open(source)?.use { input -> target.outputStream().use { input.copyTo(it) } } != null
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            target.delete()
            false
        } catch (_: SecurityException) {
            target.delete()
            false
        }
    }

    private fun decode(source: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longSide = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open(source)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val longest = max(decoded.width, decoded.height)
        if (longest <= maxSide) return decoded
        val factor = maxSide.toFloat() / longest
        return decoded.scale(max(1, (decoded.width * factor).toInt()), max(1, (decoded.height * factor).toInt()))
    }

    private fun orientation(source: String): Int = try {
        open(source)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** JPEG no tiene transparencia: se pinta sobre blanco para que no salga negro. */
    private fun flatten(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flat = createBitmap(bitmap.width, bitmap.height)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return flat
    }

    private fun open(source: String): InputStream? =
        if (source.startsWith("content://")) context.contentResolver.openInputStream(source.toUri()) else File(source).inputStream()

    companion object {
        const val MAX_SIDE = 2_480
        const val JPEG_QUALITY = 85
    }
}
