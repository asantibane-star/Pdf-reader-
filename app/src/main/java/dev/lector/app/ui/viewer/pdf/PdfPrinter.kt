package dev.lector.app.ui.viewer.pdf

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

/** Imprime un PDF ya en caché. Los errores se notifican con `onWriteFailed`; nunca lanzan. */
object PdfPrinter {
    fun print(context: Context, path: String, jobName: String): Boolean {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        return try {
            manager.print(jobName, FilePrintAdapter(File(path), jobName), null)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

private class FilePrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        executor.execute {
            val failed = try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            if (cancellationSignal?.isCanceled == true) return@execute postCancelled(callback)
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                    }
                }
                false
            } catch (_: IOException) {
                true
            }
            main.post {
                if (failed) callback.onWriteFailed(null) else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }
    }

    override fun onFinish() {
        executor.shutdown()
    }

    private fun postCancelled(callback: WriteResultCallback) {
        main.post { callback.onWriteCancelled() }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
