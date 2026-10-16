package com.walktape.app.playback

import android.content.ComponentName
import android.media.browse.MediaBrowser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.playback.browse.AutoBrowseIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Framework/legacy browse contract, including a real missing-profile state; not DHU. */
@RunWith(AndroidJUnit4::class)
class Wave10LegacyBrowserTest {
    @Test fun legacyRootDoesNotDeadlockAndMissingProfileIsAnHonestSetupItem(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Graph.database.profileDao().active()?.id
        val completed = CountDownLatch(1)
        val phase = java.util.concurrent.atomic.AtomicReference("connecting")
        var children: List<MediaBrowser.MediaItem>? = null
        lateinit var browser: MediaBrowser
        var created = false
        try {
            Graph.database.profileDao().clearActive()
            browser = withContext(Dispatchers.Main) {
                MediaBrowser(context, ComponentName(context, PlaybackService::class.java),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            phase.set("connected; subscribing")
                            browser.subscribe(browser.root, object : MediaBrowser.SubscriptionCallback() {
                                override fun onChildrenLoaded(parentId: String, items: MutableList<MediaBrowser.MediaItem>) {
                                    phase.set("loaded"); children = items; completed.countDown()
                                }
                                override fun onError(parentId: String) { phase.set("subscription failed"); completed.countDown() }
                            })
                        }
                        override fun onConnectionFailed() { phase.set("connection failed"); completed.countDown() }
                    }, null).also { it.connect() }
            }
            created = true
            val finished = completed.await(25, TimeUnit.SECONDS)
            assertTrue("Legacy root/subscribe finishes: ${phase.get()}", finished)
            assertEquals(1, children?.size)
            assertEquals("wtb-setup", children!!.single().mediaId)
            assertFalse(children!!.single().isPlayable)
            withContext(Dispatchers.Main) { assertEquals(AutoBrowseIds.ROOT, browser.root) }
        } finally {
            if (created) withContext(Dispatchers.Main) { browser.disconnect() }
            if (previous != null) Graph.profiles.activate(previous)
        }
    }
}
