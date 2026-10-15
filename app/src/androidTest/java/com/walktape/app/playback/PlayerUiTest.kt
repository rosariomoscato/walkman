package com.walktape.app.playback

import android.content.Intent
import android.media.AudioManager
import android.graphics.Rect
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import com.walktape.app.core.net.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * TPS-L2 player screen (wave 06) against the real server: chassis keys wired to the session,
 * cassette label from the real queue, long-press scrub on the FF/REW keys, volume keys, queue
 * access and the enlarged cover. Emulator verification only: haptics/clicks feel and TalkBack
 * stay manual checks on real hardware.
 */
@RunWith(AndroidJUnit4::class)
class PlayerUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun feedbackPreferencesPersistIndependently(): Unit = runBlocking {
        val repository = Graph.settingsRepository
        val before = repository.settings.first()
        try {
            repository.setHapticsEnabled(false)
            repository.setKeyClicksEnabled(true)
            val saved = com.walktape.app.core.settings.SettingsRepository(context).settings.first()
            assertTrue(!saved.hapticsEnabled && saved.keyClicksEnabled)
            repository.setKeyClicksEnabled(false)
            assertTrue(!repository.settings.first().keyClicksEnabled)
        } finally {
            repository.setHapticsEnabled(before.hapticsEnabled)
            repository.setKeyClicksEnabled(before.keyClicksEnabled)
        }
    }

    @Test fun playerChassisKeysScrubLabelCoverAndQueue(): Unit = runBlocking {
        val envFile = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue("Opt-in local server config required", envFile.exists())
        val config = envFile.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val url = config.getValue("ND_URL").replace("localhost", "10.0.2.2")
        val previousActive = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
        val added = Graph.profiles.addVerified("Player UI ${System.currentTimeMillis()}", url,
            config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
        assertTrue("Real login must succeed (details suppressed)", added is ApiResult.Success)
        val testId = (added as ApiResult.Success).value
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        var activity: android.app.Activity? = null
        try {
            assertTrue(Graph.profiles.activate(testId))
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("input keyevent KEYCODE_WAKEUP")).use { it.readBytes() }
            val launched = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = launched
            withContext(Dispatchers.Main) { launched.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

            val found = albumSongs(Graph.profiles.clientFor(testId)!!)
            assumeTrue("No suitable album on the test server", found != null)
            val (album, songs) = found!!
            val queue = songs.map { it.toSongEntity(testId) }
            withContext(Dispatchers.Main) { Graph.playback.playQueue(queue, 0, autoPlay = false) }
            awaitBridge("Album queue must reach the bridge paused, without autoplay") {
                val state = Graph.playback.state.value
                state.hasMedia && state.queue.size == songs.size && state.queue.all {
                    it.entryId.startsWith(QueueIdentity.PREFIX)
                } && state.itemKey?.startsWith(QueueIdentity.PREFIX) == true && state.canSeek && !state.playWhenReady &&
                    state.queueOriginName == null
            }
            val bridgeState = Graph.playback.state.value
            assertEquals(album.name, bridgeState.queue.first().album)

            // Player route from the mini bar on any screen.
            clickUi { it.contentDescription?.toString() == "Apri il lettore WalkTape" }
            awaitUi { it.text?.toString() == "Lettore" }
            awaitUi { it.text?.toString() == "WalkTape" }
            // Cassette label: the whole queue is one real album.
            val cassette = awaitUi { it.contentDescription?.toString()?.startsWith("Cassetta: ") == true }
            assertTrue(cassette.contentDescription?.toString()?.contains(songs[0].title ?: "") == true)
            awaitUi {
                it.text?.toString()?.equals(album.name, ignoreCase = true) == true && it.isVisibleToUser
            }
            // All chassis keys are present with accessible labels.
            awaitUi { it.contentDescription?.toString() == "Successivo" }
            awaitUi { it.contentDescription?.toString() == "Riproduci" }
            awaitUi { it.contentDescription?.toString() == "Ferma la riproduzione" }
            awaitUi { it.contentDescription?.toString() == "Mescola la coda" }
            awaitUi { it.contentDescription?.toString() == "Ripetizione: disattivata" }

            // Long-press on FF scrubs forward precisely while paused, without playing or skipping.
            val itemKey = bridgeState.itemKey
            val fastForward = awaitUi { it.contentDescription?.toString() == "Successivo" }
            ensureNodeVisible(fastForward)
            longPress(fastForward, 2_400)
            awaitBridge("FF hold must advance inside the same paused track") {
                val state = Graph.playback.state.value
                state.positionMs in 10_000..90_000 && state.itemKey == itemKey && !state.playWhenReady
            }
            val afterForward = Graph.playback.state.value.positionMs
            val rewind = awaitUi { it.contentDescription?.toString() == "Precedente o inizio brano" }
            ensureNodeVisible(rewind)
            longPress(rewind, 2_400)
            awaitBridge("REW hold must rewind within the same paused track") {
                val state = Graph.playback.state.value
                state.positionMs < afterForward - 8_000 && state.itemKey == itemKey && !state.playWhenReady
            }

            // Change track externally while FF stays held: the old gesture must be cancelled,
            // and releasing it must neither seek the new item nor trigger a next-track tap.
            longPress(awaitUi { it.contentDescription?.toString() == "Successivo" }, 1_800) {
                instrumentation.runOnMainSync { Graph.playback.next() }
            }
            awaitBridge("Track change must invalidate the held seek") {
                val state = Graph.playback.state.value
                state.queue.firstOrNull { it.isCurrent }?.mediaId == songs[1].id &&
                    state.itemKey != itemKey && state.positionMs < 3_000 && !state.playWhenReady
            }
            delay(800)
            assertTrue(Graph.playback.state.value.positionMs < 3_000)
            // Reset the test setup explicitly; next/previous taps are verified below.
            withContext(Dispatchers.Main) { Graph.playback.controller.value!!.seekTo(0, 0) }
            awaitBridge("Return after guarded hold") { Graph.playback.state.value.itemKey == itemKey }

            // A short tap on FF is next, not a scrub; back with REW to the first track.
            clickUi { it.contentDescription?.toString() == "Successivo" }
            awaitBridge("Short FF tap goes to the next track") {
                Graph.playback.state.value.itemKey != itemKey
            }
            clickUi { it.contentDescription?.toString() == "Precedente o inizio brano" }
            awaitBridge("REW tap returns to the first track") {
                Graph.playback.state.value.itemKey == itemKey
            }

            // Volume keys drive the Android media volume.
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) >=
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) - 1) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                delay(300)
            }
            val volumeBefore = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            clickUi { it.contentDescription?.toString() == "Alza il volume multimediale" }
            awaitBridge("Volume key must raise the Android media volume") {
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) == volumeBefore + 1
            }

            // Repeat key cycles the real repeat mode.
            clickUi { it.contentDescription?.toString() == "Ripetizione: disattivata" }
            awaitBridge("Repeat key must engage track repeat") { Graph.playback.state.value.repeatMode == 1 }
            clickUi { it.contentDescription?.toString() == "Ripetizione: brano" }
            awaitBridge("Repeat key must engage queue repeat") { Graph.playback.state.value.repeatMode == 2 }
            clickUi { it.contentDescription?.toString() == "Ripetizione: coda" }
            awaitBridge("Repeat key must return to off") { Graph.playback.state.value.repeatMode == 0 }

            // Shuffle key toggles the real shuffle, keeping the current track.
            clickUi { it.contentDescription?.toString() == "Mescola la coda" }
            awaitBridge("Shuffle key must shuffle the queue") {
                Graph.playback.state.value.shuffleOn &&
                    Graph.playback.state.value.queue.first().isCurrent
            }
            clickUi { it.contentDescription?.toString() == "Ripristina l\'ordine della coda" }
            awaitBridge("Shuffle key must restore the order") { !Graph.playback.state.value.shuffleOn }

            // Queue from the player.
            clickUi { it.contentDescription?.toString()?.startsWith("Apri la coda") == true }
            awaitUi { it.text?.toString() == "Coda" }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitGone { it.text?.toString() == "Coda" }

            // Enlarged cover from the real album art, when the server provides it.
            if (Graph.playback.state.value.coverArtId != null) {
                clickUi { it.contentDescription?.toString() == "Ingrandisci la copertina" }
                awaitUi { it.text?.toString() == "Tocca per chiudere" }
                clickUi { it.text?.toString() == "Tocca per chiudere" }
                awaitGone { it.text?.toString() == "Tocca per chiudere" }
            }

            // Cached playlist fixture, real songs: exercises the detail's playback action,
            // including duplicate occurrences. No playlist is created on the server.
            Graph.database.libraryDao().upsertSongs(queue)
            val fixture = listOf(queue[0], queue[1], queue[0])
            Graph.database.browseDao().replacePlaylist(
                com.walktape.app.core.db.PlaylistEntity(testId, "fixture-playlist", "Fixture playlist",
                    null, false, null, null, 3, fixture.sumOf { it.duration }, null, null, 1),
                fixture.mapIndexed { index, song -> com.walktape.app.core.db.PlaylistEntryEntity(
                    testId, "fixture-playlist", index, song.id, song.title, song.artist, song.album,
                    song.duration, song.year, song.genre) })
            val detail = withContext(Dispatchers.Main) {
                com.walktape.app.ui.screens.library.PlaylistDetailViewModel(testId, "fixture-playlist")
            }
            val collect = launch { detail.playlist.collect { } }
            try {
                awaitBridge("Playlist detail metadata must load") { detail.playlist.value != null }
                withContext(Dispatchers.Main) { detail.play(1) }
                awaitBridge("Playlist action must keep order, duplicates and origin") {
                    val snapshot = Graph.playback.state.value
                    snapshot.queueOriginName == "Fixture playlist" && snapshot.queue.size == 3 &&
                        snapshot.queue.map { it.mediaId } == fixture.map { it.id } &&
                        snapshot.queue[1].isCurrent && snapshot.isPlaying
                }
                withContext(Dispatchers.Main) { Graph.playback.togglePlayPause() }
                awaitBridge("Playlist pauses normally") { !Graph.playback.state.value.playWhenReady }
                awaitUi { it.text?.toString() == "FIXTURE PLAYLIST" }
            } finally { collect.cancel() }

            // The player's toolbar returns to the previous screen with its mini bar.
            clickUi { it.contentDescription?.toString() == "Indietro" }
            awaitUi { it.contentDescription?.toString() == "Apri il lettore WalkTape" }
        } finally {
            withContext(Dispatchers.Main) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume.coerceIn(
                0, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
            if (previousActive != null) Graph.profiles.activate(previousActive)
            Graph.profiles.remove(testId)
        }
    }

    private suspend fun awaitBridge(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (withContext(Dispatchers.Main) { condition() }) return
            delay(250)
        }
        throw AssertionError(message)
    }

    private fun awaitUi(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            // Dynamic nodes go stale across recompositions: refresh the accessibility cache.
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val wait = findNode(root) { it.text?.toString() == "Wait" }
                if (wait != null) performClick(wait)
                else findNode(root, predicate)?.let { return it }
            }
            Thread.sleep(300)
        }
        throw AssertionError("Player UI control not found")
    }

    private fun awaitGone(predicate: (AccessibilityNodeInfo) -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return
            if (findNode(root, predicate) == null) return
            Thread.sleep(250)
        }
        throw AssertionError("Player UI control still visible")
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

    private fun performClick(node: AccessibilityNodeInfo) {
        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
            assertTrue("Player UI control must be clickable", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            return
        }
        var clickable = node
        while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
        assertTrue("Player UI control must be clickable", clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    /** The player screen scrolls; bring the node on screen before injecting touch coordinates. */
    private fun ensureNodeVisible(node: AccessibilityNodeInfo) {
        val screen = Rect()
        instrumentation.uiAutomation.rootInActiveWindow?.getBoundsInScreen(screen) ?: return
        repeat(6) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.top >= screen.top && bounds.bottom <= screen.bottom) return
            val scrollable = findNode(instrumentation.uiAutomation.rootInActiveWindow ?: return) {
                it.isScrollable && it.className?.toString() == "android.widget.ScrollView"
            } ?: return
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(250)
        }
    }

    /** Press-and-hold injection for the mechanical long-press (down, moves, up after [durationMs]). */
    private fun longPress(node: AccessibilityNodeInfo, durationMs: Long, duringHold: (() -> Unit)? = null) {
        ensureNodeVisible(node)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val start = SystemClock.uptimeMillis()
        fun inject(action: Int) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN)
        val end = start + durationMs
        var changed = false
        while (SystemClock.uptimeMillis() < end) {
            Thread.sleep(90)
            inject(MotionEvent.ACTION_MOVE)
            if (!changed && SystemClock.uptimeMillis() - start >= 900) {
                changed = true
                duringHold?.invoke()
            }
        }
        inject(MotionEvent.ACTION_UP)
    }
}
