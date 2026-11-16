package dev.lector.app.data.intake

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

/** Acceso a un `content://`. Abstraído para poder probar `UriIntake` sin Android. */
interface UriSource {
    /** Abre el flujo o lanza `FileNotFoundException` / `SecurityException`. */
    fun open(uri: String): InputStream
    fun displayName(uri: String): String?
    fun declaredSize(uri: String): Long?
}

class ContentResolverUriSource(private val resolver: ContentResolver) : UriSource {
    override fun open(uri: String): InputStream =
        resolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException()

    override fun displayName(uri: String): String? = queryColumn(uri, OpenableColumns.DISPLAY_NAME) { it.getString(0) }
        ?: uri.toUri().lastPathSegment

    override fun declaredSize(uri: String): Long? =
        queryColumn(uri, OpenableColumns.SIZE) { if (it.isNull(0)) null else it.getLong(0) }?.takeIf { it >= 0 }

    private fun <T> queryColumn(uri: String, column: String, read: (android.database.Cursor) -> T?): T? = try {
        resolver.query(uri.toUri(), arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) read(cursor) else null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }
}
