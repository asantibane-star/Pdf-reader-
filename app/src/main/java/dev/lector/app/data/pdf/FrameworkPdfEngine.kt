package dev.lector.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.FractionRect
import dev.lector.app.domain.model.PageSize
import dev.lector.app.domain.model.SearchHit
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Motor basado en `android.graphics.pdf.PdfRenderer`. Cada regla evita un crash conocido (PLAN §6.1):
 * un solo hilo, una página abierta a la vez, cierre ordenado y tope de memoria del bitmap.
 */
class FrameworkPdfEngine(
    private val dispatchers: AppDispatchers,
    /** Se invoca ante un `OutOfMemoryError` para que el llamador vacíe su caché. */
    private val onMemoryPressure: () -> Unit = {},
) : PdfEngine {

    override val supportsPassword: Boolean get() = Build.VERSION.SDK_INT >= PASSWORD_API

    override suspend fun open(file: File, password: String?): Outcome<PdfSession> = withContext(dispatchers.pdf) {
        if (!file.isFile) return@withContext Outcome.Failure(DocError.AccessLost)
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = createRenderer(pfd, password)
            if (renderer.pageCount <= 0) {
                closeQuietly(renderer, pfd)
                return@withContext Outcome.Failure(DocError.Corrupt)
            }
            Outcome.Success<PdfSession>(FrameworkPdfSession(pfd, renderer, dispatchers, onMemoryPressure))
        } catch (e: CancellationException) {
            closeQuietly(renderer, pfd)
            throw e
        } catch (_: SecurityException) {
            closeQuietly(renderer, pfd)
            Outcome.Failure(if (password != null) DocError.WrongPassword else DocError.PasswordRequired)
        } catch (_: IOException) {
            closeQuietly(renderer, pfd)
            Outcome.Failure(DocError.Corrupt)
        } catch (_: IllegalStateException) {
            closeQuietly(renderer, pfd)
            Outcome.Failure(DocError.Corrupt)
        } catch (_: IllegalArgumentException) {
            closeQuietly(renderer, pfd)
            Outcome.Failure(DocError.Corrupt)
        } catch (_: OutOfMemoryError) {
            closeQuietly(renderer, pfd)
            Outcome.Failure(DocError.OutOfMemory)
        }
    }

    private fun createRenderer(pfd: ParcelFileDescriptor, password: String?): PdfRenderer =
        if (password != null && Build.VERSION.SDK_INT >= PASSWORD_API) {
            createWithPassword(pfd, password)
        } else {
            PdfRenderer(pfd)
        }

    @RequiresApi(PASSWORD_API)
    private fun createWithPassword(pfd: ParcelFileDescriptor, password: String): PdfRenderer =
        PdfRenderer(pfd, LoadParams.Builder().setPassword(password).build())

    private fun closeQuietly(renderer: PdfRenderer?, pfd: ParcelFileDescriptor?) {
        try {
            renderer?.close()
        } catch (_: RuntimeException) {
            // ya cerrado
        }
        try {
            pfd?.close()
        } catch (_: IOException) {
            // ya cerrado
        }
    }

    companion object {
        const val PASSWORD_API = 35
        private const val MB = 1024L * 1024L
        private const val MAX_BITMAP_BYTES_CAP = 64L * MB

        /** `min(maxMemory/6, 64 MB)` (PLAN §6.1). */
        fun maxBitmapBytes(): Long = min(Runtime.getRuntime().maxMemory() / 6, MAX_BITMAP_BYTES_CAP)

        /** Dimensiones finales de un bitmap tras aplicar el tope de memoria, proporcionalmente y con mínimo 1×1. */
        fun cappedSize(width: Int, height: Int, maxBytes: Long): Pair<Int, Int> {
            val w = max(1, width)
            val h = max(1, height)
            val bytes = w.toLong() * h * BYTES_PER_PIXEL
            if (bytes <= maxBytes) return w to h
            val factor = sqrt(maxBytes.toDouble() / bytes)
            return max(1, (w * factor).toInt()) to max(1, (h * factor).toInt())
        }

        private const val BYTES_PER_PIXEL = 4
    }
}

private class FrameworkPdfSession(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val dispatchers: AppDispatchers,
    private val onMemoryPressure: () -> Unit,
) : PdfSession {
    override val pageCount: Int = renderer.pageCount
    override val canSearch: Boolean = Build.VERSION.SDK_INT >= FrameworkPdfEngine.PASSWORD_API

    private val closing = AtomicBoolean(false)
    @Volatile private var closed = false
    /** Tareas de render/búsqueda en vuelo: `close()` las cancela y espera antes de cerrar el renderer. */
    private val tasks = SupervisorJob()
    private val sizes = HashMap<Int, PageSize>() // solo se toca en el despachador `pdf`

    override suspend fun pageSize(index: Int): Outcome<PageSize> = onPdfThread {
        if (index !in 0 until pageCount) return@onPdfThread Outcome.Failure(DocError.Unknown)
        sizes[index]?.let { return@onPdfThread Outcome.Success(it) }
        try {
            renderer.openPage(index).use { page ->
                val size = PageSize(page.width.toFloat(), page.height.toFloat())
                sizes[index] = size
                Outcome.Success(size)
            }
        } catch (_: IllegalStateException) {
            Outcome.Failure(DocError.Corrupt)
        } catch (_: IllegalArgumentException) {
            Outcome.Failure(DocError.Corrupt)
        }
    }

    override suspend fun render(index: Int, widthPx: Int, heightPx: Int, region: RectF?): Outcome<Bitmap> = onPdfThread {
        renderLocked(index, widthPx, heightPx, region)
    }

    private fun renderLocked(index: Int, widthPx: Int, heightPx: Int, region: RectF?): Outcome<Bitmap> {
        if (index !in 0 until pageCount) return Outcome.Failure(DocError.Unknown)
        var scale = 1f
        for (attempt in 0 until 2) {
            try {
                return renderOnce(index, widthPx, heightPx, region, scale)
            } catch (_: OutOfMemoryError) {
                // Vaciar la caché y reintentar una vez a mitad de resolución.
                onMemoryPressure()
                scale *= 0.5f
            }
        }
        return Outcome.Failure(DocError.OutOfMemory)
    }

    private fun renderOnce(index: Int, widthPx: Int, heightPx: Int, region: RectF?, scale: Float): Outcome<Bitmap> {
        val left = (region?.left ?: 0f).coerceIn(0f, 1f)
        val top = (region?.top ?: 0f).coerceIn(0f, 1f)
        val right = (region?.right ?: 1f).coerceIn(left, 1f)
        val bottom = (region?.bottom ?: 1f).coerceIn(top, 1f)

        var fullW = max(1f, widthPx * scale)
        var fullH = max(1f, heightPx * scale)
        var bitmapW = max(1, ceil((right - left) * fullW).toInt())
        var bitmapH = max(1, ceil((bottom - top) * fullH).toInt())
        val (cappedW, cappedH) = FrameworkPdfEngine.cappedSize(bitmapW, bitmapH, FrameworkPdfEngine.maxBitmapBytes())
        if (cappedW != bitmapW || cappedH != bitmapH) {
            val factor = cappedW.toFloat() / bitmapW
            fullW *= factor
            fullH *= factor
            bitmapW = cappedW
            bitmapH = cappedH
        }

        val bitmap = createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        return try {
            renderer.openPage(index).use { page ->
                val matrix = Matrix().apply {
                    setScale(fullW / page.width, fullH / page.height)
                    postTranslate(-left * fullW, -top * fullH)
                }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            Outcome.Success(bitmap)
        } catch (_: IllegalStateException) {
            Outcome.Failure(DocError.Corrupt)
        } catch (_: IllegalArgumentException) {
            Outcome.Failure(DocError.Corrupt)
        }
    }

    override fun search(query: String): Flow<SearchHit> {
        if (!canSearch || query.isBlank()) return emptyFlow()
        return flow {
            for (page in 0 until pageCount) {
                val hits = searchPage(page, query)
                for (hit in hits) emit(hit)
            }
        }
    }

    private suspend fun searchPage(page: Int, query: String): List<SearchHit> {
        if (closed) return emptyList()
        return try {
            withContext(dispatchers.pdf + tasks) {
                if (closed || Build.VERSION.SDK_INT < FrameworkPdfEngine.PASSWORD_API) emptyList() else searchPageLocked(page, query)
            }
        } catch (e: CancellationException) {
            if (closed && currentCoroutineContext().isActive) emptyList() else throw e
        }
    }

    @RequiresApi(FrameworkPdfEngine.PASSWORD_API)
    private fun searchPageLocked(index: Int, query: String): List<SearchHit> = try {
        renderer.openPage(index).use { page ->
            val w = page.width.toFloat()
            val h = page.height.toFloat()
            if (w <= 0f || h <= 0f) {
                emptyList()
            } else {
                page.searchText(query).map { match ->
                    SearchHit(index, match.bounds.map { FractionRect(it.left / w, it.top / h, it.right / w, it.bottom / h) })
                }
            }
        }
    } catch (_: IllegalStateException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    /**
     * Cierre ordenado (PLAN §6.1): marcar `closed`, cancelar y esperar las tareas en vuelo, y por último
     * cerrar `PdfRenderer` y el descriptor, en ese orden, en el despachador `pdf`.
     */
    override suspend fun close() {
        if (!closing.compareAndSet(false, true)) return
        closed = true
        tasks.cancelAndJoin()
        withContext(NonCancellable + dispatchers.pdf) {
            try {
                renderer.close()
            } catch (_: RuntimeException) {
                // ya cerrado
            }
            try {
                pfd.close()
            } catch (_: IOException) {
                // ya cerrado
            }
        }
    }

    /** Ejecuta [block] en el hilo `pdf`; tras `close()` devuelve `Failure(Closed)` sin lanzar. */
    private suspend fun <T> onPdfThread(block: () -> Outcome<T>): Outcome<T> {
        if (closed) return Outcome.Failure(DocError.Closed)
        return try {
            withContext(dispatchers.pdf + tasks) {
                if (closed) Outcome.Failure(DocError.Closed) else block()
            }
        } catch (e: CancellationException) {
            // Cancelada por `close()` y no por el llamador: se informa como cerrada.
            if (closed && currentCoroutineContext().isActive) Outcome.Failure(DocError.Closed) else throw e
        }
    }
}
