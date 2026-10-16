package com.walktape.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
    private val hapticsKey = booleanPreferencesKey("haptics_enabled")
    private val keyClicksKey = booleanPreferencesKey("key_clicks_enabled")
    private val audioCacheLimitKey = longPreferencesKey("audio_cache_limit_bytes")
    private val coverCacheLimitKey = longPreferencesKey("cover_cache_limit_bytes")
    private val crossfadeEnabledKey = booleanPreferencesKey("crossfade_enabled")
    private val crossfadeDurationKey = longPreferencesKey("crossfade_duration_ms")
    private val replayGainModeKey = stringPreferencesKey("replay_gain_mode")
    private val equalizerEnabledKey = booleanPreferencesKey("equalizer_enabled")
    private val equalizerBandsKey = stringPreferencesKey("equalizer_bands")
    private val bassBoostEnabledKey = booleanPreferencesKey("bass_boost_enabled")
    private val bassBoostStrengthKey = longPreferencesKey("bass_boost_strength")
    private val tapeEffectKey = booleanPreferencesKey("tape_effect_enabled")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[themeKey]?.let { stored ->
                ThemeMode.entries.firstOrNull { it.name == stored }
            } ?: ThemeMode.SYSTEM,
            appLockEnabled = prefs[appLockKey] ?: false,
            suggestionsEnabled = prefs[suggestionsKey] ?: false,
            hapticsEnabled = prefs[hapticsKey] ?: true,
            keyClicksEnabled = prefs[keyClicksKey] ?: false,
            audioCacheLimitBytes = prefs[audioCacheLimitKey] ?: AppSettings.DEFAULT_AUDIO_CACHE_BYTES,
            coverCacheLimitBytes = prefs[coverCacheLimitKey] ?: AppSettings.DEFAULT_COVER_CACHE_BYTES,
            crossfadeEnabled = prefs[crossfadeEnabledKey] ?: false,
            crossfadeDurationMs = (prefs[crossfadeDurationKey] ?: AppSettings.DEFAULT_CROSSFADE_MS.toLong())
                .toInt().coerceIn(AppSettings.MIN_CROSSFADE_MS, AppSettings.MAX_CROSSFADE_MS),
            replayGainMode = prefs[replayGainModeKey]?.let { stored ->
                ReplayGainMode.entries.firstOrNull { it.name == stored }
            } ?: ReplayGainMode.OFF,
            equalizerEnabled = prefs[equalizerEnabledKey] ?: false,
            equalizerBands = prefs[equalizerBandsKey] ?: "",
            bassBoostEnabled = prefs[bassBoostEnabledKey] ?: false,
            bassBoostStrength = (prefs[bassBoostStrengthKey] ?: AppSettings.DEFAULT_BASS_STRENGTH.toLong())
                .toInt().coerceIn(AppSettings.MIN_BASS_STRENGTH, AppSettings.MAX_BASS_STRENGTH),
            tapeEffectEnabled = prefs[tapeEffectKey] ?: false,
            loaded = true
        )
    }

    suspend fun setAudioCacheLimitBytes(bytes: Long) {
        require(bytes in MIN_CACHE_LIMIT..MAX_CACHE_LIMIT)
        context.settingsStore.edit { prefs -> prefs[audioCacheLimitKey] = bytes }
    }

    suspend fun setCoverCacheLimitBytes(bytes: Long) {
        require(bytes in MIN_CACHE_LIMIT..MAX_CACHE_LIMIT)
        context.settingsStore.edit { prefs -> prefs[coverCacheLimitKey] = bytes }
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

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[hapticsKey] = enabled
        }
    }

    suspend fun setKeyClicksEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[keyClicksKey] = enabled
        }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[crossfadeEnabledKey] = enabled }
    }

    suspend fun setCrossfadeDurationMs(ms: Int) {
        require(ms in AppSettings.MIN_CROSSFADE_MS..AppSettings.MAX_CROSSFADE_MS)
        context.settingsStore.edit { prefs -> prefs[crossfadeDurationKey] = ms.toLong() }
    }

    suspend fun setReplayGainMode(mode: ReplayGainMode) {
        context.settingsStore.edit { prefs -> prefs[replayGainModeKey] = mode.name }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[equalizerEnabledKey] = enabled }
    }

    suspend fun setEqualizerBands(csv: String) {
        require(csv.length <= 512)
        context.settingsStore.edit { prefs -> prefs[equalizerBandsKey] = csv }
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[bassBoostEnabledKey] = enabled }
    }

    suspend fun setBassBoostStrength(strength: Int) {
        require(strength in AppSettings.MIN_BASS_STRENGTH..AppSettings.MAX_BASS_STRENGTH)
        context.settingsStore.edit { prefs -> prefs[bassBoostStrengthKey] = strength.toLong() }
    }

    suspend fun setTapeEffectEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[tapeEffectKey] = enabled }
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

    companion object {
        const val MIN_CACHE_LIMIT = 50L * 1024 * 1024
        const val MAX_CACHE_LIMIT = 20L * 1024 * 1024 * 1024
    }
}
