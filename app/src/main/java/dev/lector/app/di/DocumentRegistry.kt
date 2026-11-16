package dev.lector.app.di

import dev.lector.app.domain.model.DocumentRef
import java.util.concurrent.ConcurrentHashMap

/** Documentos ingeridos de la sesión actual, por id. En memoria: tras la muerte del proceso se reingesta desde `sourceUri`. */
class DocumentRegistry {
    private val map = ConcurrentHashMap<String, DocumentRef>()

    fun register(ref: DocumentRef) {
        map[ref.id] = ref
    }

    fun get(id: String): DocumentRef? = map[id]

    fun remove(id: String) {
        map.remove(id)
    }
}
