package com.walktape.app.core.library

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun tabsDetailsSearchAndSelectionRemainUsable(): Unit = runBlocking {
        assumeTrue("Manual UI seed required", File("/data/local/tmp/wt-seed").exists())
        val profile = Graph.database.profileDao().active()!!
        Graph.settingsRepository.setLibrarySort("sort_album", "name")
        val dao = Graph.database.libraryDao()
        val params = PagingSource.LoadParams.Refresh<Int>(null, 10, false)
        val albums = (dao.albums(profile.id, "name", "%").load(params) as PagingSource.LoadResult.Page).data
        val artists = (dao.artists(profile.id, "%").load(params) as PagingSource.LoadResult.Page).data
        val songs = (dao.songs(profile.id, "title", "%").load(params) as PagingSource.LoadResult.Page).data
        val genres = (dao.genres(profile.id, "%").load(params) as PagingSource.LoadResult.Page).data
        instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitText(albums.first().name)
        println("wave04-ui: albums")
        clickText("Artisti")
        awaitText(artists.first().name)
        println("wave04-ui: artists")
        clickText("Brani")
        awaitText(songs.first().title)
        println("wave04-ui: songs")
        clickText("Generi")
        awaitText(genres.first().name)
        println("wave04-ui: genres")
        clickText("Album")
        awaitText(albums.first().name)
        clickText(albums.first().name)
        awaitText(albums.first().artist ?: albums.first().name)
        println("wave04-ui: album detail")
        clickDescription("Indietro")
        awaitText(albums.first().name)
        val field = awaitNode { it.className?.toString() == "android.widget.EditText" }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "adele")
        }))
        awaitText("Adele • 2011")
        println("wave04-ui: search")
        clickDescription("Cancella ricerca")
        awaitText(albums.first().name)
        performOnClickable(awaitNode { it.text?.toString() == albums.first().name }, AccessibilityNodeInfo.ACTION_LONG_CLICK)
        awaitText("Selezionati: 1")
        clickDescription("Annulla selezione")
        clickText("Titolo")
        clickText("Anno")
        awaitText("Anno")
        println("wave04-ui: search clear, selection and sorting")
    }

    private fun awaitText(text: String) = awaitNode { it.text?.toString() == text }
    private fun clickText(text: String) = performOnClickable(awaitText(text), AccessibilityNodeInfo.ACTION_CLICK)
    private fun clickDescription(text: String) = performOnClickable(awaitNode { it.contentDescription?.toString() == text }, AccessibilityNodeInfo.ACTION_CLICK)

    private fun performOnClickable(node: AccessibilityNodeInfo, action: Int) {
        var target: AccessibilityNodeInfo? = node
        while (target != null) {
            if ((action == AccessibilityNodeInfo.ACTION_CLICK && target.isClickable) ||
                (action == AccessibilityNodeInfo.ACTION_LONG_CLICK && target.isLongClickable)) {
                assertTrue("Accessible action must succeed", target.performAction(action))
                return
            }
            target = target.parent
        }
        error("No accessible action target")
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow, predicate)?.let { return it }
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
