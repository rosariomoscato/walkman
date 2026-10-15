package com.walktape.app.core.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    /** False until DataStore has emitted at least once; gates must not run on defaults. */
    val loaded: Boolean = false
) {
    companion object {
        const val DEFAULT_AUDIO_CACHE_BYTES = 1024L * 1024 * 1024
        const val DEFAULT_COVER_CACHE_BYTES = 250L * 1024 * 1024
    }
}
