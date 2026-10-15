package com.walktape.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.1 contrast checks for the fixed TPS-L2 chassis materials (wave 06): the device keeps
 * its own palette in both themes, so these pairs must hold regardless of the app theme.
 */
class ChassisContrastTest {

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun ratio(foreground: Color, background: Color): Double {
        val l1 = luminance(foreground)
        val l2 = luminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test fun smallTextPairsReach45() {
        // Cassette texts: ink/ink-soft on cream shell, orange-dim section label on cream,
        // steel/steel-dark on the dark window.
        assertTrue(ratio(WtInk, WtCream) >= 4.5)
        assertTrue(ratio(WtInkSoft, WtCream) >= 4.5)
        assertTrue(ratio(WtOrangeDim, WtCream) >= 4.5)
        assertTrue(ratio(WtSteel, WtBlueNight) >= 4.5)
        assertTrue(ratio(WtSteelDark, WtBlueNight) >= 4.5)
        assertTrue(ratio(WtInk, WtSteel) >= 4.5)
    }

    @Test fun keyIconsAndLargeElementsReach3() {
        // Play key icon on the orange gradient bounds, steel key icons, engaged orange dot
        // against its dark well and orange accents on the deep chassis surface. The small
        // orange square on the steel header is purely decorative and carries no information.
        assertTrue(ratio(WtBlueNight, WtOrange) >= 3.0)
        assertTrue(ratio(WtInk, WtOrangeDim) >= 3.0)
        assertTrue(ratio(WtInk, WtSteel) >= 3.0)
        assertTrue(ratio(WtInk, Color(0xFFE9EBEF)) >= 3.0)
        assertTrue(ratio(WtOrange, WtInk) >= 3.0)
        assertTrue(ratio(WtOrange, WtBlueDeep) >= 3.0)
    }

    @Test fun counterAndStatusTextOnWindowReach45() {
        assertTrue(ratio(WtSteel, CassetteWindowColor) >= 4.5)
        assertTrue(ratio(WtSteelDark, CassetteWindowColor) >= 4.5)
        assertTrue(ratio(WtOrange, CassetteWindowColor) >= 4.5)
    }
}
