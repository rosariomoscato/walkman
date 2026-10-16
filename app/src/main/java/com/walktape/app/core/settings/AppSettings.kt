package com.walktape.app.core.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** ReplayGain application modes; OFF keeps a clean untouched signal (SPEC default). */
enum class ReplayGainMode { OFF, TRACK, ALBUM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLockEnabled: Boolean = false,
    /** Optional server-backed continuation at the end of a queue; off until the user opts in. */
    val suggestionsEnabled: Boolean = false,
    /** Light key haptics on the TPS-L2 player; on by default, user can disable. */
    val hapticsEnabled: Boolean = true,
    /** Optional key click sounds; off until the user opts in. */
    val keyClicksEnabled: Boolean = false,
    /** Temporary audio cache ceiling (SPEC default 1 GB), editable in settings. */
    val audioCacheLimitBytes: Long = DEFAULT_AUDIO_CACHE_BYTES,
    /** Cover cache ceiling (SPEC default 250 MB), editable in settings. */
    val coverCacheLimitBytes: Long = DEFAULT_COVER_CACHE_BYTES,
    /** Optional crossfade between tracks; off and 3 s until the user opts in (W09-T02). */
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationMs: Int = DEFAULT_CROSSFADE_MS,
    /** ReplayGain mode; off by default (W09-T03). */
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    /** Optional equalizer; off by default (W09-T04). */
    val equalizerEnabled: Boolean = false,
    /** Band levels in millibels, persisted as CSV; recreated effects restore them. */
    val equalizerBands: String = "",
    /** Optional bass boost; off by default (W09-T04). */
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = DEFAULT_BASS_STRENGTH,
    /** Optional cassette tape effect; off by default (W09-T05). */
    val tapeEffectEnabled: Boolean = false,
    /** False until DataStore has emitted at least once; gates must not run on defaults. */
    val loaded: Boolean = false
) {
    companion object {
        const val DEFAULT_AUDIO_CACHE_BYTES = 1024L * 1024 * 1024
        const val DEFAULT_COVER_CACHE_BYTES = 250L * 1024 * 1024
        const val DEFAULT_CROSSFADE_MS = 3000
        const val MIN_CROSSFADE_MS = 500
        const val MAX_CROSSFADE_MS = 12000
        const val DEFAULT_BASS_STRENGTH = 500
        const val MIN_BASS_STRENGTH = 0
        const val MAX_BASS_STRENGTH = 1000
    }
}
