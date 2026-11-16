package dev.lector.app.data.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** PDF temporal que entrega ML Kit: hay que copiarlo de inmediato. */
data class ScannerPdf(val uri: String, val pageCount: Int)

/** Camino principal del escáner (PLAN §8.1): ML Kit Document Scanner, que corre en Google Play Services. */
class MlKitScanner {
    fun isAvailable(context: Context): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (_: RuntimeException) {
        false
    }

    /** Intent del escáner o `ScannerUnavailable` (entonces se usa el modo simple). */
    suspend fun startIntent(activity: Activity): Outcome<IntentSender> {
        if (!isAvailable(activity)) return Outcome.Failure(DocError.ScannerUnavailable)
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(PAGE_LIMIT)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .build()
        return suspendCancellableCoroutine { continuation ->
            try {
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender -> if (continuation.isActive) continuation.resume(Outcome.Success(sender)) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(Outcome.Failure(DocError.ScannerUnavailable)) }
            } catch (_: RuntimeException) {
                if (continuation.isActive) continuation.resume(Outcome.Failure(DocError.ScannerUnavailable))
            }
        }
    }

    fun resultPdf(data: Intent?): ScannerPdf? {
        val result = try {
            GmsDocumentScanningResult.fromActivityResultIntent(data)
        } catch (_: RuntimeException) {
            null
        }
        val pdf = result?.pdf ?: return null
        return ScannerPdf(pdf.uri.toString(), pdf.pageCount)
    }

    companion object {
        const val PAGE_LIMIT = 50
    }
}
