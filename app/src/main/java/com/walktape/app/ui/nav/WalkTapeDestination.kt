package com.walktape.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class WalkTapeDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    HOME("home", com.walktape.app.R.string.nav_home, Icons.Filled.MusicNote),
    SETTINGS("settings", com.walktape.app.R.string.nav_settings, Icons.Filled.Settings)
}
