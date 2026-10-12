package com.walktape.app.core.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLockEnabled: Boolean = false,
    /** False until DataStore has emitted at least once; gates must not run on defaults. */
    val loaded: Boolean = false
)
