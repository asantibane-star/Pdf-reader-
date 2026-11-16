package dev.lector.app.testing

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import dev.lector.app.core.crash.CrashRecorder
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.data.intake.UriIntake
import dev.lector.app.data.intake.UriSource
import dev.lector.app.data.pdf.PdfBitmapCache
import dev.lector.app.data.pdf.PdfEngine
import dev.lector.app.data.pdf.PdfSession
import dev.lector.app.data.recents.ThumbnailRepository
import dev.lector.app.data.share.DocumentExporter
import dev.lector.app.di.AppServices
import dev.lector.app.di.DocumentRegistry
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.domain.model.PageSize
import dev.lector.app.domain.model.RecentDocument
import dev.lector.app.domain.model.SearchHit
import dev.lector.app.domain.repo.RecentsRepository
import dev.lector.app.domain.repo.SettingsRepository
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** `content://` → bytes. Una entrada ausente simula acceso revocado. */
class FakeUriSource(private val files: MutableMap<String, ByteArray> = mutableMapOf()) : UriSource {
    var opens = 0
    var delayHook: (() -> Unit)? = null

    fun put(uri: String, bytes: ByteArray) {
        files[uri] = bytes
    }

    override fun open(uri: String): InputStream {
        opens++
        delayHook?.invoke()
        return (files[uri] ?: throw FileNotFoundException(uri)).inputStream()
    }

    override fun displayName(uri: String): String? = "Documento.pdf"
    override fun declaredSize(uri: String): Long? = files[uri]?.size?.toLong()
}

class FakeRecents : RecentsRepository {
    private val rows = MutableStateFlow<List<RecentDocument>>(emptyList())
    private var nextId = 1L
    val progress = mutableListOf<Triple<Long, Int, Int>>()
    val removed = mutableListOf<Long>()

    override fun observe(): Flow<List<RecentDocument>> = rows.map { it.sortedByDescending { r -> r.lastOpenedAt } }

    override suspend fun record(ref: DocumentRef, now: Long): Outcome<RecentDocument> {
        val existing = rows.value.firstOrNull { it.uri == ref.sourceUri }
        val row = existing?.copy(displayName = ref.displayName, lastOpenedAt = now) ?: RecentDocument(
            id = nextId++, uri = ref.sourceUri, displayName = ref.displayName, kind = ref.kind, sizeBytes = ref.sizeBytes,
            lastOpenedAt = now, lastPage = 0, pageCount = 0,
        )
        rows.value = rows.value.filterNot { it.uri == row.uri } + row
        return Outcome.Success(row)
    }

    fun seed(uri: String, lastPage: Int, pageCount: Int): RecentDocument {
        val row = RecentDocument(nextId++, uri, "Doc", dev.lector.app.domain.model.DocumentKind.Pdf, 10, 1, lastPage, pageCount)
        rows.value = rows.value + row
        return row
    }

    override suspend fun updateProgress(id: Long, page: Int, pageCount: Int): Outcome<Unit> {
        progress += Triple(id, page, pageCount)
        rows.value = rows.value.map { if (it.id == id) it.copy(lastPage = page, pageCount = pageCount) else it }
        return Outcome.Success(Unit)
    }

    override suspend fun remove(id: Long): Outcome<Unit> {
        removed += id
        rows.value = rows.value.filterNot { it.id == id }
        return Outcome.Success(Unit)
    }

    override suspend fun restore(recent: RecentDocument): Outcome<Unit> {
        rows.value = rows.value + recent
        return Outcome.Success(Unit)
    }

    override suspend fun clear(): Outcome<Unit> {
        rows.value = emptyList()
        return Outcome.Success(Unit)
    }

    override suspend fun find(id: Long): RecentDocument? = rows.value.firstOrNull { it.id == id }
}

class FakeSettings : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings): Outcome<Unit> {
        state.value = transform(state.value)
        return Outcome.Success(Unit)
    }
}

class FakeThumbnails : ThumbnailRepository {
    val saved = mutableListOf<Long>()
    override suspend fun save(id: Long, bitmap: Bitmap): Boolean { saved += id; return true }
    override suspend fun load(id: Long): Bitmap? = null
    override suspend fun delete(id: Long) = true
    override suspend fun deleteAll() = true
    override suspend fun retain(ids: Set<Long>) = Unit
}

class FakeExporter : DocumentExporter {
    var shares = 0
    var copies = 0
    override suspend fun prepareShare(sourcePath: String, displayName: String, extension: String): Outcome<Uri> {
        shares++
        delay(100)
        return Outcome.Success(Uri.parse("content://dev.lector.app.fileprovider/share/x.$extension"))
    }

    override suspend fun copyTo(sourcePath: String, destination: Uri): Outcome<Unit> {
        copies++
        return Outcome.Success(Unit)
    }
}

class FakePdfSession(override val pageCount: Int = 20) : PdfSession {
    var closed = false
    override val canSearch = false
    override suspend fun pageSize(index: Int): Outcome<PageSize> = Outcome.Success(PageSize.A4)
    override suspend fun render(index: Int, widthPx: Int, heightPx: Int, region: RectF?): Outcome<Bitmap> = Outcome.Failure(DocError.Closed)
    override fun search(query: String): Flow<SearchHit> = emptyFlow()
    override suspend fun close() {
        closed = true
    }
}

class FakePdfEngine(
    var supportsPasswordValue: Boolean = true,
    var requiredPassword: String? = null,
    var pageCount: Int = 20,
    var failure: DocError? = null,
) : PdfEngine {
    override val supportsPassword: Boolean get() = supportsPasswordValue
    val sessions = mutableListOf<FakePdfSession>()
    val opens = mutableListOf<String?>()

    override suspend fun open(file: File, password: String?): Outcome<PdfSession> {
        opens += password
        failure?.let { return Outcome.Failure(it) }
        val required = requiredPassword
        if (required != null) {
            if (password == null) return Outcome.Failure(DocError.PasswordRequired)
            if (password != required) return Outcome.Failure(DocError.WrongPassword)
        }
        return Outcome.Success(FakePdfSession(pageCount).also { sessions += it })
    }
}

class FakeServices(
    dispatchers: AppDispatchers,
    appScope: CoroutineScope,
    val source: FakeUriSource = FakeUriSource(),
    override val pdfEngine: FakePdfEngine = FakePdfEngine(),
) : AppServices {
    override val dispatchers: AppDispatchers = dispatchers
    override val appScope: CoroutineScope = appScope
    override val documents = DocumentRegistry()
    override val cacheDir: File = Files.createTempDirectory("cache").toFile()
    override val filesDir: File = Files.createTempDirectory("files").toFile()
    override val uriIntake = UriIntake(source, cacheDir, dispatchers, freeSpace = { Long.MAX_VALUE })
    override val bitmapCache = PdfBitmapCache(1_000_000)
    override val exporter = FakeExporter()
    override val thumbnails = FakeThumbnails()
    override val recents = FakeRecents()
    override val settings = FakeSettings()
    override val crashRecorder = CrashRecorder(File(filesDir, "crash"))
    override val scans: dev.lector.app.domain.repo.ScanRepository = FakeScans()
    override val scanner = dev.lector.app.data.scan.MlKitScanner()
}

class FakeScans : dev.lector.app.domain.repo.ScanRepository {
    val pendingList = mutableListOf<dev.lector.app.domain.model.PendingScan>()
    var saveResult: Outcome<String> = Outcome.Success("content://media/external_primary/file/1")
    val saved = mutableListOf<Pair<String, String>>()
    val discarded = mutableListOf<String>()
    var saveCalls = 0

    override suspend fun importScannerPdf(uri: String, pageCount: Int): Outcome<dev.lector.app.domain.model.PendingScan> {
        val p = dev.lector.app.domain.model.PendingScan("p-${pendingList.size + 1}", "/tmp/p.pdf", pageCount)
        pendingList += p
        return Outcome.Success(p)
    }

    override suspend fun buildFromImages(sources: List<String>, onProgress: (Int, Int) -> Unit): Outcome<dev.lector.app.domain.model.PendingScan> {
        sources.indices.forEach { onProgress(it + 1, sources.size) }
        return importScannerPdf("", sources.size)
    }

    override suspend fun saveToDocuments(pending: dev.lector.app.domain.model.PendingScan, name: String): Outcome<String> {
        saveCalls++
        if (saveResult is Outcome.Success) saved += pending.id to name
        return saveResult
    }

    override suspend fun saveTo(pending: dev.lector.app.domain.model.PendingScan, destination: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun discard(id: String) {
        discarded += id
        pendingList.removeAll { it.id == id }
    }

    override suspend fun pending(): List<dev.lector.app.domain.model.PendingScan> = pendingList.toList()
}
