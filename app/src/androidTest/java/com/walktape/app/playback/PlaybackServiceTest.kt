package com.walktape.app.playback

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.graphics.Rect
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.SongDto
import com.walktape.app.core.net.parseSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Real server, emulator decoding; never embeds or prints secrets. Restores the previous profile. */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun realMp3AndFlacStreamThroughSessionAndProfileSwitchStopsPlayback() = runBlocking {
        val envFile = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue("Opt-in local server config required", envFile.exists())
        val config = envFile.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val url = config.getValue("ND_URL").replace("localhost", "10.0.2.2")
        val previousActive = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
        val added = Graph.profiles.addVerified(
            "Playback e2e ${System.currentTimeMillis()}", url,
            config.getValue("ND_USER"), config.getValue("ND_PASS"), true
        )
        assertTrue("Real login must succeed (details suppressed)", added is ApiResult.Success)
        val testId = (added as ApiResult.Success).value
        var controller: MediaController? = null
        var activity: android.app.Activity? = null
        try {
            assertTrue(Graph.profiles.activate(testId))
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("input keyevent KEYCODE_WAKEUP")).use { it.readBytes() }
            val launched = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = launched
            withContext(Dispatchers.Main) { launched.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val songs = playbackCandidates(Graph.profiles.clientFor(testId)!!)
            assertTrue("Real library must supply MP3 and FLAC samples", songs.size == 2)
            val future = withContext(Dispatchers.Main) {
                MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val connected = future.get(20, TimeUnit.SECONDS)
            controller = connected
            for (song in songs) {
                withContext(Dispatchers.Main) {
                    connected.setMediaItem(song.toQueueItem(testId))
                    connected.prepare()
                    connected.play()
                }
                var playing = false
                val deadline = System.currentTimeMillis() + 30_000
                while (System.currentTimeMillis() < deadline) {
                    playing = withContext(Dispatchers.Main) {
                        connected.isPlaying && connected.currentPosition > 1000
                    }
                    if (playing) break
                    delay(250)
                }
                assertTrue("Real ${song.suffix} streaming must advance position (details suppressed)", playing)
                withContext(Dispatchers.Main) { connected.pause() }
                // A controller masks the command immediately; await the independently updated
                // app bridge too, then three stable samples instead of a fixed 300 ms settling time.
                awaitPlayback("Pause must synchronize both controllers") {
                    !connected.isPlaying && !connected.playWhenReady && Graph.playback.state.value.connected &&
                        !Graph.playback.state.value.isPlaying && !Graph.playback.state.value.playWhenReady
                }
                var previousPosition = withContext(Dispatchers.Main) { connected.currentPosition }
                var stableSamples = 0
                awaitPlayback("Paused position must stay stable") {
                    val position = connected.currentPosition
                    stableSamples = if (!connected.isPlaying && kotlin.math.abs(position - previousPosition) < 100)
                        stableSamples + 1 else 0
                    previousPosition = position
                    stableSamples >= 3
                }
            }
            verifyInAppControlsAndSessionSync(connected, songs, testId)
            withContext(Dispatchers.Main) { connected.repeatMode = Player.REPEAT_MODE_ALL; connected.play() }
            delay(500)
            if (previousActive != null) Graph.profiles.activate(previousActive)
            else Graph.profiles.remove(testId)
            var cleared = false
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                cleared = withContext(Dispatchers.Main) {
                    !connected.playWhenReady && connected.mediaItemCount == 0 && connected.repeatMode == Player.REPEAT_MODE_OFF
                }
                if (cleared) break
                delay(250)
            }
            assertTrue("Profile switch/removal must stop and clear playback", cleared)
        } finally {
            withContext(Dispatchers.Main) { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            withContext(Dispatchers.Main) { controller?.pause(); controller?.release() }
            if (previousActive != null) Graph.profiles.activate(previousActive)
            Graph.profiles.remove(testId)
        }
    }

    private suspend fun playbackCandidates(client: OpenSubsonicClient): List<SongDto> {
        val found = mutableMapOf<String, SongDto>()
        repeat(5) {
            val result = client.request("getRandomSongs", listOf("size" to "500"))
            if (result is ApiResult.Success) {
                result.value.getAsJsonObject("randomSongs")?.getAsJsonArray("song")?.forEach {
                    val song = parseSong(it.asJsonObject)
                    if (song.duration > 30 && song.suffix in listOf("mp3", "flac")) found.putIfAbsent(song.suffix!!, song)
                }
            }
            if (found.size == 2) return listOf(found.getValue("mp3"), found.getValue("flac"))
        }
        return emptyList()
    }

    private fun SongDto.toQueueItem(profileId: Long): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(SubsonicStream.uri(profileId, id).toUri())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
        .build()

    private suspend fun verifyInAppControlsAndSessionSync(controller: MediaController, songs: List<SongDto>, profileId: Long) {
        val bridge = Graph.playback
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            withContext(Dispatchers.Main) {
                controller.pause()
                controller.setMediaItems(songs.map { it.toQueueItem(profileId) })
                controller.prepare()
            }
            awaitPlayback("Bridge must receive track, duration and seekability") {
                bridge.state.value.hasMedia && bridge.state.value.canSeek && bridge.state.value.connected &&
                    bridge.state.value.title == controller.mediaMetadata.title?.toString()
            }
            withContext(Dispatchers.Main) { bridge.togglePlayPause() }
            awaitPlayback("In-app play must reach the service") { controller.isPlaying && bridge.state.value.isPlaying }
            clickUi { it.contentDescription?.toString() == "Pausa" }
            awaitPlayback("In-app pause must reach the service") { !controller.playWhenReady && !bridge.state.value.playWhenReady }
            clickUi { it.contentDescription?.toString() == "Apri controlli di riproduzione" }
            awaitUi { it.text?.toString() == "Controlli di riproduzione" }
            awaitPlayback("Seek must remain available in the panel") { bridge.state.value.canSeek }
            val seek = awaitProgress("Posizione di riproduzione")
            assertTrue("Accessible seek action must be supported", seek.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, Bundle().apply {
                    putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 10_000f)
                }))
            awaitPlayback("Paused seek must retain the precise requested position") {
                kotlin.math.abs(controller.currentPosition - 10_000) < 200 && !controller.playWhenReady
            }
            tapProgress("Posizione di riproduzione", 0.6f)
            awaitPlayback("Touch seeking must move playback without unpausing") {
                controller.currentPosition in (controller.duration * 45 / 100)..(controller.duration * 75 / 100) &&
                    !controller.playWhenReady
            }
            withContext(Dispatchers.Main) { bridge.previous() }
            awaitPlayback("Previous after threshold must restart current track") {
                controller.currentMediaItemIndex == 0 && controller.currentPosition < 200
            }
            val firstKey = bridge.state.value.itemKey
            clickUi { it.contentDescription?.toString() == "Successivo" }
            awaitPlayback("Next must select the second track and update metadata") {
                controller.currentMediaItemIndex == 1 && bridge.state.value.title == controller.mediaMetadata.title?.toString() &&
                    bridge.state.value.itemKey != firstKey && bridge.state.value.canSeek
            }
            withContext(Dispatchers.Main) { bridge.seekTo(20_000, firstKey) }
            delay(400)
            assertTrue("A stale seek gesture must not affect the next track", withContext(Dispatchers.Main) {
                controller.currentPosition < 1000
            })
            clickUi { it.contentDescription?.toString() == "Precedente o inizio brano" }
            awaitPlayback("Previous near start must select the prior track") {
                controller.currentMediaItemIndex == 0 && bridge.state.value.itemKey == firstKey
            }

            clickUi { it.text?.toString() == "Ripetizione: disattivata" }
            awaitPlayback("Repeat-one must be reflected by the external session controller") { controller.repeatMode == Player.REPEAT_MODE_ONE }
            awaitPlayback("Track must be prepared after previous") { bridge.state.value.canSeek && controller.playbackState == Player.STATE_READY }
            withContext(Dispatchers.Main) {
                bridge.seekTo(controller.duration - 400, bridge.state.value.itemKey)
                bridge.togglePlayPause()
            }
            awaitPlayback("Repeat-one must actually wrap the current track") {
                controller.isPlaying && controller.currentMediaItemIndex == 0 && controller.currentPosition < 3000
            }
            withContext(Dispatchers.Main) { bridge.togglePlayPause() }
            clickUi { it.text?.toString() == "Ripetizione: brano" }
            awaitPlayback("Repeat-all must be applied") { controller.repeatMode == Player.REPEAT_MODE_ALL }
            withContext(Dispatchers.Main) { bridge.next() }
            awaitPlayback("Last track must be ready for repeat-all") {
                controller.currentMediaItemIndex == 1 && controller.playbackState == Player.STATE_READY && bridge.state.value.canSeek
                    && bridge.state.value.itemKey != firstKey
            }
            withContext(Dispatchers.Main) {
                bridge.seekTo(controller.duration - 400, bridge.state.value.itemKey)
                bridge.togglePlayPause()
            }
            awaitPlayback("Repeat-all must wrap back to the first track") {
                controller.isPlaying && controller.currentMediaItemIndex == 0 && controller.currentPosition < 3000
            }
            withContext(Dispatchers.Main) { bridge.togglePlayPause() }
            clickUi { it.text?.toString() == "Ripetizione: coda" }
            awaitPlayback("Repeat must return to off") { controller.repeatMode == Player.REPEAT_MODE_OFF }

            withContext(Dispatchers.Main) { bridge.next() }
            awaitPlayback("Last track must be ready with repeat off") {
                controller.currentMediaItemIndex == 1 && controller.playbackState == Player.STATE_READY &&
                    bridge.state.value.canSeek && bridge.state.value.itemKey != firstKey
            }
            withContext(Dispatchers.Main) {
                bridge.seekTo(controller.duration - 400, bridge.state.value.itemKey)
                bridge.togglePlayPause()
            }
            awaitPlayback("Repeat off must end the queue") {
                controller.playbackState == Player.STATE_ENDED && bridge.state.value.ended && !controller.isPlaying
            }
            clickUi { it.contentDescription?.toString() == "Riproduci" }
            awaitPlayback("Play after queue end must restart from the first track") {
                controller.currentMediaItemIndex == 0 && controller.isPlaying && bridge.state.value.isPlaying
            }
            clickUi { it.contentDescription?.toString() == "Pausa" }
            awaitPlayback("Restarted queue must pause normally") { !controller.playWhenReady }

            val volume = awaitProgress("Volume multimediale Android")
            assertTrue(volume.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, Bundle().apply {
                putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0f)
            }))
            awaitPlayback("Volume slider must change Android music volume, not player gain") {
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 && bridge.state.value.connected &&
                    bridge.state.value.volume == 0 && controller.volume == 1f
            }
            awaitPlayback("Session must advertise the real Android device-volume range") {
                controller.deviceInfo.maxVolume == audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }
            // Local MediaController does not advertise device-volume write commands.
            // System volume is controlled by AudioManager, independently of the app bridge.
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            awaitPlayback("External Android volume changes must update the in-app state") {
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 &&
                    bridge.state.value.volume == audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            withContext(Dispatchers.Main) { controller.play() }
            awaitPlayback("External play must update in-app controls") { bridge.state.value.playWhenReady }
            withContext(Dispatchers.Main) { controller.pause() }
            awaitPlayback("External pause must update in-app controls") { !bridge.state.value.playWhenReady }
            // Release/rebind the same bridge while the service remains bound: never auto-start.
            withContext(Dispatchers.Main) { bridge.disconnect(); bridge.connect() }
            awaitPlayback("Reconnect must restore session state without autoplay") {
                bridge.state.value.connected && bridge.state.value.hasMedia && !bridge.state.value.playWhenReady
            }
            assertEquals(0, bridge.state.value.repeatMode)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        } finally {
            withContext(Dispatchers.Main) { bridge.setSystemVolume(originalVolume) }
        }
    }

    private suspend fun awaitPlayback(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 40_000
        while (System.currentTimeMillis() < deadline) {
            if (withContext(Dispatchers.Main) { condition() }) return
            delay(250)
        }
        throw AssertionError(message)
    }

    private fun awaitUi(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val wait = findNode(root) { it.text?.toString() == "Wait" }
                if (wait != null) performClick(wait)
                else findNode(root, predicate)?.let { return it }
            }
            Thread.sleep(300)
        }
        val diagnostic = mutableListOf<String>()
        fun inspect(node: AccessibilityNodeInfo) {
            val description = node.contentDescription?.toString().orEmpty()
            if (node.rangeInfo != null || description in listOf("Posizione di riproduzione", "Volume multimediale Android")) {
                val range = node.rangeInfo
                diagnostic.add("description=$description enabled=${node.isEnabled} actions=${node.actionList.map { it.id }} " +
                    "range=${range?.min}..${range?.max} current=${range?.current}")
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::inspect)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::inspect)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nPlayback slider diagnostics: ${diagnostic.joinToString("; ")}\n") })
        throw AssertionError("Playback UI control not found")
    }

    private fun findNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNode(child, predicate)?.let { return it }
        }
        return null
    }

    private fun clickUi(predicate: (AccessibilityNodeInfo) -> Boolean) = performClick(awaitUi(predicate))

    private fun awaitProgress(description: String): AccessibilityNodeInfo = awaitUi { node ->
        if (!node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id }) false
        else {
            var ancestor: AccessibilityNodeInfo? = node
            var labelled = false
            while (ancestor != null) {
                if (ancestor.contentDescription?.toString() == description) { labelled = true; break }
                ancestor = ancestor.parent
            }
            labelled
        }
    }

    private fun tapProgress(description: String, fraction: Float) {
        val bounds = Rect()
        awaitProgress(description).getBoundsInScreen(bounds)
        val x = bounds.left + bounds.width() * fraction
        val y = bounds.exactCenterY()
        val start = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(down, true)) } finally { down.recycle() }
        Thread.sleep(80)
        val up = MotionEvent.obtain(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(up, true)) } finally { up.recycle() }
    }

    private fun performClick(node: AccessibilityNodeInfo) {
        var clickable = node
        while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
        assertTrue("Playback UI control must be clickable", clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
}
