package com.walktape.app.ui.components

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.core.settings.AppSettings

/**
 * Optional key feedback for the TPS-L2 player: light haptics on by default (user can disable
 * them in settings), click sounds off until opted in. System-level haptic and sound switches
 * are always honored by the platform APIs used here; nothing fires on playback state changes,
 * only on explicit key presses.
 */
class KeyFeedback(
    private val view: View?,
    private val audio: AudioManager?,
    private val hapticsEnabled: Boolean,
    private val clicksEnabled: Boolean
) {
    fun press() {
        if (hapticsEnabled) view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        click(1f)
    }

    fun holdStart() {
        if (hapticsEnabled) view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun tick() {
        if (hapticsEnabled) view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        click(0.6f)
    }

    private fun click(volume: Float) {
        if (!clicksEnabled) return
        val resolver = view?.context?.contentResolver ?: return
        if (Settings.System.getInt(resolver, Settings.System.SOUND_EFFECTS_ENABLED, 0) == 1) {
            audio?.playSoundEffect(AudioManager.FX_KEY_CLICK, volume)
        }
    }
}

@Composable
fun rememberKeyFeedback(): KeyFeedback {
    val settings by Graph.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings()
    )
    val view = LocalView.current
    val audio = LocalContext.current.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    return KeyFeedback(view, audio, settings.hapticsEnabled, settings.keyClicksEnabled)
}
