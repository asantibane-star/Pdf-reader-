package dev.lector.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.AppSettings
import dev.lector.app.domain.model.ThemeMode
import dev.lector.app.domain.repo.SettingsRepository
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override val settings: Flow<AppSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings): Outcome<Unit> = try {
        store.edit { prefs -> prefs.writeSettings(transform(prefs.toSettings())) }
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        Outcome.Failure(DocError.SaveFailed)
    }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = this[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
        dynamicColor = this[DYNAMIC] ?: false,
        keepScreenOn = this[KEEP_SCREEN_ON] ?: false,
        hideInRecents = this[HIDE_IN_RECENTS] ?: false,
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.writeSettings(s: AppSettings) {
        this[THEME] = s.themeMode.name
        this[DYNAMIC] = s.dynamicColor
        this[KEEP_SCREEN_ON] = s.keepScreenOn
        this[HIDE_IN_RECENTS] = s.hideInRecents
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
    }
}
