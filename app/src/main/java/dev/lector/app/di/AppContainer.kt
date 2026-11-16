package dev.lector.app.di

import android.app.Application
import dev.lector.app.core.crash.CrashRecorder
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.dispatch.DefaultAppDispatchers
import dev.lector.app.data.intake.CacheCleaner
import dev.lector.app.data.intake.ContentResolverUriSource
import dev.lector.app.data.intake.UriIntake
import dev.lector.app.data.pdf.FrameworkPdfEngine
import dev.lector.app.data.pdf.PdfBitmapCache
import dev.lector.app.data.pdf.PdfEngine
import dev.lector.app.data.recents.LectorDatabase
import dev.lector.app.data.recents.RoomRecentsRepository
import dev.lector.app.data.recents.ThumbnailRepository
import dev.lector.app.data.recents.ThumbnailStore
import dev.lector.app.data.settings.DataStoreSettingsRepository
import dev.lector.app.data.share.DocumentExporter
import dev.lector.app.data.share.FileProviderExporter
import dev.lector.app.domain.repo.RecentsRepository
import dev.lector.app.domain.repo.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Inyección de dependencias manual (PLAN §1.3): sin Hilt ni Koin. */
class AppContainer(private val app: Application) : AppServices {
    override val dispatchers: AppDispatchers = DefaultAppDispatchers()

    /** Ámbito de la aplicación para trabajo que debe sobrevivir a una pantalla (guardar progreso, cerrar sesiones). */
    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val crashRecorder = CrashRecorder(File(app.filesDir, "crash"))

    /** La limpieza de la caché de trabajo termina antes de que cualquier ingesta la toque. */
    val cacheReady: Job = appScope.launch(dispatchers.io) { CacheCleaner.cleanOnStart(app.cacheDir) }

    override val documents = DocumentRegistry()

    override val uriIntake = UriIntake(
        source = ContentResolverUriSource(app.contentResolver),
        cacheDir = app.cacheDir,
        dispatchers = dispatchers,
        awaitCacheReady = { cacheReady.join() },
    )

    override val bitmapCache = PdfBitmapCache()

    override val pdfEngine: PdfEngine = FrameworkPdfEngine(dispatchers, onMemoryPressure = { bitmapCache.evictAll() })

    override val exporter: DocumentExporter = FileProviderExporter(app, dispatchers)

    override val thumbnails: ThumbnailRepository = ThumbnailStore(File(app.filesDir, "thumbs"), dispatchers)

    private val database: LectorDatabase by lazy { LectorDatabase.build(app) }

    override val recents: RecentsRepository by lazy { RoomRecentsRepository(database.recentDao(), thumbnails, dispatchers) }

    override val settings: SettingsRepository by lazy { DataStoreSettingsRepository(app) }

    override val cacheDir: File get() = app.cacheDir
    override val filesDir: File get() = app.filesDir
}
