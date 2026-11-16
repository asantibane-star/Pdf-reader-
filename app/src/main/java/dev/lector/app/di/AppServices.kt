package dev.lector.app.di

import dev.lector.app.core.crash.CrashRecorder
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.data.intake.UriIntake
import dev.lector.app.data.pdf.PdfBitmapCache
import dev.lector.app.data.pdf.PdfEngine
import dev.lector.app.data.recents.ThumbnailRepository
import dev.lector.app.data.share.DocumentExporter
import dev.lector.app.domain.repo.RecentsRepository
import dev.lector.app.domain.repo.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** Lo que necesitan los ViewModels. Las pruebas lo implementan con falsos. */
interface AppServices {
    val crashRecorder: CrashRecorder
    val dispatchers: AppDispatchers
    val appScope: CoroutineScope
    val documents: DocumentRegistry
    val uriIntake: UriIntake
    val pdfEngine: PdfEngine
    val bitmapCache: PdfBitmapCache
    val exporter: DocumentExporter
    val thumbnails: ThumbnailRepository
    val recents: RecentsRepository
    val settings: SettingsRepository
    val cacheDir: File
    val filesDir: File
}
