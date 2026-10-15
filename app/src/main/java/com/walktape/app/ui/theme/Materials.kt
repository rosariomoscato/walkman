package com.walktape.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Fixed materials of the TPS-L2 chassis. The device keeps its own palette in both themes;
 * text/icon pairs on these surfaces are covered by the contrast checks of wave 06.
 */
val ChassisBodyBrush = Brush.verticalGradient(listOf(WtBlue, WtBlueDeep))
val ChassisSteelBrush = Brush.verticalGradient(
    listOf(Color(0xFFE9EBEF), WtSteel, Color(0xFFA9AFBA))
)
val KeySteelBrush = Brush.linearGradient(listOf(Color(0xFFE9EBEF), WtSteel, Color(0xFFA9AFBA)))
val KeyOrangeBrush = Brush.linearGradient(listOf(Color(0xFFF8874E), WtOrange, WtOrange))

/** Cassette window surfaces: dark window, cream shell, tape and hub materials. */
val CassetteWindowColor = WtBlueNight
val CassetteShellColor = WtCream
val TapeColor = Color(0xFF3B3227)
val ReelHubColor = Color(0xFFFAFAF6)
