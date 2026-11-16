package dev.lector.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.StrictMode
import dev.lector.app.di.AppContainer

class LectorApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        container = AppContainer(this)
        container.crashRecorder.install()
    }

    // RUNNING_LOW está obsoleto desde API 35 (ya no se notifica), pero sigue activo en API 29-34.
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) container.bitmapCache.evictAll()
    }
}
