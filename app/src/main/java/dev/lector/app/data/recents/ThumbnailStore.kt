package dev.lector.app.data.recents

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.lector.app.core.dispatch.AppDispatchers
import java.io.File
import java.io.IOException
import kotlinx.coroutines.withContext

interface ThumbnailRepository {
    suspend fun save(id: Long, bitmap: Bitmap): Boolean
    suspend fun load(id: Long): Bitmap?
    suspend fun delete(id: Long): Boolean
    suspend fun deleteAll(): Boolean
    suspend fun retain(ids: Set<Long>)
}

/** Miniaturas de la primera página en `filesDir/thumbs/<id>.jpg` (JPEG 80, lado mayor 264 px). */
class ThumbnailStore(private val dir: File, private val dispatchers: AppDispatchers) : ThumbnailRepository {
    private fun file(id: Long) = File(dir, "$id.jpg")

    override suspend fun save(id: Long, bitmap: Bitmap): Boolean = withContext(dispatchers.io) {
        try {
            dir.mkdirs()
            val target = file(id)
            val tmp = File(dir, "$id.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            tmp.renameTo(target)
        } catch (_: IOException) {
            false
        }
    }

    override suspend fun load(id: Long): Bitmap? = withContext(dispatchers.io) {
        val f = file(id)
        if (f.isFile) BitmapFactory.decodeFile(f.path) else null
    }

    override suspend fun delete(id: Long): Boolean = withContext(dispatchers.io) { file(id).delete() }

    override suspend fun deleteAll(): Boolean = withContext(dispatchers.io) { dir.deleteRecursively() }

    /** Borra miniaturas de filas que ya no existen. */
    override suspend fun retain(ids: Set<Long>): Unit = withContext(dispatchers.io) {
        dir.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension.toLongOrNull()
            if (id == null || id !in ids) f.delete()
        }
    }

    companion object {
        const val QUALITY = 80
        const val LONG_SIDE_PX = 264
    }
}
