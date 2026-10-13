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
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
        val automation=instrumentation.uiAutomation
        automation.serviceInfo=automation.serviceInfo.apply {
            flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val profile = Graph.database.profileDao().active()!!
        Graph.settingsRepository.setLibrarySort("sort_album", "name")
        val dao = Graph.database.libraryDao()
        val fixtureId = "__walktape_ui_fixture"
        val browse=Graph.database.browseDao()
        val fixtureCreated=browse.playlistCount(profile.id)==0
        if (fixtureCreated) browse.replacePlaylist(
            PlaylistEntity(profile.id,fixtureId,"Fixture playlist (test UI)","fixture",false,null,null,2,200,null,null,1),
            listOf(PlaylistEntryEntity(profile.id,fixtureId,0,"fixture-song","Fixture first",null,null,100,null,null),
                PlaylistEntryEntity(profile.id,fixtureId,1,"fixture-song","Fixture repeat",null,null,100,null,null)))
        try {
        val params = PagingSource.LoadParams.Refresh<Int>(null, 10, false)
        val albums = (dao.albums(profile.id, "name", "%").load(params) as PagingSource.LoadResult.Page).data
        val artists = (dao.artists(profile.id, "%").load(params) as PagingSource.LoadResult.Page).data
        val songs = (dao.songs(profile.id, "title", "%").load(params) as PagingSource.LoadResult.Page).data
        val genres = (dao.genres(profile.id, "%").load(params) as PagingSource.LoadResult.Page).data
        instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitText("La mia musica")
        clickTab("Album")
        awaitText(albums.first().name)
        println("wave04-ui: albums")
        clickTab("Artisti")
        awaitText(artists.first().name)
        println("wave04-ui: artists")
        clickTab("Brani")
        awaitText(songs.first().title)
        println("wave04-ui: songs")
        clickTab("Generi")
        awaitText(genres.first().name)
        println("wave04-ui: genres")
        clickTab("Album")
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
        kotlinx.coroutines.withTimeout(10000) {
            while (Graph.settingsRepository.librarySorts()["sort_album"] != "year") kotlinx.coroutines.delay(100)
        }
        println("wave04-ui: search clear, selection and sorting")
        clickTab("Panoramica")
        awaitText("Continua ascolto")
        println("wave04-ui: overview")
        clickTab("Playlist")
        val playlistPage=browse.playlists(BrowseQueries.list(BrowseKind.PLAYLIST,profile.id,LibraryFilter(),"name"))
            .load(PagingSource.LoadParams.Refresh<Int>(null,80,false)) as PagingSource.LoadResult.Page
        if (playlistPage.data.isNotEmpty()) {
            val p=playlistPage.data.first()
            awaitText(p.name)
            clickText(p.name)
            awaitText("Playlist in lettura. Le modifiche saranno disponibili nella fase dedicata.")
            if (fixtureCreated) {
                awaitText("Fixture first")
                awaitText("Fixture repeat")
            }
            clickDescription("Indietro")
            println("wave04-ui: playlist detail")
        }
        clickTab("Brani")
        clickText("Disponibili offline")
        awaitText("Nessun risultato per questa ricerca o questi filtri.")
        clickText("Disponibili offline")
        clickTab("Tutti")
        val searchField=awaitNode { it.className?.toString()=="android.widget.EditText" }
        assertTrue(searchField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"adele")
        }))
        awaitText("Adele")
        println("wave04-ui: offline filter and unified search")
        clickDescription("Cancella ricerca")
        clickTab("Brani")
        val genre=browse.genreNames(profile.id).first().firstOrNull()
        val year=browse.years(profile.id).first().firstOrNull()
        if (genre!=null && year!=null) {
            clickText("Genere")
            clickText(genre)
            clickText("Anno")
            clickText(year.toString())
            val expected=Graph.database.openHelper.readableDatabase.query(
                "SELECT title FROM song WHERE profileId=? AND genre=? AND year=? ORDER BY title COLLATE NOCASE,id LIMIT 1",
                arrayOf<Any>(profile.id,genre,year)).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            awaitText(expected ?: "Nessun risultato per questa ricerca o questi filtri.")
            println("wave04-ui: genre and year filters")
        }
        } finally {
            if (fixtureCreated) browse.deletePlaylist(profile.id,fixtureId)
        }
    }

    private fun awaitText(text: String) = awaitNode { it.text?.toString()?.equals(text,ignoreCase=true) == true }
    private fun clickText(text: String) = performOnClickable(awaitText(text), AccessibilityNodeInfo.ACTION_CLICK)
    private fun clickDescription(text: String) = performOnClickable(awaitNode { it.contentDescription?.toString() == text }, AccessibilityNodeInfo.ACTION_CLICK)

    private fun clickTab(text: String) {
        awaitText("La mia musica")
        val deadline = SystemClock.uptimeMillis() + 60000
        var attempt = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root == null) { SystemClock.sleep(500); continue }
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
        val screen=android.graphics.Rect()
        tabs.getBoundsInScreen(screen)
        val start=screen.left+screen.width()*(if(direction==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) 0.15f else 0.85f)
        val end=screen.left+screen.width()*(if(direction==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) 0.85f else 0.15f)
        val y=screen.exactCenterY()
        val down=SystemClock.uptimeMillis()
        fun send(action:Int,x:Float) {
            val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event,true)
            event.recycle()
        }
        send(android.view.MotionEvent.ACTION_DOWN,start)
        for (step in 1..6) { SystemClock.sleep(35); send(android.view.MotionEvent.ACTION_MOVE,start+(end-start)*step/6) }
        send(android.view.MotionEvent.ACTION_UP,end)
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo?=node
        while (target!=null) { if (target.isClickable) return true; target=target.parent }
        return false
    }

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
            val root=instrumentation.uiAutomation.rootInActiveWindow
            val systemDialog=find(root) { it.packageName?.toString()=="android" &&
                (it.text?.toString()?.contains("Quickstep isn't responding")==true ||
                    it.text?.toString()?.contains("System UI isn't responding")==true) }
            if (systemDialog!=null) {
                find(root) { it.text?.toString()=="Wait" }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                println("wave04-ui: system ANR dialog observed; waiting")
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
