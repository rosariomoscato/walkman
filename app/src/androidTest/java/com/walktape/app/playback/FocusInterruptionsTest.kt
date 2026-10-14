package com.walktape.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import com.walktape.app.core.net.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Audio focus (duck / transient / permanent), AUDIO_BECOMING_NOISY pause and background media
 * keys, against the real server. A second focus requester in the same process acts as the
 * competing app; physical calls/Bluetooth remain phone-only checks (see checklist).
 */
@RunWith(AndroidJUnit4::class)
class FocusInterruptionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** Competing AUDIO_USAGE_MEDIA focus owner; the framework itself delivers the loss to ExoPlayer. */
    private class FocusRival(context: Context) {
        private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val handler = Handler(Looper.getMainLooper())
        private var request: AudioFocusRequest? = null

        fun acquire(focusType: Int) {
            release()
            val built = AudioFocusRequest.Builder(focusType)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener({ }, handler)
                .build()
            request = built
            assertTrue("Competing focus request must actually be granted",
                audio.requestAudioFocus(built) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }

        fun release() {
            request?.let { audio.abandonAudioFocusRequest(it) }
            request = null
        }
    }

    @Test fun focusLossDuckingNoisyPauseAndMediaKeysBehave() = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue("Opt-in server configuration required", env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val previous = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
        val added = Graph.profiles.addVerified("Focus test ${System.nanoTime()}",
            config.getValue("ND_URL").replace("localhost", "10.0.2.2"),
            config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
        assertTrue("Real login must succeed (details suppressed)", added is ApiResult.Success)
        val id = (added as ApiResult.Success).value
        var controller: MediaController? = null
        var activity: android.app.Activity? = null
        val rival = FocusRival(context)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            Graph.profiles.activate(id)
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("input keyevent KEYCODE_WAKEUP")).use { it.readBytes() }
            val launched = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = launched
            withContext(Dispatchers.Main) { launched.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val songs = playbackCandidates(Graph.profiles.clientFor(id)!!)
            assertTrue("Need two real tracks for media-key checks", songs.size == 2)
            val future = withContext(Dispatchers.Main) {
                MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val c = future.get(20, TimeUnit.SECONDS)
            controller = c
            withContext(Dispatchers.Main) {
                c.setMediaItems(songs.map { it.toQueueItem(id) })
                c.prepare(); c.play()
            }
            await("Real stream must be playing") { c.isPlaying && c.currentPosition > 1_000 }
            val systemVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            assertTrue("Default public player volume must be unity", withContext(Dispatchers.Main) { c.volume == 1f })

            // 1) The sink starts at its implicit default; observe explicit duck/regain writes.
            rival.acquire(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            await("Ducking lowers player gain without pausing") {
                c.isPlaying && Graph.audioOutput.outputGain.value?.let { kotlin.math.abs(it - 0.2f) < 0.01f } == true && c.volume == 1f
            }
            await("Ducking never touches Android media volume") {
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) == systemVolume
            }
            rival.release()
            await("Sink gain is restored when the rival releases") { Graph.audioOutput.outputGain.value == 1f && c.volume == 1f && c.isPlaying }

            // 2) Transient loss (incoming call shape): pause, then Android auto-resumes on regain.
            rival.acquire(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            await("Transient loss must suppress output while retaining play intent") {
                c.playWhenReady && !c.isPlaying && c.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
            }
            rival.release()
            await("Transient regain auto-resumes playback") { c.isPlaying }

            rival.acquire(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            await("Second transient interruption must suppress playback") { !c.isPlaying && c.playWhenReady }
            withContext(Dispatchers.Main) { Graph.playback.togglePlayPause() }
            await("Manual pause during interruption overrides resume intent") { !c.playWhenReady }
            rival.release()
            delay(1500)
            assertFalse("Focus regain must respect the user's explicit pause", withContext(Dispatchers.Main) { c.playWhenReady || c.isPlaying })
            withContext(Dispatchers.Main) { Graph.playback.togglePlayPause() }
            await("Manual resume after interruption") { c.isPlaying }

            // 3) Permanent loss: pause and never restart on its own afterwards.
            rival.acquire(AudioManager.AUDIOFOCUS_GAIN)
            await("Permanent loss must pause") { !c.playWhenReady && !c.isPlaying }
            rival.release()
            delay(2_500)
            assertFalse("No autoplay after permanent focus loss", withContext(Dispatchers.Main) { c.playWhenReady || c.isPlaying })
            withContext(Dispatchers.Main) { Graph.playback.togglePlayPause() }
            await("Manual play re-acquires focus and plays") { c.isPlaying }

            // 4) Unplugging headphones shape: AUDIO_BECOMING_NOISY pauses; plugging back never resumes.
            // Protected broadcast: only this opt-in AOSP test uses su, never application code.
            val noisyResult = shell("su 0 am broadcast -a android.media.AUDIO_BECOMING_NOISY")
            assertTrue("Root-capable AOSP must deliver the protected synthetic broadcast",
                noisyResult.contains("Broadcast completed") && !noisyResult.contains("SecurityException"))
            await("Becoming noisy must pause") { !c.playWhenReady && !c.isPlaying }
            delay(2_500)
            assertFalse("No auto-resume after synthetic noisy event", withContext(Dispatchers.Main) { c.playWhenReady })
            withContext(Dispatchers.Main) { Graph.playback.togglePlayPause() }
            await("Manual play resumes after noisy pause") { c.isPlaying }

            // 5) Background media keys drive the session (headset/Bluetooth/notification shape).
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("input keyevent KEYCODE_HOME")).use { it.readBytes() }
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("cmd media_session dispatch pause")).use { it.readBytes() }
            await("MEDIA_PAUSE key controls the session in background") { !c.playWhenReady && !c.isPlaying }
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("cmd media_session dispatch play")).use { it.readBytes() }
            await("MEDIA_PLAY key resumes the session") { c.isPlaying }
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("cmd media_session dispatch next")).use { it.readBytes() }
            await("MEDIA_NEXT key advances the queue") { c.currentMediaItemIndex == 1 }
            withContext(Dispatchers.Main) { c.pause(); c.seekTo(0) }
            await("Previous test starts near beginning in pause") { c.currentPosition < 500 && !c.playWhenReady }
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("cmd media_session dispatch previous")).use { it.readBytes() }
            await("MEDIA_PREVIOUS key selects the previous track near start") {
                c.currentMediaItemIndex == 0 && !c.playWhenReady
            }
            val sessionState = instrumentation.uiAutomation.executeShellCommand("dumpsys media_session")
            val dump = ParcelFileDescriptor.AutoCloseInputStream(sessionState).use { it.readBytes().decodeToString() }
            assertTrue("WalkTape must remain the active media-button session while paused/playing",
                dump.contains("Media button session is com.walktape.app/"))

            shell("input keyevent KEYCODE_SLEEP")
            shell("cmd media_session dispatch play")
            await("Media key resumes with display asleep") { c.isPlaying }
            val start = withContext(Dispatchers.Main) { c.currentPosition }
            await("Real audio position advances with display asleep") { c.currentPosition > start + 1_000 }
            assertTrue("Display must actually be asleep", shell("dumpsys power").contains("mWakefulness=Asleep"))
            shell("cmd media_session dispatch pause")
            await("Media key pauses with display asleep") { !c.playWhenReady && !c.isPlaying }
        } finally {
            rival.release()
            shell("input keyevent KEYCODE_WAKEUP")
            withContext(Dispatchers.Main) { controller?.pause(); controller?.release(); activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            if (previous != null) Graph.profiles.activate(previous)
            Graph.profiles.remove(id)
            Graph.queues.flush()
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).use { it.readBytes().decodeToString() }

    private suspend fun await(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (withContext(Dispatchers.Main) { condition() }) return
            delay(200)
        }
        throw AssertionError(message)
    }
}
