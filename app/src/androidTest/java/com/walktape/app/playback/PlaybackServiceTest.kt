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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.SongDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
        val previousSaved = previousActive?.let { Graph.queues.load(it) }
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
                    !connected.playWhenReady && connected.mediaItemCount == (previousSaved?.items?.size ?: 0) &&
                        connected.repeatMode == (previousSaved?.repeatMode ?: Player.REPEAT_MODE_OFF) &&
                        connected.currentMediaItem?.mediaId == previousSaved?.currentId
                }
                if (cleared) break
                delay(250)
            }
            assertTrue("Profile switch/removal must stop and restore the other profile, never autoplay", cleared)
        } finally {
            withContext(Dispatchers.Main) { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            withContext(Dispatchers.Main) { controller?.pause(); controller?.release() }
            if (previousActive != null) Graph.profiles.activate(previousActive)
            Graph.profiles.remove(testId)
        }
    }

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

    /** Queue sheet: entries, remove, move via accessibility action, play-from-row, shuffle+restore. */
    @Test fun realSavedFlacResumesAtPositionOnlyAfterExplicitPlay() = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue("Opt-in server configuration required", env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val previous = Graph.database.profileDao().active()?.id
        val url = config.getValue("ND_URL").replace("localhost", "10.0.2.2")
        suspend fun add(name: String): Long {
            val result = Graph.profiles.addVerified("$name ${System.nanoTime()}", url,
                config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
            assertTrue("Real login must succeed (details suppressed)", result is ApiResult.Success)
            return (result as ApiResult.Success).value
        }
        val a = add("Live saved A"); val b = add("Live saved B")
        var controller: MediaController? = null
        var activity: android.app.Activity? = null
        try {
            Graph.profiles.activate(a)
            val launched = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = launched
            withContext(Dispatchers.Main) { launched.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val songs = playbackCandidates(Graph.profiles.clientFor(a)!!)
            val future = withContext(Dispatchers.Main) {
                MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val c = future.get(20, TimeUnit.SECONDS); controller = c
            withContext(Dispatchers.Main) { c.setMediaItems(songs.map { it.toQueueItem(a) }, 1, 10_000); c.prepare(); c.play() }
            awaitPlayback("Live FLAC must advance from seek position") { c.isPlaying && c.currentPosition > 10_500 }
            withContext(Dispatchers.Main) { c.pause(); c.repeatMode = Player.REPEAT_MODE_ALL; c.shuffleModeEnabled = true }
            awaitPlayback("Pause and shuffle acknowledged") { !c.playWhenReady && c.shuffleModeEnabled }
            delay(500)
            Graph.profiles.activate(b)
            awaitPlayback("Other profile must stay paused") { c.mediaItemCount == 0 && !c.playWhenReady }
            val saved = Graph.queues.load(a)!!
            assertTrue("Playing/seek position must have been durably saved", saved.positionMs > 10_000)
            assertTrue(saved.shuffle)
            Graph.profiles.activate(a)
            awaitPlayback("Restored live queue is idle and paused at saved FLAC occurrence") {
                c.currentMediaItem?.mediaId == saved.currentId && c.currentPosition == saved.positionMs &&
                    c.playbackState == Player.STATE_IDLE && !c.playWhenReady && c.shuffleModeEnabled
            }
            delay(1000)
            assertFalse(withContext(Dispatchers.Main) { c.isPlaying })
            withContext(Dispatchers.Main) { Graph.playback.togglePlayPause() }
            awaitPlayback("Explicit in-app resume must stream at the saved position") {
                c.isPlaying && c.currentMediaItem?.mediaId == saved.currentId && c.currentPosition > saved.positionMs + 500
            }
        } finally {
            withContext(Dispatchers.Main) { controller?.pause(); controller?.release(); activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            if (previous != null) Graph.profiles.activate(previous)
            Graph.profiles.remove(a); Graph.profiles.remove(b); Graph.queues.flush()
        }
    }

    @Test fun realQueueOperationsPreserveOccurrencesAndShuffle() = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue("Opt-in server configuration required", env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val previous = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
        val added = Graph.profiles.addVerified("Queue test ${System.currentTimeMillis()}",
            config.getValue("ND_URL").replace("localhost", "10.0.2.2"), config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
        assertTrue("Real connection must succeed (details suppressed)", added is ApiResult.Success)
        val id = (added as ApiResult.Success).value
        var connected: MediaController? = null
        var activity: android.app.Activity? = null
        try {
            Graph.profiles.activate(id)
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("input keyevent KEYCODE_WAKEUP")).use { it.readBytes() }
            val launched = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = launched
            withContext(Dispatchers.Main) { launched.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val future = withContext(Dispatchers.Main) {
                MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val controller = future.get(20, TimeUnit.SECONDS)
            connected = controller
            verifyQueueEditing(controller, Graph.playback, playbackCandidates(Graph.profiles.clientFor(id)!!), id)
        } finally {
            withContext(Dispatchers.Main) { connected?.pause(); connected?.release(); activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            if (previous != null) Graph.profiles.activate(previous)
            Graph.profiles.remove(id)
        }
    }

    private suspend fun verifyQueueEditing(controller: MediaController, bridge: PlaybackConnection,
        songs: List<SongDto>, profileId: Long) {
        val queue = listOf(songs[0], songs[1], songs[0], songs[1], songs[0])
        withContext(Dispatchers.Main) {
            controller.pause()
            controller.setMediaItems(queue.map { it.toQueueItem(profileId) }, 3, 10_000)
            controller.prepare()
        }
        awaitPlayback("Queue ready with unique occurrences") {
            bridge.state.value.canEditQueue && bridge.state.value.queue.size == 5 && controller.playbackState == Player.STATE_READY
        }
        val original = bridge.state.value.queue.map { it.entryId }
        assertEquals(5, original.toSet().size)
        val selected = original[3]
        val opener = awaitUi { it.contentDescription?.toString() == "Apri controlli di riproduzione" ||
            it.text?.toString()?.startsWith("Apri la coda") == true }
        if (opener.contentDescription?.toString() == "Apri controlli di riproduzione") performClick(opener)
        clickQueueButton()
        awaitUi { it.text?.toString() == "Coda" }
        clickUi { it.contentDescription?.toString() == "Mescola la coda" }
        awaitPlayback("Shuffle anchors exact current duplicate without seeking") {
            bridge.state.value.shuffleOn && bridge.state.value.queue.first().entryId == selected &&
                controller.currentMediaItemIndex == 3 && kotlin.math.abs(controller.currentPosition - 10_000) < 200 && !controller.playWhenReady
        }
        val traversal = bridge.state.value.queue.map { it.entryId }
        assertEquals(original.toSet(), traversal.toSet())
        // Rebind while the service is held by the second controller: shuffle/order must survive.
        withContext(Dispatchers.Main) { bridge.disconnect(); bridge.connect() }
        awaitPlayback("Shuffle survives release/rebind") {
            bridge.state.value.connected && bridge.state.value.shuffleOn && bridge.state.value.queue.map { it.entryId } == traversal
        }
        for (id in traversal.drop(1)) {
            withContext(Dispatchers.Main) { controller.seekToNextMediaItem() }
            awaitPlayback("Native next follows the displayed shuffle permutation") { controller.currentMediaItem?.mediaId == id }
        }
        val last = traversal.last()
        withContext(Dispatchers.Main) { bridge.toggleShuffle() }
        awaitPlayback("Disable restores original order and exact duplicate identity") {
            !bridge.state.value.shuffleOn && bridge.state.value.queue.map { it.entryId } == original &&
                controller.currentMediaItem?.mediaId == last
        }
        // The transient disconnect may dismiss the sheet; reopen through the bar if necessary.
        val root = instrumentation.uiAutomation.rootInActiveWindow
        if (findNode(root ?: throw AssertionError("No UI root")) { it.text?.toString() == "Coda" } == null) {
            clickUi { it.contentDescription?.toString() == "Apri controlli di riproduzione" }
            clickQueueButton()
        }
        val removed = original.first { it != last }
        performQueueAction(removed, "Rimuovi dalla coda")
        awaitPlayback("Remove one occurrence only") { bridge.state.value.queue.map { it.entryId } == original.filter { it != removed } }
        val remaining = bridge.state.value.queue.map { it.entryId }
        performClick(queueNode(remaining[0]))
        awaitPlayback("Queue row tap plays that occurrence") { controller.isPlaying && controller.currentMediaItem?.mediaId == remaining[0] }
        withContext(Dispatchers.Main) { controller.pause(); controller.seekTo(11_000) }
        performQueueAction(remaining[0], "Sposta giù")
        awaitPlayback("Move current preserves source, paused position and identity") {
            bridge.state.value.queue[1].entryId == remaining[0] && controller.currentMediaItem?.mediaId == remaining[0] &&
                kotlin.math.abs(controller.currentPosition - 11_000) < 200 && !controller.playWhenReady
        }
        val beforeMenu = bridge.state.value.queue.map { it.entryId }
        val row = queueNode(beforeMenu[0])
        val actions = findNode(row) { it.contentDescription?.toString() == "Azioni del brano in coda" }!!
        performClick(actions)
        clickUi { it.text?.toString() == "Sposta giù" }
        awaitPlayback("Visible row menu reorders the exact occurrence") { bridge.state.value.queue[1].entryId == beforeMenu[0] }
        withContext(Dispatchers.Main) { bridge.removeQueueItem(beforeMenu[0]) }
        awaitPlayback("Queue remains editable after menu reordering") { bridge.state.value.queue.size == 3 }
        // Replacing the queue externally while shuffled must not resurrect old entries.
        withContext(Dispatchers.Main) { controller.shuffleModeEnabled = true; controller.setMediaItem(songs[0].toQueueItem(profileId)); controller.prepare() }
        awaitPlayback("External replacement contains no old entries") { bridge.state.value.queue.size == 1 && bridge.state.value.canEditQueue }
        withContext(Dispatchers.Main) { controller.shuffleModeEnabled = false; controller.pause() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun queueNode(id: String) = awaitUi {
        it.viewIdResourceName?.endsWith("queue-row-${id.replace('/', '_')}") == true
    }

    private fun performQueueAction(id: String, label: String) {
        val row = queueNode(id)
        val action = row.actionList.firstOrNull { it.label?.toString() == label }
            ?: throw AssertionError("Queue action unavailable: $label")
        assertTrue(row.performAction(action.id))
    }

    private fun clickQueueButton() {
        repeat(8) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                findNode(root) { it.text?.toString()?.startsWith("Apri la coda") == true && it.isVisibleToUser }?.let { performClick(it); return }
                findNode(root) { it.isScrollable && it.className?.toString() == "android.widget.ScrollView" }
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            Thread.sleep(300)
        }
        clickUi { it.text?.toString()?.startsWith("Apri la coda") == true }
    }

    private fun dragHandle(x: Float, y: Float, distance: Float) {
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, at: Float) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, at, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, y); Thread.sleep(650)
        for (step in 1..8) { send(MotionEvent.ACTION_MOVE, y + distance * step / 8); Thread.sleep(40) }
        send(MotionEvent.ACTION_UP, y + distance)
    }

    /** Runs a queue-row custom action on the row carrying [title] (by occurrence order). */
    private fun performCustomAction(title: String, occurrence: Int, label: String) {
        val node = awaitNthTitle(title, occurrence) { candidate ->
            candidate.actionList.any { it.label?.toString() == label }
        }
        val action = node.actionList.first { it.label?.toString() == label }
        assertTrue("Queue custom action must run: $label",
            node.performAction(action.id))
    }

    /** Clicks the n-th (0-based) visible node showing [title], in screen order. */
    private fun clickNthTitle(title: String, occurrence: Int) {
        performClick(awaitNthTitle(title, occurrence) { true })
    }

    private fun awaitNthTitle(title: String, occurrence: Int,
        extra: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val matches = mutableListOf<AccessibilityNodeInfo>()
                collectNodes(root, { it.text?.toString() == title && extra(it) }, matches)
                if (matches.size > occurrence) return matches[occurrence]
            }
            Thread.sleep(300)
        }
        throw AssertionError("Queue row not found: title occurrence $occurrence")
    }

    private fun collectNodes(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean,
        output: MutableList<AccessibilityNodeInfo>) {
        if (predicate(node)) output.add(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectNodes(it, predicate, output) }
    }

    private suspend fun awaitPlayback(message: String, condition: () -> Boolean) {        val deadline = System.currentTimeMillis() + 40_000
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
        val rootPackage = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nPlayback slider diagnostics: ${diagnostic.joinToString("; ")}\n" +
            "rootPackage=$rootPackage\n") })
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
