package com.walktape.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.walktape.app.core.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = WtBlue,
    onPrimary = WtCream,
    primaryContainer = WtBlueDeep,
    onPrimaryContainer = WtSteel,
    secondary = WtSteelDark,
    onSecondary = WtInk,
    secondaryContainer = WtSteel,
    onSecondaryContainer = WtInk,
    tertiary = WtOrange,
    onTertiary = WtCream,
    background = WtCream,
    onBackground = WtInk,
    surface = WtCream,
    onSurface = WtInk,
    surfaceVariant = WtSteel,
    onSurfaceVariant = WtInkSoft,
    outline = WtSteelDark
)

private val DarkColors = darkColorScheme(
    primary = WtBlueSoft,
    onPrimary = WtBlueNight,
    primaryContainer = WtBlue,
    onPrimaryContainer = WtSteel,
    secondary = WtSteel,
    onSecondary = WtBlueNight,
    secondaryContainer = WtBlueDeep,
    onSecondaryContainer = WtSteel,
    tertiary = WtOrange,
    onTertiary = WtBlueNight,
    background = WtBlueNight,
    onBackground = WtSteel,
    surface = WtBlueNight,
    onSurface = WtSteel,
    surfaceVariant = WtBlueDeep,
    onSurfaceVariant = WtSteelDark,
    outline = WtSteelDark
)

@Composable
fun WalkTapeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WalkTapeTypography,
        content = content
    )
}
