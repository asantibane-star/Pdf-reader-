package dev.lector.app.ui.scan

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lector.app.LectorApp
import dev.lector.app.data.scan.ImageImport
import dev.lector.app.di.AppServices
import dev.lector.app.ui.services
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ScanPage(val id: String, val source: String)

/**
 * Modo simple (sin Google Play Services, PLAN §8.3): lista de páginas con cámara o galería, reordenable y
 * eliminable. Las imágenes de la galería se copian a un archivo propio para sobrevivir a permisos revocados.
 */
class FallbackScanViewModel(
    private val savedState: SavedStateHandle,
    private val services: AppServices,
    private val images: ImageImport,
) : ViewModel() {
    private val _pages = MutableStateFlow(restore())
    val pages: StateFlow<List<ScanPage>> = _pages.asStateFlow()

    private val thumbs = object : LruCache<String, Bitmap>(THUMB_CACHE_ENTRIES) {}

    private fun cameraDir() = File(services.cacheDir, "camera").apply { mkdirs() }

    /** Páginas guardadas en `SavedStateHandle`; se descartan las que ya no existen (caché limpiada). */
    private fun restore(): List<ScanPage> =
        savedState.get<ArrayList<String>>(KEY_PAGES).orEmpty().mapNotNull { entry ->
            val id = entry.substringBefore('|')
            val source = entry.substringAfter('|', "")
            if (source.isNotEmpty() && File(source).isFile) ScanPage(id, source) else null
        }

    private fun publish(pages: List<ScanPage>) {
        _pages.value = pages
        savedState[KEY_PAGES] = ArrayList(pages.map { "${it.id}|${it.source}" })
    }

    /** Archivo destino de la foto; su ruta se guarda porque `TakePicture` solo devuelve un booleano. */
    fun prepareCameraFile(): File? {
        if (_pages.value.size >= MAX_PAGES) return null
        val file = File(cameraDir(), "${UUID.randomUUID()}.jpg")
        savedState[KEY_CAMERA] = file.absolutePath
        return file
    }

    fun onCameraResult(success: Boolean) {
        val path = savedState.get<String>(KEY_CAMERA) ?: return
        savedState.remove<String>(KEY_CAMERA)
        val file = File(path)
        if (success && file.length() > 0L && _pages.value.size < MAX_PAGES) {
            publish(_pages.value + ScanPage(UUID.randomUUID().toString(), path))
        } else {
            file.delete()
        }
    }

    fun addFromGallery(uris: List<String>) {
        val room = MAX_PAGES - _pages.value.size
        if (room <= 0 || uris.isEmpty()) return
        viewModelScope.launch {
            for (uri in uris.take(room)) {
                val target = File(cameraDir(), "${UUID.randomUUID()}.img")
                if (images.copyToFile(uri, target)) publish(_pages.value + ScanPage(UUID.randomUUID().toString(), target.absolutePath))
            }
        }
    }

    fun remove(id: String) {
        _pages.value.firstOrNull { it.id == id }?.let { File(it.source).delete() }
        publish(_pages.value.filterNot { it.id == id })
    }

    /** Mueve la página de [from] a [to] (arrastrar para reordenar). */
    fun move(from: Int, to: Int) {
        val list = _pages.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        publish(list)
    }

    suspend fun thumbnail(page: ScanPage): Bitmap? {
        thumbs.get(page.id)?.let { return it }
        return images.thumbnail(page.source, THUMB_SIDE_PX)?.also { thumbs.put(page.id, it) }
    }

    fun sources(): List<String> = _pages.value.map { it.source }

    override fun onCleared() {
        // Las copias de las páginas viven en `cache/camera/`: se borran al salir de la pantalla.
        _pages.value.forEach { File(it.source).delete() }
        _pages.update { emptyList() }
    }

    companion object {
        const val MAX_PAGES = 50
        private const val KEY_PAGES = "scanPages"
        private const val KEY_CAMERA = "cameraFile"
        private const val THUMB_SIDE_PX = 480
        private const val THUMB_CACHE_ENTRIES = 24

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as LectorApp
                FallbackScanViewModel(createSavedStateHandle(), services(), app.container.images)
            }
        }
    }
}
