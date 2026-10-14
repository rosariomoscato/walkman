package com.walktape.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val appLockKey = booleanPreferencesKey("app_lock_enabled")
    private val suggestionsKey = booleanPreferencesKey("suggestions_enabled")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[themeKey]?.let { stored ->
                ThemeMode.entries.firstOrNull { it.name == stored }
            } ?: ThemeMode.SYSTEM,
            appLockEnabled = prefs[appLockKey] ?: false,
            suggestionsEnabled = prefs[suggestionsKey] ?: false,
            loaded = true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[appLockKey] = enabled
        }
    }

    suspend fun setSuggestionsEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[suggestionsKey] = enabled
        }
    }

    suspend fun setLibrarySort(key: String, sort: String) {
        require(key.startsWith("sort_"))
        context.settingsStore.edit { prefs -> prefs[stringPreferencesKey(key)] = sort }
    }

    suspend fun librarySorts(): Map<String, String> =
        context.settingsStore.data.first().let { prefs ->
            prefs.asMap().filterKeys { it.name.startsWith("sort_") }
                .mapKeys { it.key.name }.mapValues { it.value as String }
        }
}
