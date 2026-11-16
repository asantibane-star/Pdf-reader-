package dev.lector.app.data.recents

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecentDao {
    @Query("SELECT * FROM recent ORDER BY lastOpenedAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<RecentEntity>>

    @Query("SELECT * FROM recent WHERE uri = :uri LIMIT 1")
    abstract suspend fun findByUri(uri: String): RecentEntity?

    @Query("SELECT * FROM recent WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: Long): RecentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: RecentEntity): Long

    @Update
    abstract suspend fun update(entity: RecentEntity)

    @Query("UPDATE recent SET lastPage = :page, pageCount = :pageCount WHERE id = :id")
    abstract suspend fun updateProgress(id: Long, page: Int, pageCount: Int)

    @Query("DELETE FROM recent WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM recent")
    abstract suspend fun deleteAll()

    @Query("SELECT id FROM recent ORDER BY lastOpenedAt DESC, id DESC LIMIT -1 OFFSET :keep")
    abstract suspend fun idsBeyond(keep: Int): List<Long>

    @Query("DELETE FROM recent WHERE id IN (:ids)")
    abstract suspend fun deleteAll(ids: List<Long>)

    @Query("SELECT id FROM recent")
    abstract suspend fun allIds(): List<Long>

    /** Upsert por `uri` conservando `lastPage` y `pageCount` de la fila existente. Devuelve la fila final. */
    @Transaction
    open suspend fun upsert(entity: RecentEntity): RecentEntity {
        val existing = findByUri(entity.uri)
        return if (existing == null) {
            entity.copy(id = insert(entity.copy(id = 0)))
        } else {
            val merged = entity.copy(id = existing.id, lastPage = existing.lastPage, pageCount = existing.pageCount)
            update(merged)
            merged
        }
    }
}
