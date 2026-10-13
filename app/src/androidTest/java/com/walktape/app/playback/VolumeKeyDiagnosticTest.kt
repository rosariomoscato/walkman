package com.walktape.app.playback

import android.content.Intent
import android.content.ComponentName
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.MainActivity
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Explicit diagnostic: key injection is distinct from an actual Motorola hardware-key test. */
@RunWith(AndroidJUnit4::class)
class VolumeKeyDiagnosticTest {
    @Test fun injectedVolumeKeyTargetsMusicWithForegroundActivity() = runBlocking {
        assumeTrue("Opt-in key-injection diagnostic; see WORKLOG",
            InstrumentationRegistry.getArguments().getString("volumeKeyDiagnostic") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val music = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val ring = audio.getStreamVolume(AudioManager.STREAM_RING)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")).use { it.readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        var controller: MediaController? = null
        try {
            withContext(Dispatchers.Main) {
                activity.volumeControlStream = AudioManager.STREAM_MUSIC
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
            delay(1000)
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("input keyevent KEYCODE_VOLUME_UP")).use { it.readBytes() }
            delay(1500)
            val keyVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            fun report(stage: String) = instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "\n$stage music=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} " +
                    "muted=${audio.isStreamMute(AudioManager.STREAM_MUSIC)} fixed=${audio.isVolumeFixed}\n")
            })
            report("key")
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            delay(1000)
            report("adjust")
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0)
            delay(1000)
            report("set-2")
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            delay(1000)
            report("unmute-adjust")
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            delay(500)
            report("set-0")
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            delay(1000)
            report("adjust-from-0")
            val future = withContext(Dispatchers.Main) {
                MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val connected = future.get(20, TimeUnit.SECONDS)
            controller = connected
            delay(1000)
            withContext(Dispatchers.Main) {
                instrumentation.sendStatus(0, Bundle().apply { putString("stream",
                    "\nSession volume commands: set=${connected.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)} " +
                        "adjust=${connected.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)} device=${connected.deviceVolume}\n") })
                if (connected.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) connected.increaseDeviceVolume(0)
            }
            delay(1000)
            report("session-adjust")
            withContext(Dispatchers.Main) {
                if (connected.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) connected.setDeviceVolume(4, 0)
            }
            delay(1000)
            report("session-set")
            assertTrue("Key injection must increase music volume: keyMusic=$keyVolume, currentMusic=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}, " +
                "ring=${audio.getStreamVolume(AudioManager.STREAM_RING)}",
                keyVolume > 1)
        } finally {
            withContext(Dispatchers.Main) { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            withContext(Dispatchers.Main) { controller?.release() }
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, music, 0)
            if (audio.getStreamVolume(AudioManager.STREAM_RING) != ring) audio.setStreamVolume(AudioManager.STREAM_RING, ring, 0)
        }
    }
}
