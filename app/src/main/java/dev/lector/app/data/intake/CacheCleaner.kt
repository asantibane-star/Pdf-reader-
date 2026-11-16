package dev.lector.app.data.intake

import java.io.File

/** Limpieza de la caché de trabajo. `cacheDir/converted/` se conserva a propósito (PLAN §7.3). */
object CacheCleaner {
    private val transientDirs = listOf(UriIntake.OPEN_DIR, "share", "camera")

    fun cleanOnStart(cacheDir: File) {
        transientDirs.forEach { File(cacheDir, it).deleteRecursively() }
    }

    /** Borra la copia en caché de un documento al cerrar su visor. Solo dentro de `open/`. */
    fun deleteOpenFile(cacheDir: File, path: String) {
        val file = File(path)
        val openDir = File(cacheDir, UriIntake.OPEN_DIR)
        if (file.canonicalFile.parentFile == openDir.canonicalFile) file.delete()
    }
}
