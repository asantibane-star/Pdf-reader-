package dev.lector.app.data.recents

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Solo lo mínimo (PLAN §4.5): URI, nombre visible, tipo, tamaño, fecha, última página y total de páginas. */
@Entity(tableName = "recent", indices = [Index("uri", unique = true)])
data class RecentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val kind: String,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
    val lastPage: Int = 0,
    val pageCount: Int = 0,
)
