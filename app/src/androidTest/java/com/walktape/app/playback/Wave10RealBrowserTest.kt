package com.walktape.app.playback

import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.net.ApiResult
import com.walktape.app.playback.browse.AutoBrowseIds
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual MP3/FLAC from the test Navidrome account, played through the browse-id contract. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Wave10RealBrowserTest {
    @Test fun browseIdsAndVoiceRequestResolveToRealMp3AndFlac(): Unit = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue("Real server credentials supplied locally", env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Graph.database.profileDao().active()?.id
        var profile: Long? = null
        var browser: MediaBrowser? = null
        suspend fun <T> main(block: () -> T): T = withContext(Dispatchers.Main) { block() }
        try {
            Graph.networkGate.awaitReady()
            val added = Graph.profiles.addVerified("W10 real browser ${System.nanoTime()}",
                config.getValue("ND_URL").replace("localhost", "10.0.2.2"),
                config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
            assertTrue("Real test login", added is ApiResult.Success)
            val id = (added as ApiResult.Success).value; profile = id
            Graph.profiles.activate(id)
            val candidates = playbackCandidates(Graph.profiles.clientFor(id)!!)
            assertEquals(2, candidates.size)
            Graph.database.libraryDao().upsertSongs(candidates.map { it.toSongEntity(id) })
            val b = main { MediaBrowser.Builder(context, SessionToken(context,
                ComponentName(context, PlaybackService::class.java))).buildAsync() }.get(30, TimeUnit.SECONDS)
            browser = b
            for (song in candidates) {
                val item = main { b.getItem(AutoBrowseIds.song(id, song.id)) }.get(30, TimeUnit.SECONDS).value!!
                main { b.setMediaItem(item); b.prepare(); b.play() }
                withTimeout(45_000) {
                    while (!main { b.isPlaying && b.currentPosition > 300 }) delay(100)
                }
                main {
                    assertNull(b.playerError)
                    assertEquals(song.id, b.currentMediaItem?.mediaMetadata?.extras?.getString(QueueIdentity.SONG))
                    assertTrue(b.currentMediaItem!!.mediaId.startsWith(QueueIdentity.PREFIX))
                    b.pause()
                }
            }
            val song = candidates.first()
            val voice = MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder()
                .setSearchQuery(song.title).build()).build()
            main { b.setMediaItem(voice); b.prepare(); b.play() }
            withTimeout(45_000) {
                while (!main { b.isPlaying && b.currentPosition > 300 }) delay(100)
            }
            main { assertNotNull(b.currentMediaItem?.mediaMetadata?.extras?.getString(QueueIdentity.SONG)); b.pause() }
        } finally {
            main { browser?.pause(); browser?.stop(); browser?.clearMediaItems(); browser?.release() }
            context.stopService(Intent(context, PlaybackService::class.java))
            if (previous != null) Graph.profiles.activate(previous)
            profile?.let { Graph.profiles.remove(it) }
            Graph.queues.flush()
        }
    }
}
