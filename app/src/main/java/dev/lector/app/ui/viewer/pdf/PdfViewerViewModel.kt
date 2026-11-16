package dev.lector.app.ui.viewer.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lector.app.R
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.data.intake.CacheCleaner
import dev.lector.app.data.pdf.PdfBitmapCache
import dev.lector.app.data.pdf.PdfSession
import dev.lector.app.data.recents.ThumbnailStore
import dev.lector.app.di.AppServices
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.domain.model.PageSize
import dev.lector.app.domain.model.SearchHit
import dev.lector.app.ui.services
import dev.lector.app.ui.viewer.DocumentResolver
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Relaciones de aspecto de las páginas (0 = aún desconocida). Se usa la de la página 0 hasta conocerlas. */
@Immutable
class PageAspects(private val ratios: FloatArray) {
    val count: Int get() = ratios.size

    fun aspect(index: Int): Float {
        val own = ratios.getOrElse(index) { 0f }
        if (own > 0f) return own
        val first = ratios.firstOrNull() ?: 0f
        return if (first > 0f) first else PageSize.DEFAULT_ASPECT
    }

    companion object {
        val Empty = PageAspects(FloatArray(0))
    }
}

@Immutable
data class SearchState(
    val active: Boolean = false,
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val current: Int = -1,
    val searching: Boolean = false,
)

sealed interface PdfUiState {
    data object Loading : PdfUiState

    data class Content(
        val title: String,
        val pageCount: Int,
        val canSearch: Boolean,
        val initialPage: Int,
        val initialZoom: Float,
    ) : PdfUiState

    data class PasswordNeeded(val wrong: Boolean) : PdfUiState

    data class Error(val error: DocError, val passwordSupported: Boolean) : PdfUiState
}

sealed interface PdfEffect {
    /** [yFraction] posiciona el resultado dentro de la página (0 = arriba). */
    data class ScrollToPage(val page: Int, val yFraction: Float = 0f) : PdfEffect
    data class Snack(@StringRes val messageRes: Int) : PdfEffect
    data class Share(val uri: Uri) : PdfEffect
    data class Print(val path: String, val jobName: String) : PdfEffect
}

class PdfViewerViewModel(
    private val savedState: SavedStateHandle,
    private val services: AppServices,
) : ViewModel() {

    private var docId: String = savedState.get<String>(KEY_DOC_ID).orEmpty()
    private var ref: DocumentRef? = null
    private var session: PdfSession? = null
    private var recentId: Long? = null
    private var loadJob: Job? = null
    private var aspectsJob: Job? = null
    private var searchJob: Job? = null

    private val _state = MutableStateFlow<PdfUiState>(PdfUiState.Loading)
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    private val _aspects = MutableStateFlow(PageAspects.Empty)
    val aspects: StateFlow<PageAspects> = _aspects.asStateFlow()

    private val _page = MutableStateFlow(savedState.get<Int>(KEY_PAGE) ?: 0)
    val page: StateFlow<Int> = _page.asStateFlow()

    private val _zoom = MutableStateFlow(savedState.get<Float>(KEY_ZOOM) ?: 1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _night = MutableStateFlow(savedState.get<Boolean>(KEY_NIGHT) ?: false)
    val night: StateFlow<Boolean> = _night.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** `true` mientras compartir/guardar/imprimir están en curso: protege contra el doble toque. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val effects = Channel<PdfEffect>(Channel.BUFFERED)
    val effectFlow: Flow<PdfEffect> = effects.receiveAsFlow()

    val title: String get() = ref?.displayName ?: savedState.get<String>(KEY_NAME).orEmpty()

    /** `content://` de origen, para "Abrir con otra app". */
    val sourceUri: String? get() = ref?.sourceUri ?: savedState.get<String>(KEY_SOURCE_URI)

    init {
        load(password = null)
        @OptIn(FlowPreview::class)
        _page.debounce(PROGRESS_DEBOUNCE_MS).onEach { persistProgress() }.launchIn(viewModelScope)
    }

    // ---------------------------------------------------------------- carga y restauración

    fun retry() = load(password = null)

    fun submitPassword(password: String) = load(password)

    private fun load(password: String?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = PdfUiState.Loading
            val resolved = resolveRef()
            if (resolved is Outcome.Failure) {
                _state.value = PdfUiState.Error(resolved.error, services.pdfEngine.supportsPassword)
                return@launch
            }
            val document = (resolved as Outcome.Success).value
            ref = document
            savedState[KEY_DOC_ID] = document.id
            savedState[KEY_SOURCE_URI] = document.sourceUri
            savedState[KEY_NAME] = document.displayName
            docId = document.id

            when (val opened = services.pdfEngine.open(File(document.cachePath), password)) {
                is Outcome.Failure -> _state.value = when (opened.error) {
                    DocError.PasswordRequired ->
                        if (services.pdfEngine.supportsPassword) PdfUiState.PasswordNeeded(wrong = false)
                        else PdfUiState.Error(DocError.PasswordRequired, passwordSupported = false)
                    DocError.WrongPassword -> PdfUiState.PasswordNeeded(wrong = true)
                    else -> PdfUiState.Error(opened.error, services.pdfEngine.supportsPassword)
                }
                is Outcome.Success -> attach(document, opened.value)
            }
        }
    }

    private suspend fun resolveRef(): Outcome<DocumentRef> =
        DocumentResolver(services).resolve(docId, savedState.get<String>(KEY_SOURCE_URI))

    private suspend fun attach(document: DocumentRef, opened: PdfSession) {
        session?.close()
        session = opened
        val recent = (services.recents.record(document) as? Outcome.Success)?.value
        recentId = recent?.id
        val last = opened.pageCount - 1
        val saved = savedState.get<Int>(KEY_PAGE)
        val initialPage = (saved ?: recent?.lastPage ?: 0).coerceIn(0, last)
        _page.value = initialPage
        savedState[KEY_PAGE] = initialPage
        recent?.let { services.recents.updateProgress(it.id, initialPage, opened.pageCount) }
        _aspects.value = PageAspects(FloatArray(opened.pageCount))
        startAspectLoading(opened)
        _state.value = PdfUiState.Content(
            title = document.displayName,
            pageCount = opened.pageCount,
            canSearch = opened.canSearch,
            initialPage = initialPage,
            initialZoom = _zoom.value,
        )
    }

    private fun startAspectLoading(opened: PdfSession) {
        aspectsJob?.cancel()
        aspectsJob = viewModelScope.launch {
            val ratios = FloatArray(opened.pageCount)
            var start = 0
            while (start < ratios.size) {
                val end = min(start + ASPECT_BLOCK, ratios.size)
                for (index in start until end) {
                    when (val size = opened.pageSize(index)) {
                        is Outcome.Success -> ratios[index] = size.value.aspectRatio
                        is Outcome.Failure -> if (size.error == DocError.Closed) return@launch
                    }
                }
                _aspects.value = PageAspects(ratios.copyOf())
                start = end
            }
        }
    }

    // ---------------------------------------------------------------- páginas y caché

    fun cachedBitmap(index: Int, widthPx: Int, heightPx: Int, region: RectF? = null): Bitmap? =
        services.bitmapCache.get(key(index, widthPx, heightPx, region))

    /** Renderiza (o recupera de la caché) una página o una región de ella. Devuelve null si falla. */
    suspend fun renderPage(index: Int, widthPx: Int, heightPx: Int, region: RectF? = null): Bitmap? {
        val cache = services.bitmapCache
        val k = key(index, widthPx, heightPx, region)
        cache.get(k)?.let { return it }
        val current = session ?: return null
        return when (val result = current.render(index, widthPx, heightPx, region)) {
            is Outcome.Success -> result.value.also { cache.put(k, it) }
            is Outcome.Failure -> null
        }
    }

    private fun key(index: Int, w: Int, h: Int, region: RectF?) =
        PdfBitmapCache.Key(docId, index, w, h, PdfBitmapCache.regionHash(region))

    fun onPageChanged(index: Int) {
        if (_page.value == index) return
        _page.value = index
        savedState[KEY_PAGE] = index
    }

    /** Salto pedido por el usuario (deslizador, "Ir a página", resultado de búsqueda). */
    fun requestPage(index: Int, yFraction: Float = 0f) {
        val count = (state.value as? PdfUiState.Content)?.pageCount ?: return
        val target = index.coerceIn(0, count - 1)
        onPageChanged(target)
        effects.trySend(PdfEffect.ScrollToPage(target, yFraction))
    }

    fun onZoomChanged(zoom: Float) {
        _zoom.value = zoom
        savedState[KEY_ZOOM] = zoom
    }

    fun toggleNight() {
        val next = !_night.value
        _night.value = next
        savedState[KEY_NIGHT] = next
    }

    // ---------------------------------------------------------------- búsqueda

    fun openSearch() = _search.update { it.copy(active = true) }

    fun closeSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        _search.update { it.copy(query = query, hits = emptyList(), current = -1, searching = query.isNotBlank()) }
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(query)
        }
    }

    private suspend fun runSearch(query: String) {
        val current = session ?: return
        val found = ArrayList<SearchHit>()
        var published = 0
        current.search(query).collect { hit ->
            found += hit
            if (found.size == 1) {
                _search.update { it.copy(hits = found.toList(), current = 0) }
                published = 1
                requestPage(hit.page, hit.bounds.firstOrNull()?.top ?: 0f)
            } else if (found.size - published >= SEARCH_PUBLISH_BATCH) {
                _search.update { it.copy(hits = found.toList()) }
                published = found.size
            }
        }
        _search.update { it.copy(hits = found.toList(), searching = false) }
    }

    fun nextHit() = moveHit(+1)

    fun previousHit() = moveHit(-1)

    private fun moveHit(step: Int) {
        val s = _search.value
        if (s.hits.isEmpty()) return
        val next = ((if (s.current < 0) 0 else s.current + step) + s.hits.size) % s.hits.size
        _search.update { it.copy(current = next) }
        val hit = s.hits[next]
        requestPage(hit.page, hit.bounds.firstOrNull()?.top ?: 0f)
    }

    // ---------------------------------------------------------------- compartir, guardar, imprimir

    fun share() = runExclusive {
        val document = ref ?: return@runExclusive
        when (val uri = services.exporter.prepareShare(document.cachePath, document.displayName, "pdf")) {
            is Outcome.Success -> effects.send(PdfEffect.Share(uri.value))
            is Outcome.Failure -> effects.send(PdfEffect.Snack(R.string.err_save))
        }
    }

    fun saveCopy(destination: Uri) = runExclusive {
        val document = ref ?: return@runExclusive
        val result = services.exporter.copyTo(document.cachePath, destination)
        effects.send(PdfEffect.Snack(if (result is Outcome.Success) R.string.viewer_saved_copy else R.string.err_save))
    }

    fun print() {
        val document = ref ?: return
        effects.trySend(PdfEffect.Print(document.cachePath, document.displayName))
    }

    private fun runExclusive(block: suspend () -> Unit) {
        if (!_busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    // ---------------------------------------------------------------- ciclo de vida

    private suspend fun persistProgress() {
        val id = recentId ?: return
        val count = (state.value as? PdfUiState.Content)?.pageCount ?: return
        services.recents.updateProgress(id, _page.value, count)
    }

    override fun onCleared() {
        // viewModelScope ya está cancelado: el cierre ordenado vive en el ámbito de la aplicación.
        val closing = session
        val document = ref
        val id = recentId
        val lastPage = _page.value
        val count = (state.value as? PdfUiState.Content)?.pageCount ?: 0
        session = null
        services.appScope.launch {
            if (id != null && count > 0) services.recents.updateProgress(id, lastPage, count)
            if (closing != null && id != null) saveThumbnail(closing, id)
            closing?.close()
            document?.let {
                CacheCleaner.deleteOpenFile(services.cacheDir, it.cachePath)
                services.documents.remove(it.id)
            }
        }
    }

    private suspend fun saveThumbnail(closing: PdfSession, id: Long) {
        val size = (closing.pageSize(0) as? Outcome.Success)?.value ?: return
        val ratio = size.aspectRatio
        val long = ThumbnailStore.LONG_SIDE_PX
        val (w, h) = if (ratio >= 1f) long to (long / ratio).roundToInt() else (long * ratio).roundToInt() to long
        val bitmap = (closing.render(0, w, h) as? Outcome.Success)?.value ?: return
        services.thumbnails.save(id, bitmap)
    }

    companion object {
        const val KEY_DOC_ID = "docId"
        const val KEY_SOURCE_URI = "sourceUri"
        const val KEY_NAME = "displayName"
        const val KEY_PAGE = "page"
        const val KEY_ZOOM = "zoom"
        const val KEY_NIGHT = "night"
        const val PROGRESS_DEBOUNCE_MS = 1_000L
        private const val ASPECT_BLOCK = 50
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val SEARCH_PUBLISH_BATCH = 25

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PdfViewerViewModel(createSavedStateHandle(), services()) }
        }
    }
}
