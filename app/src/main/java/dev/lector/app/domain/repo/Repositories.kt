package dev.lector.app.domain.repo

import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.domain.model.RecentDocument
import kotlinx.coroutines.flow.Flow

interface RecentsRepository {
    fun observe(): Flow<List<RecentDocument>>

    /** Upsert por `uri`: conserva la última página y el total si el documento ya estaba. */
    suspend fun record(ref: DocumentRef, now: Long = System.currentTimeMillis()): Outcome<RecentDocument>

    suspend fun updateProgress(id: Long, page: Int, pageCount: Int): Outcome<Unit>

    suspend fun remove(id: Long): Outcome<Unit>

    /** Deshacer un borrado: vuelve a insertar la fila con su id. */
    suspend fun restore(recent: RecentDocument): Outcome<Unit>

    suspend fun clear(): Outcome<Unit>

    suspend fun find(id: Long): RecentDocument?
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings): Outcome<Unit>
}
