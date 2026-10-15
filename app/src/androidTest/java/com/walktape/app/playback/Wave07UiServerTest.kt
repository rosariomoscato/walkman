package com.walktape.app.playback

import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import com.walktape.app.core.net.*
import com.walktape.app.ui.components.QueueListContent
import com.walktape.app.ui.screens.library.PlaylistDetailScreen
import com.walktape.app.ui.screens.library.LibraryScreen
import com.walktape.app.ui.screens.library.LibraryViewModel
import com.walktape.app.ui.screens.library.LibraryTab
import com.walktape.app.ui.screens.player.SongDetailsSheet
import com.walktape.app.ui.theme.WalkTapeTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Actual Compose actions and actual Navidrome persistence; only dedicated temporary playlists. */
@RunWith(AndroidJUnit4::class)
class Wave07UiServerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            result.add(node)
            repeat(node.childCount) { i -> node.getChild(i)?.let(::walk) }
        }
        walk(root)
        return result
    }
    private suspend fun node(label: String = "expected content", predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        try {
            return withTimeout(30_000) {
                while (true) {
                    val current = nodes()
                    if (current.any { it.text?.toString()?.contains("isn't responding") == true }) {
                        current.firstOrNull { it.text?.toString() == "Wait" && it.isClickable }
                            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    current.firstOrNull { it.isVisibleToUser && predicate(it) }?.let { return@withTimeout it }
                    delay(200)
                }
                error("unreachable")
            }
        } catch (_: TimeoutCancellationException) {
            throw AssertionError("UI node not found: $label; visible labels: " + nodes().filter { it.isVisibleToUser }
                .mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }.joinToString(" | "))
        }
    }
    private suspend fun click(text: String, description: Boolean = false) {
        var target: AccessibilityNodeInfo? = node(text) {
            val matches = if (description) it.contentDescription?.toString() == text else it.text?.toString() == text
            var parent: AccessibilityNodeInfo? = it
            while (parent != null && !parent.isClickable) parent = parent.parent
            matches && parent?.isEnabled == true
        }
        while (target != null && !target.isClickable) target = target.parent
        assertTrue("UI action must be clickable: $text",target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        delay(250)
    }
    private suspend fun input(value: String) {
        val field = node { it.isEditable }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)
        }))
    }
    private suspend fun until(predicate: suspend () -> Boolean) {
        withTimeout(40_000) { while (!predicate()) delay(200) }
    }

    @Test fun playlistEditsQueueSaveShuffleMixtapeAndServerLyrics(): Unit = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue(env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        val previous = Graph.database.profileDao().active()?.takeUnless { it.name.startsWith("wt-test-ui-") }?.id
        val prefix = "wt-test-ui-${UUID.randomUUID()}"
        val added = Graph.profiles.addVerified(prefix, config.getValue("ND_URL").replace("localhost","10.0.2.2"),
            config.getValue("ND_USER"),config.getValue("ND_PASS"),true)
        assertTrue("Dedicated login must succeed", added is ApiResult.Success)
        val profile = (added as ApiResult.Success).value
        val client = Graph.profiles.clientFor(profile)!!
        var activity: MainActivity? = null
        try {
            // Recover only our dedicated fixtures after a process-crash test (finally cannot run then).
            val abandoned = (client.playlists() as ApiResult.Success).value.filter { it.name.startsWith("wt-test-ui-") }
            abandoned.forEach { assertTrue(client.deletePlaylist(it.id) is ApiResult.Success) }
            Graph.profiles.profiles.first().filter { it.id != profile && it.name.startsWith("wt-test-ui-") }
                .forEach { Graph.profiles.remove(it.id) }
            Graph.profiles.activate(profile)
            val newest = (client.albumList("newest",1,0) as ApiResult.Success).value.first()
            val songs = (client.album(newest.id) as ApiResult.Success).value.songs.take(2)
            assertEquals(2,songs.size)
            val queueSongs = listOf(songs[0],songs[1],songs[0]).map { it.toSongEntity(profile) }
            Graph.database.libraryDao().upsertSongs(queueSongs.distinctBy { it.id })
            assertNull(Graph.playlistRepository().create(profile,prefix,queueSongs.map { it.id }))
            val playlist = (client.playlists() as ApiResult.Success).value.single { it.name == prefix }
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP"))
                .use { it.readBytes() }
            val launched = instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = launched
            val deleted = AtomicBoolean(false)
            val library = withContext(Dispatchers.Main) { LibraryViewModel().apply { setTab(LibraryTab.SONGS) } }
            withContext(Dispatchers.Main) {
                launched.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                launched.setContent { WalkTapeTheme {
                    LibraryScreen({}, {}, {}, {}, {}, viewModel = library)
                } }
            }
            node("indexed song") { it.text?.toString() == songs[0].title }
            withContext(Dispatchers.Main) {
                library.toggleSelection(songs[0].id)
                library.toggleSelection(songs[1].id)
            }
            click("Aggiungi a playlist")
            click(prefix)
            until { (client.playlist(playlist.id) as ApiResult.Success).value.entries.size == 5 }
            val selectedAdded = (client.playlist(playlist.id) as ApiResult.Success).value.entries
            assertEquals(queueSongs.map { it.id },selectedAdded.take(3).map { it.id })
            assertEquals(songs.map { it.id }.toSet(),selectedAdded.drop(3).map { it.id }.toSet())
            assertTrue(client.updatePlaylist(playlist.id,songIndexesToRemove = listOf(3,4)) is ApiResult.Success)
            assertNull(com.walktape.app.core.library.PlaylistIndex(Graph.database.browseDao()).refresh(profile,client))
            withContext(Dispatchers.Main) {
                launched.setContent { WalkTapeTheme { PlaylistDetailScreen(playlist.id, onBack = { deleted.set(true) }) } }
            }
            click("Rinomina",description = true)
            input("$prefix-renamed")
            click("Rinomina")
            until { (client.playlist(playlist.id) as ApiResult.Success).value.playlist.name == "$prefix-renamed" }
            click("Azioni del brano in coda",description = true)
            click("Sposta giù")
            until { (client.playlist(playlist.id) as ApiResult.Success).value.entries.map { it.id } ==
                listOf(songs[1].id,songs[0].id,songs[0].id) }
            click("Azioni del brano in coda",description = true)
            click("Rimuovi dalla playlist")
            until { (client.playlist(playlist.id) as ApiResult.Success).value.entries.map { it.id } == listOf(songs[0].id,songs[0].id) }
            click("Elimina",description = true)
            click("Elimina")
            until { deleted.get() }
            assertTrue((client.playlists() as ApiResult.Success).value.none { it.id == playlist.id })

            withContext(Dispatchers.Main) {
                Graph.playback.connect()
                Graph.playback.playQueue(queueSongs,0,autoPlay = false)
                launched.setContent { WalkTapeTheme { QueueListContent() } }
            }
            until { withContext(Dispatchers.Main) { Graph.playback.state.value.canShuffle } }
            withContext(Dispatchers.Main) { Graph.playback.toggleShuffle() }
            until { Graph.playback.state.value.shuffleOn }
            click("Nome della mixtape")
            input("$prefix-saved")
            click("Salva")
            until { Graph.playback.state.value.mixtapeName == "$prefix-saved" }
            // Capture the displayed order at save time, after service normalization/shuffle ACK,
            // not the controller's provisional timeline immediately after toggling shuffle.
            until { Graph.playback.state.value.canEditQueue }
            val expected = Graph.playback.state.value.queue.map { it.mediaId }
            click("Salva coda come playlist")
            click("Crea")
            var saved: PlaylistDto? = null
            until {
                saved = (client.playlists() as ApiResult.Success).value.firstOrNull { it.name == "$prefix-saved" }
                saved != null
            }
            assertEquals(expected,(client.playlist(saved!!.id) as ApiResult.Success).value.entries.map { it.id })
            Graph.queues.flush()
            assertEquals("$prefix-saved",Graph.queues.load(profile)!!.mixtapeName)
            assertFalse(Graph.playback.state.value.playWhenReady)

            val lyricId = "s4O9TKx8oDM7Uh2IkntD09"
            val lyricSong = (client.request("getSong",listOf("id" to lyricId)) as ApiResult.Success).value.obj("song")!!
            Graph.database.libraryDao().upsertSongs(listOf(parseSong(lyricSong).toSongEntity(profile)))
            withContext(Dispatchers.Main) {
                launched.setContent { WalkTapeTheme { SongDetailsSheet(profile,lyricId) {} } }
            }
            node { it.text?.toString() == "Dettagli brano" }
            until { Graph.database.lyricsDao().get(profile,lyricId)?.lines?.let { it != "[]" } == true }
            node { it.text?.toString() == "Testo" }
        } finally {
            withContext(Dispatchers.Main) { Graph.playback.controller.value?.pause(); activity?.finish() }
            val playlists = client.playlists()
            if (playlists is ApiResult.Success) playlists.value.filter { it.name.startsWith(prefix) }.forEach {
                assertTrue(client.deletePlaylist(it.id) is ApiResult.Success)
            }
            if (previous != null) Graph.profiles.activate(previous)
            Graph.profiles.remove(profile)
        }
    }
}
