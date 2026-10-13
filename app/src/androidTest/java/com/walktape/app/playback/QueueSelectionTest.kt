package com.walktape.app.playback

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Queue actions from the library multi-selection, against the locally indexed "UI check" profile
 * (opt-in marker like the wave-04 UI regression). Reading the index is local; playback needs the
 * real server reachable from that saved profile. Never prints credentials or authenticated URLs.
 */
@RunWith(AndroidJUnit4::class)
class QueueSelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun selectedAlbumsQueueAllTracksInDiscTrackOrder() = runBlocking {
        assumeTrue("Manual UI seed required", File("/data/local/tmp/wt-seed").exists())
        val profile = Graph.database.profileDao().active()!!
        val previousSort = Graph.settingsRepository.librarySorts()["sort_album"] ?: "name"
        Graph.settingsRepository.setLibrarySort("sort_album", "name")
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            val albums = (Graph.database.browseDao().albums(com.walktape.app.core.library.BrowseQueries.list(
                com.walktape.app.core.library.BrowseKind.ALBUM, profile.id, com.walktape.app.core.library.LibraryFilter(), "name"))
                .load(PagingSource.LoadParams.Refresh<Int>(null, 10, false)) as PagingSource.LoadResult.Page).data.take(2)
            assertTrue(albums.size == 2)
            val expected = albums.flatMap { Graph.database.libraryDao().songsInAlbums(profile.id, listOf(it.id)) }.map { it.id }
            assertTrue(expected.isNotEmpty())
            instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitBridge("Connection ready") { it.connected }
            withContext(Dispatchers.Main) { Graph.playback.controller.value!!.let { it.pause(); it.clearMediaItems(); it.shuffleModeEnabled = false } }
            clickTab("Album")
            for ((index, album) in albums.withIndex()) {
                val node = awaitNode { it.viewIdResourceName?.endsWith("library-album-${album.id}") == true }
                performOnClickable(node, if (index == 0) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK)
            }
            awaitNode { it.text?.toString() == "Selezionati: 2" }
            performOnClickable(awaitNode { it.text?.toString() == "Aggiungi in coda" }, AccessibilityNodeInfo.ACTION_CLICK)
            awaitBridge("Every selected album track must be queued, still paused") {
                it.queue.map { row -> row.mediaId } == expected && !it.playWhenReady
            }
        } finally {
            withContext(Dispatchers.Main) { Graph.playback.controller.value?.let { it.pause(); it.clearMediaItems() } }
            Graph.settingsRepository.setLibrarySort("sort_album", previousSort)
        }
    }

    @Test fun selectionQueueActionsAppendNextAndNow() = runBlocking {
        assumeTrue("Manual UI seed required", File("/data/local/tmp/wt-seed").exists())
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val profile = Graph.database.profileDao().active()!!
        val previousSort = Graph.settingsRepository.librarySorts()["sort_song"] ?: "title"
        Graph.settingsRepository.setLibrarySort("sort_song", "title")
        try {
        val params = PagingSource.LoadParams.Refresh<Int>(null, 10, false)
        val songs = (Graph.database.browseDao().songs(com.walktape.app.core.library.BrowseQueries.list(
            com.walktape.app.core.library.BrowseKind.SONG, profile.id, com.walktape.app.core.library.LibraryFilter(), "title"))
            .load(params) as PagingSource.LoadResult.Page<Int, com.walktape.app.core.db.SongEntity>).data
        assertTrue("Indexed profile must expose songs", songs.size >= 3)
        val first = songs[0]
        val second = songs[1]
        val third = songs[2]
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        withContext(Dispatchers.Main) {
            Graph.playback.controller.value?.let { it.pause(); it.clearMediaItems(); it.shuffleModeEnabled = false }
        }
        awaitNode { it.text?.toString() == "La mia musica" }
        clickTab("Brani")
        performOnClickable(songNode(first.id), AccessibilityNodeInfo.ACTION_LONG_CLICK)
        awaitNode { it.text?.toString() == "Selezionati: 1" }
        performOnClickable(songNode(second.id), AccessibilityNodeInfo.ACTION_CLICK)
        awaitNode { it.text?.toString() == "Selezionati: 2" }
        performOnClickable(awaitNode { it.text?.toString() == "Aggiungi in coda" }, AccessibilityNodeInfo.ACTION_CLICK)
        awaitBridge("Append from empty queue must remain paused") { state ->
            state.queue.size == 2 && !state.playWhenReady && state.queue.first().mediaId == first.id &&
                state.queue[1].mediaId == second.id
        }
        withContext(Dispatchers.Main) { Graph.playback.toggleShuffle() }
        awaitBridge("Shuffle before insertion") { it.shuffleOn }

        performOnClickable(songNode(third.id), AccessibilityNodeInfo.ACTION_LONG_CLICK)
        awaitNode { it.text?.toString() == "Selezionati: 1" }
        performOnClickable(awaitNode { it.text?.toString() == "Dopo il corrente" }, AccessibilityNodeInfo.ACTION_CLICK)
        awaitBridge("Play next must insert after the current item") { state ->
            state.queue.size == 3 && state.queue[0].isCurrent &&
                state.queue[1].mediaId == third.id && !state.playWhenReady
        }

        performOnClickable(songNode(second.id), AccessibilityNodeInfo.ACTION_LONG_CLICK)
        awaitNode { it.text?.toString() == "Selezionati: 1" }
        performOnClickable(awaitNode { it.text?.toString() == "Riproduci ora" }, AccessibilityNodeInfo.ACTION_CLICK)
        awaitBridge("Play now must jump to the inserted item and play") { state ->
            state.queue.size == 4 && state.queue.any { it.mediaId == second.id && it.isCurrent } &&
                state.isPlaying
        }
        withContext(Dispatchers.Main) {
            Graph.playback.togglePlayPause()
            Graph.playback.controller.value?.clearMediaItems()
        }
        awaitBridge("Final clear") { state -> !state.playWhenReady && state.queue.isEmpty() }
        } finally {
            withContext(Dispatchers.Main) { Graph.playback.controller.value?.let { it.pause(); it.clearMediaItems() } }
            Graph.settingsRepository.setLibrarySort("sort_song", previousSort)
        }
    }

    private fun songNode(id: String) = awaitNode { it.viewIdResourceName?.endsWith("library-song-$id") == true }

    private suspend fun awaitBridge(message: String, condition: (com.walktape.app.playback.PlaybackSnapshot) -> Boolean) {
        val matched = withTimeoutOrNull(30_000) { Graph.playback.state.first { condition(it) } }
        assertTrue(message, matched != null)
    }

    private fun clickTab(text: String) {
        awaitNode { it.text?.toString() == "La mia musica" }
        val deadline = SystemClock.uptimeMillis() + 60000
        var attempt = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: run { SystemClock.sleep(500); continue }
            val wait = find(root) { it.packageName?.toString() == "android" && it.text?.toString() == "Wait" }
            if (wait != null) {
                wait.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                SystemClock.sleep(800)
                continue
            }
            find(root) { node ->
                node.text?.toString()?.equals(text, ignoreCase = true) == true &&
                    node.isVisibleToUser && hasClickableAncestor(node)
            }?.let { performOnClickable(it, AccessibilityNodeInfo.ACTION_CLICK); return }
            find(root) { it.viewIdResourceName?.endsWith("library-tabs") == true }?.let { tabs ->
                find(tabs) { node ->
                    node.text?.toString()?.equals(text, ignoreCase = true) == true && hasClickableAncestor(node)
                }?.let { performOnClickable(it, AccessibilityNodeInfo.ACTION_CLICK); return }
                val direction = if (attempt % 12 < 6) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                swipeTabStrip(tabs, direction)
            }
            attempt++
            SystemClock.sleep(500)
        }
        error("Expected tab '$text' did not appear")
    }

    private fun swipeTabStrip(tabs: AccessibilityNodeInfo, direction: Int) {
        val screen = android.graphics.Rect()
        tabs.getBoundsInScreen(screen)
        val start = screen.left + screen.width() * (if (direction == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) 0.15f else 0.85f)
        val end = screen.left + screen.width() * (if (direction == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) 0.85f else 0.15f)
        val y = screen.exactCenterY()
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float) {
            val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(android.view.MotionEvent.ACTION_DOWN, start)
        for (step in 1..6) { SystemClock.sleep(35); send(android.view.MotionEvent.ACTION_MOVE, start + (end - start) * step / 6) }
        send(android.view.MotionEvent.ACTION_UP, end)
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null) { if (target.isClickable) return true; target = target.parent }
        return false
    }

    private fun performOnClickable(node: AccessibilityNodeInfo, action: Int) {
        var target: AccessibilityNodeInfo? = node
        while (target != null) {
            if ((action == AccessibilityNodeInfo.ACTION_CLICK && target.isClickable) ||
                (action == AccessibilityNodeInfo.ACTION_LONG_CLICK && target.isLongClickable)) {
                val deadline = SystemClock.uptimeMillis() + 5000
                do {
                    target.refresh()
                    if (target.isEnabled && target.performAction(action)) return
                    SystemClock.sleep(200)
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError("Accessible action must succeed (enabled=${target.isEnabled}, visible=${target.isVisibleToUser})")
            }
            target = target.parent
        }
        error("No accessible action target")
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val systemDialog = find(root) { it.packageName?.toString() == "android" &&
                (it.text?.toString()?.contains("isn't responding") == true) }
            if (systemDialog != null) {
                find(root) { it.text?.toString() == "Wait" }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                SystemClock.sleep(500)
                continue
            }
            find(root, predicate)?.let { return it }
            SystemClock.sleep(250)
        }
        error("Expected UI element did not appear")
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        repeat(node.childCount) { find(node.getChild(it), predicate)?.let { found -> return found } }
        return null
    }
}
