package com.walktape.app.playback

import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.db.*
import com.walktape.app.core.downloads.DownloadStore
import com.walktape.app.core.net.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import mockwebserver3.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Downloaded audio, actual service/session and real HTTP request counter in offline-only. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Wave09OfflineHandoverTest {
    @Test fun offlinePromotionAndProfileRestoreNeverStartNetworkOrAutoplay(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Graph.database.profileDao().active()?.id
        val settings = Graph.settingsRepository.settings.first()
        val wav = TestAudioEngine.wav(TestAudioEngine.sine(64000, 6000, 300))
        var id: Long? = null
        var controller: MediaController? = null
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (request.url.encodedPath.endsWith("download.view"))
                MockResponse.Builder().addHeader("Content-Type", "audio/wav").body(okio.Buffer().write(wav)).build()
            else MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build()
        }
        server.start()
        suspend fun await(predicate: suspend () -> Boolean) {
            withTimeout(30_000) { while (!predicate()) delay(100) }
        }
        try {
            Graph.settingsRepository.setCrossfadeEnabled(true)
            Graph.settingsRepository.setCrossfadeDurationMs(1500)
            Graph.settingsRepository.setTapeEffectEnabled(true)
            Graph.settingsRepository.setReplayGainMode(com.walktape.app.core.settings.ReplayGainMode.ALBUM)
            Graph.settingsRepository.setEqualizerEnabled(true)
            Graph.settingsRepository.setEqualizerBands("0:300,1:-200")
            Graph.settingsRepository.setBassBoostStrength(200)
            Graph.settingsRepository.setBassBoostEnabled(true)
            await { Graph.currentSettings.crossfadeEnabled }
            val result = Graph.profiles.addVerified("Offline handover fixture", server.url("/").toString(),
                "fixture", "fixture-only", true) as ApiResult.Success
            val profile = result.value
            id = profile
            val songs = listOf("a", "b").map { song -> SongEntity(profile, song, null, "album", null,
                "Local $song", "Album", "Fixture", null, null, 1, 1, null, null, null, 8, 128,
                "wav", "audio/wav", wav.size.toLong(), null, null, null, null) }
            Graph.database.libraryDao().upsertSongs(songs)
            for (song in songs) {
                Graph.downloadStore.enqueueSongs(profile, listOf(song), "single")
                Graph.database.downloadDao().claimNext(profile, 1)
                assertEquals(DownloadStore.Outcome.Completed, Graph.downloadStore.transfer(profile, song.id,
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
            }
            Graph.database.downloadDao().upsertSettings(ProfileSettingsEntity(profile, offlineOnly = true))
            Graph.profiles.activate(profile)
            await { Graph.networkGate.offlineOnly(profile) }
            val requests = server.requestCount
            val future = withContext(Dispatchers.Main) {
                MediaController.Builder(context, SessionToken(context,
                    ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val c = future.get(30, TimeUnit.SECONDS)
            controller = c
            delay(500)
            withContext(Dispatchers.Main) { c.setMediaItems(songs.map { songMediaItem(it) }); c.prepare(); c.play() }
            await { withContext(Dispatchers.Main) { c.isPlaying && c.currentPosition > 100 } }
            withContext(Dispatchers.Main) { c.seekTo(0, 5600) }
            await { withContext(Dispatchers.Main) { c.currentMediaItemIndex == 1 && c.isPlaying && c.currentPosition > 1800 } }
            val effects = Graph.systemEffects.state.value
            if (effects is com.walktape.app.playback.dsp.SystemEffectsController.State.Ready) {
                assertTrue(effects.eqEnabled)
                assertTrue(effects.bassEnabled)
            } else assertTrue(effects is com.walktape.app.playback.dsp.SystemEffectsController.State.Unavailable)
            val skips = Graph.offlineSkips.value
            withContext(Dispatchers.Main) {
                c.addMediaItems(listOf(songMediaItem(songs[0].copy(id = "unavailable", title = "Unavailable")),
                    songMediaItem(songs[0])))
            }
            await { withContext(Dispatchers.Main) { c.mediaItemCount == 4 } }
            withContext(Dispatchers.Main) { c.seekTo(1, 5600) }
            // Missing auxiliary fails behind the same gate; outgoing local audio must
            // continue, then the normal unavailable-song policy skips to its local copy.
            await { withContext(Dispatchers.Main) { c.currentMediaItemIndex == 3 && c.isPlaying } }
            assertTrue(Graph.offlineSkips.value > skips)
            Graph.scrobbleSender.deliverPending()
            assertEquals("preload/handover/offline sender must issue no HTTP", requests, server.requestCount)
            withContext(Dispatchers.Main) { c.pause(); c.release() }
            controller = null
            context.stopService(Intent(context, PlaybackService::class.java))
            delay(750)
            val restored = withContext(Dispatchers.Main) {
                MediaController.Builder(context, SessionToken(context,
                    ComponentName(context, PlaybackService::class.java))).buildAsync()
            }.get(30, TimeUnit.SECONDS)
            controller = restored
            await { withContext(Dispatchers.Main) { restored.currentMediaItemIndex == 3 } }
            assertFalse(withContext(Dispatchers.Main) { restored.playWhenReady })
            assertEquals(requests, server.requestCount)
        } finally {
            withContext(Dispatchers.Main) { controller?.pause(); controller?.release() }
            context.stopService(Intent(context, PlaybackService::class.java))
            if (previous != null) Graph.profiles.activate(previous)
            id?.let { Graph.profiles.remove(it) }
            Graph.settingsRepository.setCrossfadeEnabled(settings.crossfadeEnabled)
            Graph.settingsRepository.setCrossfadeDurationMs(settings.crossfadeDurationMs)
            Graph.settingsRepository.setTapeEffectEnabled(settings.tapeEffectEnabled)
            Graph.settingsRepository.setReplayGainMode(settings.replayGainMode)
            Graph.settingsRepository.setEqualizerEnabled(settings.equalizerEnabled)
            Graph.settingsRepository.setEqualizerBands(settings.equalizerBands)
            Graph.settingsRepository.setBassBoostStrength(settings.bassBoostStrength)
            Graph.settingsRepository.setBassBoostEnabled(settings.bassBoostEnabled)
            server.close()
        }
    }
}
