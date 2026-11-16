package dev.lector.app.data.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache

/**
 * Caché LRU de páginas renderizadas, dimensionada por bytes. Nunca se llama a `recycle()`:
 * un bitmap reciclado que Compose aún dibuja provoca un crash; se deja al GC.
 */
class PdfBitmapCache(maxBytes: Long = Runtime.getRuntime().maxMemory() / 5) {
    data class Key(val docId: String, val page: Int, val width: Int, val height: Int, val region: Int)

    private val lru = object : LruCache<Key, Bitmap>(maxBytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: Key): Bitmap? = lru.get(key)

    fun put(key: Key, bitmap: Bitmap) {
        lru.put(key, bitmap)
    }

    fun evictAll() = lru.evictAll()

    /** Libera una parte de la caché (p. ej. con `onTrimMemory` moderado). */
    fun trimToHalf() = lru.trimToSize(lru.maxSize() / 2)

    companion object {
        fun regionHash(region: RectF?): Int = if (region == null) 0 else {
            var h = region.left.toBits()
            h = 31 * h + region.top.toBits()
            h = 31 * h + region.right.toBits()
            h = 31 * h + region.bottom.toBits()
            h
        }
    }
}
