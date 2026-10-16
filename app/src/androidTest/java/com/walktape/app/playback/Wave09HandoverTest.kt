package com.walktape.app.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.ProfileEntity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real decoders/output buffers with synthetic audio; no hardware-output claim. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Wave09HandoverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private inner class Rig(count: Int = 2, timer: SleepTimer? = null, dsp: Boolean = false) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val dir = File(context.cacheDir, "handover-${System.nanoTime()}")
        val captures = mutableListOf<CapturingAudioOutput>()
        val decks = mutableListOf<ExoPlayer>()
        val items = List(count) { index ->
            val file = TestAudioEngine.writeWav(dir, "$index.wav", TestAudioEngine.sine(64000, 6000, 220 + index * 110))
            MediaItem.Builder().setUri(android.net.Uri.fromFile(file)).setMediaId("queue/occurrence-$index")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Track $index").setDurationMs(8000)
                    .setExtras(Bundle().apply {
                        putString(QueueIdentity.SONG, "duplicate-song")
                        putString(SleepTimer.ALBUM_ID, if (index < 2) "album-one" else "album-two")
                    }).build()).build()
        }
        var status = CrossfadeCoordinator.Status()
        private val enableDsp = dsp
        fun deck(): ExoPlayer {
            val capture = CapturingAudioOutput().also { captures.add(it) }
            return TestAudioEngine(context, capture,
                gain = com.walktape.app.playback.dsp.ReplayGainProcessor().apply { enabled = enableDsp; targetGainDb = -6.0206f },
                tape = com.walktape.app.playback.dsp.TapeProcessor().apply { enabled = enableDsp }
            ).newPlayer().also { decks.add(it) }
        }
        val engine = CrossfadeCoordinator(scope, deck(), ::deck,
            enabled = { true }, durationMs = { 2000 },
            transitionAllowed = { index -> timer?.allowsOverlap(index) ?: true }, report = { status = it })
        val player = engine.player
        private val sleepTimer = timer
        init { player.setMediaItems(items); timer?.attach(player); engine.start() }
        fun close() { sleepTimer?.detach(); engine.release(); scope.cancel(); dir.deleteRecursively() }
    }

    private suspend fun await(message: String, predicate: suspend () -> Boolean) {
        try { withTimeout(25_000) { while (!predicate()) delay(50) } }
        catch (e: TimeoutCancellationException) { throw AssertionError(message, e) }
    }

    @Test fun incomingDecoderOutputAndListenerIdentitySurvivePromotion(): Unit = runBlocking {
        val rig = withContext(Dispatchers.Main) { Rig() }
        try {
            val identity = rig.player
            var automatic = 0
            withContext(Dispatchers.Main) {
                rig.player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { assertSame(identity, player) }
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) automatic++
                    }
                })
                rig.player.prepare(); rig.player.seekTo(0, 5600); rig.player.play()
            }
            await("incoming became audible") { withContext(Dispatchers.Main) { rig.status.incomingPlaying } }
            val incoming = withContext(Dispatchers.Main) { rig.decks.last() }
            await("logical player promoted") { withContext(Dispatchers.Main) {
                assertNull(rig.player.playerError)
                rig.player.currentMediaItemIndex == 1
            } }
            val position = withContext(Dispatchers.Main) { rig.player.currentPosition }
            assertTrue("incoming overlap position retained", position > 1000)
            assertSame(identity, rig.player)
            assertSame(incoming, rig.engine.activeDeck)
            delay(750)
            assertTrue(withContext(Dispatchers.Main) { rig.player.currentPosition >= position + 500 && rig.player.isPlaying })
            assertEquals("single automatic transition", 1, automatic)
            assertEquals("no output recreation/seek at handover", 1, rig.captures[1].outputCreations)
            assertFalse(rig.captures[1].failed)
            assertTrue(rig.captures[1].pcm().any { it.toInt() != 0 })
        } finally { withContext(Dispatchers.Main) { rig.close() } }
    }

    @Test fun pauseFocusManualSkipShuffleAndRepeatKeepOneLogicalQueue(): Unit = runBlocking {
        val rig = withContext(Dispatchers.Main) { Rig(3) }
        try {
            val next = withContext(Dispatchers.Main) {
                val shuffle = ServiceShuffle(rig.player)
                rig.player.addListener(shuffle)
                rig.player.shuffleModeEnabled = true
                val id = rig.player.getMediaItemAt(rig.player.nextMediaItemIndex).mediaId
                rig.player.prepare(); rig.player.seekTo(0, 5600); rig.player.play()
                id
            }
            await("overlap") { withContext(Dispatchers.Main) { rig.status.incomingPlaying } }
            val secondary = withContext(Dispatchers.Main) { rig.decks.last() }
            withContext(Dispatchers.Main) {
                rig.engine.onFocusCommand(androidx.media3.common.audio.AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK)
                assertTrue(rig.player.playWhenReady)
                assertFalse(rig.player.isPlaying)
                assertFalse(secondary.isPlaying)
                rig.player.pause()
                rig.engine.onFocusCommand(androidx.media3.common.audio.AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY)
                assertFalse(rig.player.playWhenReady)
                assertFalse(secondary.isPlaying)
                rig.player.play()
            }
            await("resumed overlap") { withContext(Dispatchers.Main) { rig.status.incomingPlaying } }
            withContext(Dispatchers.Main) { rig.player.seekToNextMediaItem() }
            await("skip exact shuffled UUID") { withContext(Dispatchers.Main) {
                rig.player.currentMediaItem?.mediaId == next && rig.player.isPlaying
            } }
            assertTrue("old secondary released on manual skip", rig.captures[1].releasedAtNanos != Long.MAX_VALUE)
            withContext(Dispatchers.Main) {
                assertEquals(3, rig.player.mediaItemCount)
                assertEquals(3, (0 until 3).map { rig.player.getMediaItemAt(it).mediaId }.toSet().size)
                rig.player.repeatMode = Player.REPEAT_MODE_ONE
                rig.player.seekTo(7600)
            }
            delay(1100)
            withContext(Dispatchers.Main) {
                assertEquals(next, rig.player.currentMediaItem?.mediaId)
                assertTrue(rig.player.currentPosition < 2000)
                assertFalse(rig.status.active)
                rig.player.pause(); rig.engine.resetForProfile(); rig.player.stop(); rig.player.clearMediaItems()
                rig.player.setMediaItems(rig.items, 0, 1200) // metadata-only profile restore
                assertFalse(rig.player.playWhenReady)
                assertEquals(Player.STATE_IDLE, rig.player.playbackState)
            }
        } finally { withContext(Dispatchers.Main) { rig.close() } }
    }

    @Test fun overlapClockSurvivesPausePromotionAndAcknowledgedRecreation(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, com.walktape.app.core.db.WalkTapeDatabase::class.java).build()
        val id = db.profileDao().insert(ProfileEntity(name = "clock fixture", serverUrl = "https://fixture.invalid",
            username = "fixture", createdAt = 1))
        val store = ListeningStore(db.scrobbleDao()) {}
        val rig = withContext(Dispatchers.Main) { Rig() }
        var tracker: ScrobbleTracker? = null
        try {
            withContext(Dispatchers.Main) {
                val clock = ScrobbleTracker(rig.player, store, rig.scope,
                    mainAudible = { rig.player.isPlaying && rig.engine.activeDeck.volume > 0f })
                tracker = clock
                clock.profile(id)
                rig.engine.overlap = { deck, audible -> clock.overlap(deck, audible) }
                rig.engine.promoting = clock::promoteOverlap
                rig.engine.cancelOverlap = clock::cancelOverlap
                rig.player.prepare(); rig.player.play()
            }
            await("silent preload ready") { withContext(Dispatchers.Main) {
                rig.decks.size == 2 && rig.decks.last().playbackState == Player.STATE_READY
            } }
            assertNull("silent preload has no listening pass", store.loadOverlap(id))
            await("audible incoming clock") { store.loadOverlap(id)?.listenedMs?.let { it >= 500 } == true }
            val beforePause = store.loadOverlap(id)!!
            withContext(Dispatchers.Main) { rig.player.pause() }
            val paused = store.loadOverlap(id)!!
            delay(700)
            val afterPause = store.loadOverlap(id)!!
            assertEquals(paused.listenedMs, afterPause.listenedMs)
            assertEquals(beforePause.eventId, afterPause.eventId)
            withContext(Dispatchers.Main) { rig.player.play() }
            await("promoted UUID") { withContext(Dispatchers.Main) { rig.player.currentMediaItemIndex == 1 } }
            val promoted = store.load(id)!!
            assertEquals(beforePause.eventId, promoted.eventId)
            assertTrue("audible overlap must not be reset to zero", promoted.listenedMs >= 1400)
            await("two earned passes") { db.scrobbleDao().count(id) == 2 }
            withContext(Dispatchers.Main) { rig.player.pause(); tracker!!.close(); tracker = null }
            val saved = store.load(id)!!
            val events = db.scrobbleDao().pending(id, 10)
            assertEquals(2, events.size)
            db.scrobbleDao().markSent(id, events.map { it.eventId }, System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                val recovered = ScrobbleTracker(rig.player, store, rig.scope)
                tracker = recovered
                recovered.profile(id)
                rig.player.play()
            }
            delay(1200)
            assertEquals(saved.eventId, store.load(id)!!.eventId)
            assertEquals(2, db.scrobbleDao().count(id))
            assertTrue(db.scrobbleDao().pending(id, 10).isEmpty())
        } finally {
            withContext(Dispatchers.Main) { tracker?.close(); rig.close() }
            store.load(id) // FIFO barrier before closing Room
            db.close()
        }
    }

    @Test fun laggingQueueRecoveryKeepsBothConfirmedPassIdentities(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, com.walktape.app.core.db.WalkTapeDatabase::class.java).build()
        val id = db.profileDao().insert(ProfileEntity(name = "recovery fixture", serverUrl = "https://fixture.invalid",
            username = "fixture", createdAt = 1))
        val outgoing = com.walktape.app.core.db.ListeningStateEntity(id, "queue/occurrence-0", "ack-out", "duplicate-song",
            "A", null, null, 1, 5000, true)
        val incoming = outgoing.copy(mediaId = "queue/occurrence-1", eventId = "ack-in", title = "B")
        val eventA = com.walktape.app.core.db.ScrobbleEventEntity(id, "ack-out", "duplicate-song", "A", null, null, 1, 5000, 1)
        val eventB = eventA.copy(eventId = "ack-in", title = "B")
        db.scrobbleDao().record(outgoing, eventA)
        db.scrobbleDao().recordOverlap(incoming, eventB)
        db.scrobbleDao().markSent(id, listOf("ack-out", "ack-in"), 2)
        db.scrobbleDao().promoteOverlap(incoming, com.google.gson.Gson().toJson(outgoing))
        // Crash-window fixture: queue snapshot still points at the outgoing UUID, while
        // the atomic listening transaction has already promoted the incoming ACK.
        val rig = withContext(Dispatchers.Main) { Rig() }
        val store = ListeningStore(db.scrobbleDao()) {}
        var clock: ScrobbleTracker? = null
        try {
            withContext(Dispatchers.Main) {
                rig.player.seekTo(0, 5600)
                val tracker = ScrobbleTracker(rig.player, store, rig.scope)
                clock = tracker
                tracker.profile(id)
                rig.engine.overlap = { deck, audible -> tracker.overlap(deck, audible) }
                rig.engine.promoting = tracker::promoteOverlap
                rig.engine.cancelOverlap = tracker::cancelOverlap
                assertFalse(rig.player.playWhenReady)
                assertEquals(Player.STATE_IDLE, rig.player.playbackState)
            }
            assertEquals("ack-out", store.load(id)!!.eventId)
            withContext(Dispatchers.Main) { rig.player.prepare(); rig.player.play() }
            await("recovered incoming promotion") { withContext(Dispatchers.Main) { rig.player.currentMediaItemIndex == 1 } }
            assertEquals("ack-in", store.load(id)!!.eventId)
            delay(1200)
            assertEquals(2, db.scrobbleDao().count(id))
            assertTrue(db.scrobbleDao().pending(id, 10).isEmpty())
        } finally {
            withContext(Dispatchers.Main) { clock?.close(); rig.close() }
            store.load(id)
            db.close()
        }
    }

    @Test fun boundaryTimersPreventOverlapPastTrackAlbumAndQueue(): Unit = runBlocking {
        for (mode in listOf(SleepTimer.Mode.TRACK, SleepTimer.Mode.ALBUM, SleepTimer.Mode.QUEUE)) {
            val timer = SleepTimer()
            val rig = withContext(Dispatchers.Main) { Rig(if (mode == SleepTimer.Mode.ALBUM) 3 else 2, timer) }
            try {
                withContext(Dispatchers.Main) {
                    rig.player.prepare()
                    if (mode == SleepTimer.Mode.QUEUE) {
                        rig.player.repeatMode = Player.REPEAT_MODE_ALL
                        rig.player.seekTo(1, 7600)
                    } else rig.player.seekTo(0, if (mode == SleepTimer.Mode.TRACK) 7600 else 5600)
                    timer.arm(mode)
                    rig.player.play()
                }
                if (mode == SleepTimer.Mode.ALBUM) {
                    await("allowed overlap inside album") { withContext(Dispatchers.Main) { rig.player.currentMediaItemIndex == 1 } }
                    assertEquals(2, rig.decks.size)
                    withContext(Dispatchers.Main) { rig.player.seekTo(7600) }
                }
                await("$mode timer paused") { withContext(Dispatchers.Main) { !rig.player.playWhenReady && !timer.state.value.armed } }
                withContext(Dispatchers.Main) {
                    assertFalse(rig.player.isPlaying)
                    assertFalse(rig.status.incomingPlaying)
                    assertEquals(if (mode == SleepTimer.Mode.ALBUM) 2 else if (mode == SleepTimer.Mode.QUEUE) 0 else 1,
                        rig.player.currentMediaItemIndex)
                }
                assertEquals("no deck prepared beyond stop boundary", if (mode == SleepTimer.Mode.ALBUM) 2 else 1, rig.decks.size)
            } finally { withContext(Dispatchers.Main) { rig.close() } }
        }
    }

    @Test fun removingSuccessorDuringFadeCannotPromoteItsStaleUuid(): Unit = runBlocking {
        val rig = withContext(Dispatchers.Main) { Rig(3) }
        try {
            withContext(Dispatchers.Main) {
                rig.player.prepare(); rig.player.seekTo(0, 5600); rig.player.play()
            }
            await("audible soon-to-be-removed successor") { withContext(Dispatchers.Main) { rig.status.incomingPlaying } }
            withContext(Dispatchers.Main) { rig.player.removeMediaItem(1) }
            await("surviving successor became current") { withContext(Dispatchers.Main) {
                assertNull(rig.player.playerError)
                rig.player.currentMediaItem?.mediaId == "queue/occurrence-2" && rig.player.isPlaying
            } }
            assertEquals("removed deck output released", true, rig.captures[1].releasedAtNanos != Long.MAX_VALUE)
            withContext(Dispatchers.Main) {
                assertEquals(2, rig.player.mediaItemCount)
                assertTrue((0 until 2).none { rig.player.getMediaItemAt(it).mediaId == "queue/occurrence-1" })
            }
        } finally { withContext(Dispatchers.Main) { rig.close() } }
    }

    @Test fun dspOverlapTimerAndDuckingComposeWithoutChangingAndroidVolume(): Unit = runBlocking {
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val androidVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val timer = SleepTimer()
        val rig = withContext(Dispatchers.Main) { Rig(timer = timer, dsp = true) }
        try {
            withContext(Dispatchers.Main) { rig.player.prepare(); rig.player.seekTo(0, 5600); rig.player.play() }
            await("DSP overlap playing") { withContext(Dispatchers.Main) { rig.status.incomingPlaying } }
            withContext(Dispatchers.Main) {
                rig.engine.onDucking(0.2f)
                assertEquals(1f, rig.player.volume, 0.001f)
                assertEquals(0.2f, rig.decks.sumOf { it.volume.toDouble() }.toFloat(), 0.01f)
                timer.arm(SleepTimer.Mode.DURATION, 900)
            }
            delay(250)
            withContext(Dispatchers.Main) {
                assertTrue(rig.player.volume < 0.4f)
                assertTrue(rig.decks.sumOf { it.volume.toDouble() } < 0.09)
            }
            await("timer stopped both DSP decks") { withContext(Dispatchers.Main) { !rig.player.playWhenReady && !rig.status.incomingPlaying } }
            withContext(Dispatchers.Main) {
                rig.engine.onDucking(1f)
                assertFalse(rig.player.isPlaying)
                assertEquals(1f, rig.player.volume, 0.001f)
                assertTrue(rig.decks.none { it.isPlaying })
            }
            assertEquals(androidVolume, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
            assertTrue(rig.captures.size >= 2)
            rig.captures.forEach { capture ->
                assertTrue(capture.pcm().isNotEmpty())
                assertTrue("combined DSP retains digital headroom", capture.pcm().maxOf { kotlin.math.abs(it.toInt()) } < 3500)
            }
        } finally { withContext(Dispatchers.Main) { rig.close() } }
    }

    @Test fun removingCurrentKeepsTheAlreadyAudibleSuccessorPass(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, com.walktape.app.core.db.WalkTapeDatabase::class.java).build()
        val id = db.profileDao().insert(ProfileEntity(name = "edit clock fixture", serverUrl = "https://fixture.invalid",
            username = "fixture", createdAt = 1))
        val store = ListeningStore(db.scrobbleDao()) {}
        val rig = withContext(Dispatchers.Main) { Rig() }
        var tracker: ScrobbleTracker? = null
        try {
            withContext(Dispatchers.Main) {
                val clock = ScrobbleTracker(rig.player, store, rig.scope)
                tracker = clock
                clock.profile(id)
                rig.engine.overlap = { deck, audible -> clock.overlap(deck, audible) }
                rig.engine.promoting = clock::promoteOverlap
                rig.engine.cancelOverlap = clock::cancelOverlap
                rig.player.prepare(); rig.player.play()
            }
            await("audible prefix before current removal") { store.loadOverlap(id)?.listenedMs?.let { it >= 400 } == true }
            val prefix = store.loadOverlap(id)!!
            withContext(Dispatchers.Main) { rig.player.removeMediaItem(0) }
            await("same successor selected by queue edit") { withContext(Dispatchers.Main) {
                rig.player.currentMediaItem?.mediaId == prefix.mediaId && rig.player.isPlaying
            } }
            val continued = store.load(id)!!
            assertEquals(prefix.eventId, continued.eventId)
            assertTrue(continued.listenedMs >= prefix.listenedMs)
            await("successor earned one event") { db.scrobbleDao().pending(id, 10).any { it.eventId == prefix.eventId } }
            assertEquals(1, db.scrobbleDao().pending(id, 10).count { it.eventId == prefix.eventId })
        } finally {
            withContext(Dispatchers.Main) { tracker?.close(); rig.close() }
            store.load(id); db.close()
        }
    }
}
