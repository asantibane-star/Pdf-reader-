package dev.lector.app.data.recents

import android.database.SQLException
import dev.lector.app.core.dispatch.AppDispatchers
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.domain.model.RecentDocument
import dev.lector.app.domain.repo.RecentsRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomRecentsRepository(
    private val dao: RecentDao,
    private val thumbnails: ThumbnailRepository,
    private val dispatchers: AppDispatchers,
) : RecentsRepository {

    override fun observe(): Flow<List<RecentDocument>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }.catch { error ->
            if (error is SQLException) emit(emptyList()) else throw error
        }

    override suspend fun record(ref: DocumentRef, now: Long): Outcome<RecentDocument> = db {
        val row = dao.upsert(
            RecentEntity(
                uri = ref.sourceUri,
                displayName = ref.displayName,
                kind = ref.kind.name,
                sizeBytes = ref.sizeBytes,
                lastOpenedAt = now,
            ),
        )
        purge()
        row.toDomain()
    }

    override suspend fun updateProgress(id: Long, page: Int, pageCount: Int): Outcome<Unit> = db {
        dao.updateProgress(id, page.coerceAtLeast(0), pageCount.coerceAtLeast(0))
    }

    override suspend fun remove(id: Long): Outcome<Unit> = db {
        dao.delete(id)
        thumbnails.delete(id)
    }

    override suspend fun restore(recent: RecentDocument): Outcome<Unit> = db {
        dao.insert(recent.toEntity())
        purge()
    }

    override suspend fun clear(): Outcome<Unit> = db {
        dao.deleteAll()
        thumbnails.deleteAll()
    }

    override suspend fun find(id: Long): RecentDocument? = try {
        dao.findById(id)?.toDomain()
    } catch (e: CancellationException) {
        throw e
    } catch (_: SQLException) {
        null
    }

    /** Máximo 100 filas: purga las más antiguas (y sus miniaturas). */
    private suspend fun purge() {
        val stale = dao.idsBeyond(MAX_ROWS)
        if (stale.isNotEmpty()) {
            dao.deleteAll(stale)
            stale.forEach { thumbnails.delete(it) }
        }
    }

    private suspend fun <T> db(block: suspend () -> T): Outcome<T> = withContext(dispatchers.io) {
        try {
            Outcome.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (_: SQLException) {
            Outcome.Failure(DocError.SaveFailed)
        } catch (_: IllegalStateException) {
            Outcome.Failure(DocError.SaveFailed)
        }
    }

    companion object {
        const val MAX_ROWS = 100
    }
}

internal fun RecentEntity.toDomain() = RecentDocument(
    id = id, uri = uri, displayName = displayName, kind = DocumentKind.fromName(kind), sizeBytes = sizeBytes,
    lastOpenedAt = lastOpenedAt, lastPage = lastPage, pageCount = pageCount,
)

internal fun RecentDocument.toEntity() = RecentEntity(
    id = id, uri = uri, displayName = displayName, kind = kind.name, sizeBytes = sizeBytes,
    lastOpenedAt = lastOpenedAt, lastPage = lastPage, pageCount = pageCount,
)
