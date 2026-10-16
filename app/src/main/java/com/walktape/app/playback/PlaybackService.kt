package com.walktape.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.UUID
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import com.walktape.app.core.net.OpenSubsonicClient
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Media3 session service: owns the ExoPlayer, feeds it through the local-first data
 * source (completed downloads, then temporary cache, then authenticated network) and
 * follows the active profile — switching profile stops playback before restoring
 * the profile's saved queue without preparing or starting audio.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val connection = MutableStateFlow<StreamProfile?>(null)
    private lateinit var mediaSession: MediaSession
    private lateinit var persistence: PlaybackPersistence
    private lateinit var tracker: ScrobbleTracker
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Completed downloads of the active profile: songId -> audio file + representation. */
    @Volatile private var completedDownloads: Map<String, Pair<File, Boolean>> = emptyMap()
    private var skipStreak = 0

    private data class DeckAudio(
        val gain: com.walktape.app.playback.dsp.ReplayGainProcessor,
        val tape: com.walktape.app.playback.dsp.TapeProcessor,
        var tags: com.walktape.app.playback.dsp.ReplayGainParser.Tags = com.walktape.app.playback.dsp.ReplayGainParser.Tags(),
    )
    private val deckAudio = java.util.IdentityHashMap<ExoPlayer, DeckAudio>()
    private lateinit var crossfade: CrossfadeCoordinator

    override fun onCreate() {
        super.onCreate()
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val selectionFor: (Long) -> StreamSelection = { profileId ->
            // Pure local decision (persisted settings + connectivity state): no request,
            // no capability probe — offline-only never leaks through this path.
            streamSelection(
                Graph.networkGate.mobileReduced(profileId),
                Graph.networkGate.reducedBitRate(profileId),
                connectivity?.activeNetwork?.let { network ->
                    connectivity.getNetworkCapabilities(network)?.let { capabilities ->
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) &&
                            !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    }
                } == true,
            )
        }
        val dataSourceFactory = LocalFirstDataSourceFactory(
            OpenSubsonicClient.streamingHttpClient(), "WalkTape",
            { connection.value },
            Graph.audioCache,
            localFile = { profileId, songId ->
                if (connection.value?.id == profileId) completedDownloads[songId]?.first else null
            },
            offlineOnly = { profileId -> Graph.networkGate.offlineOnly(profileId) },
            selectionFor = selectionFor,
            qualityMonitor = Graph.playbackQuality,
            localReducedLookup = { songId -> completedDownloads[songId]?.second == true },
        )
        Graph.audioOutput.reset()
        Graph.scrobbleSender.trigger() // deliver offline listen events as soon as possible
        // One shared audio session for both decks: the system EQ/bass chain (W09-T04)
        // processes whatever either deck plays, including during a crossfade.
        val audioSessionId = (getSystemService(AUDIO_SERVICE) as? android.media.AudioManager)
            ?.generateAudioSessionId() ?: android.media.audiofx.AudioEffect.ERROR_BAD_VALUE
        val musicAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        fun buildDeck(): ExoPlayer {
            val audio = DeckAudio(com.walktape.app.playback.dsp.ReplayGainProcessor(),
                com.walktape.app.playback.dsp.TapeProcessor())
            audio.gain.enabled = Graph.currentSettings.replayGainMode != com.walktape.app.core.settings.ReplayGainMode.OFF
            audio.tape.enabled = Graph.currentSettings.tapeEffectEnabled
            val deck = ExoPlayer.Builder(this,
                Graph.audioOutput.renderers(this, sinkHook = { builder ->
                    builder.setAudioProcessors(arrayOf(audio.gain, audio.tape))
                }, monitor = true))
                .setDeviceVolumeControlEnabled(true)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setAudioAttributes(musicAttributes, /* handleAudioFocus = */ false)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
            if (audioSessionId > 0) deck.setAudioSessionId(audioSessionId)
            deckAudio[deck] = audio
            deck.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    audio.tags = com.walktape.app.playback.dsp.ReplayGainParser.parse(
                        tracks.groups.firstOrNull { it.getTrackFormat(0).sampleMimeType?.startsWith("audio/") == true }
                            ?.getTrackFormat(0)?.metadata)
                    applyReplayGainTags(audio.gain, audio.tags)
                    if (::crossfade.isInitialized && crossfade.activeDeck === deck) publishReplayGain(audio)
                }
            })
            return deck
        }
        crossfade = CrossfadeCoordinator(scope, buildDeck(), ::buildDeck,
            enabled = { Graph.currentSettings.crossfadeEnabled },
            durationMs = { Graph.currentSettings.crossfadeDurationMs },
            transitionAllowed = { index -> Graph.playbackTimer.allowsOverlap(index) },
            report = { Graph.crossfadeStatus.value = it },
            deckReleased = { deckAudio.remove(it) })
        val player = crossfade.player
        crossfade.attachFocus(LogicalAudioFocus(this, musicAttributes,
            crossfade::onFocusCommand, crossfade::onDucking))
        val shuffle = ServiceShuffle(player)
        player.addListener(shuffle)
        Graph.playbackTimer.attach(player)
        player.addListener(automationListener)
        player.addListener(unavailableListener)
        player.addListener(qualityListener)
        tracker = ScrobbleTracker(player, Graph.listeningStore, scope,
            mainAudible = { player.isPlaying && crossfade.activeDeck.volume > 0f })
        crossfade.overlap = { deck, audible -> tracker.overlap(deck, audible) }
        crossfade.promoting = tracker::promoteOverlap
        crossfade.cancelOverlap = tracker::cancelOverlap
        crossfade.start()
        Graph.systemEffects.attach(audioSessionId)
        scope.launch {
            Graph.settingsRepository.settings.collect { settings ->
                deckAudio.values.toList().forEach { audio ->
                    audio.tape.enabled = settings.tapeEffectEnabled
                    audio.gain.enabled = settings.replayGainMode != com.walktape.app.core.settings.ReplayGainMode.OFF
                    applyReplayGainTags(audio.gain, audio.tags, settings.replayGainMode)
                }
                deckAudio[crossfade.activeDeck]?.let { publishReplayGain(it, settings.replayGainMode) }
                Graph.systemEffects.apply(settings.equalizerEnabled,
                    Graph.systemEffects.sanitizeBandLevelsCsv(settings.equalizerBands),
                    settings.bassBoostEnabled, settings.bassBoostStrength)
            }
        }
        scope.launch {
            while (true) {
                delay(60_000)
                Graph.scrobbleSender.trigger()
            }
        }
        // Keep the local-file lookup in sync with the download table of the active profile.
        scope.launch {
            connection.collectLatest { profile ->
                completedDownloads = emptyMap()
                if (profile != null) {
                    Graph.downloadStore.downloads(profile.id).collect { rows ->
                        val available = mutableMapOf<String, Pair<File, Boolean>>()
                        rows.filter { it.status == com.walktape.app.core.db.DownloadStatus.COMPLETED.name }
                            .forEach { row ->
                                val path = row.relativePath ?: return@forEach
                                val root = Graph.profiles.directory(profile.id).canonicalFile
                                runCatching {
                                    val file = File(root, path).canonicalFile
                                    if (file.path.startsWith(root.path + File.separator) && file.isFile) {
                                        available[row.songId] = Pair(file,
                                            row.representation == com.walktape.app.core.db.DownloadRepresentation.REDUCED.name)
                                    }
                                }
                            }
                        completedDownloads = available.toMap()
                    }
                }
            }
        }
        // Apply audio cache limit changes as soon as the player is idle and empty.
        scope.launch {
            Graph.settingsRepository.settings.map { it.audioCacheLimitBytes }
                .distinctUntilChanged().collectLatest { limit ->
                    Graph.audioCache.updateLimit(limit)
                    val active = connection.value?.id
                    if (active != null && Graph.audioCache.limitPending()) {
                        awaitIdleAndRecreate(active)
                    }
                }
        }
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(session: MediaSession,
                    controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val result = SettableFuture.create<MutableList<MediaItem>>()
                    val job = scope.launch {
                        Graph.networkGate.awaitReady()
                        // Binding may complete before Room/Keystore has loaded the active profile.
                        val profile = withTimeoutOrNull(5_000) { connection.filterNotNull().first() }
                        if (profile != null && mediaItems.all {
                                SubsonicStream.songId(it.localConfiguration?.uri.toString(), profile.id) != null
                            }) result.set(mediaItems.map { item ->
                                val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {
                                    putString(QueueIdentity.SONG, SubsonicStream.songId(item.localConfiguration!!.uri.toString(), profile.id))
                                }
                                item.buildUpon().setMediaId(QueueIdentity.PREFIX + UUID.randomUUID())
                                    .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
                            }.toMutableList())
                        else result.setException(IOException("Playback profile unavailable"))
                    }
                    job.invokeOnCompletion { if (!result.isDone) result.cancel(false) }
                    return result
                }
            })
            .build()
        persistence = PlaybackPersistence(player, shuffle, Graph.queues, scope) {
            if (it == null) {
                Graph.playbackTimer.cancel(); Graph.suggestion.reset(); Graph.playbackQuality.reset()
                crossfade.resetForProfile()
                deckAudio[crossfade.activeDeck]?.let {
                    it.tags = com.walktape.app.playback.dsp.ReplayGainParser.Tags()
                    it.gain.targetGainDb = 0f
                    it.gain.tagPeak = null
                }
            }
            connection.value = it
        }
        persistence.start()
        scope.launch { connection.collectLatest { tracker.profile(it?.id) } }
        scope.launch {
            Graph.settingsRepository.settings.map { it.suggestionsEnabled }
                .distinctUntilChanged().collect {
                    if (!it) Graph.suggestion.reset()
                }
        }
    }

    /** Recreates the profile cache when the player holds no prepared media. */
    private suspend fun awaitIdleAndRecreate(profileId: Long) {
        if (!::mediaSession.isInitialized) return
        val player = mediaSession.player
        var waited = 0
        while (player.playbackState != Player.STATE_IDLE && waited < 30_000) {
            delay(1_000)
            waited += 1_000
        }
        if (player.playbackState == Player.STATE_IDLE) Graph.audioCache.recreate(profileId)
    }

    /**
     * Unavailable songs during offline listening (W08-T04): when the failing song has no
     * local copy and the error is network-side, skip to the next occurrence if the profile
     * allows it — at most one full round of the queue, then the error surfaces. The skip is
     * signalled through Graph.offlineSkips for the honest UI notice.
     */
    private val unavailableListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val player = mediaSession.player
            val profile = connection.value ?: return
            if (!Graph.networkGate.skipUnavailable(profile.id)) return
            val item = player.currentMediaItem ?: return
            val songId = item.mediaMetadata.extras?.getString(QueueIdentity.SONG) ?: return
            if (completedDownloads.containsKey(songId)) return // local copy failed: real error
            val networkCause = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                -> true
                else -> error.cause is IOException
            }
            if (!networkCause) return
            if (skipStreak >= player.mediaItemCount) return // a full loop: stop skipping
            skipStreak++
            if (player.hasNextMediaItem()) {
                Graph.lastSkippedTitle.value = item.mediaMetadata.title?.toString()
                Graph.offlineSkips.value = Graph.offlineSkips.value + 1
                player.seekToNextMediaItem()
                player.prepare()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) skipStreak = 0
        }
    }

    /** Queue-end automation: the suggestion is fetched only for the active profile's server. */
    private val automationListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) Graph.suggestion.reset()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = mediaSession.player
            if (playbackState == Player.STATE_ENDED && !Graph.networkGate.offlineOnly(connection.value?.id ?: -1)) {
                val item = player.currentMediaItem
                val profile = connection.value
                Graph.suggestion.evaluate(profile?.client, profile?.id ?: -1L,
                    item?.mediaMetadata?.extras?.getString(QueueIdentity.SONG),
                    item?.mediaMetadata?.title?.toString())
            } else {
                Graph.suggestion.reset()
            }
        }
    }

    /** Honest quality label (W09-T01): decoder-side mime/bitrate of the song actually playing. */
    private val qualityListener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val item = mediaSession.player.currentMediaItem ?: return
            val songId = item.mediaMetadata.extras?.getString(QueueIdentity.SONG) ?: return
            var mime: String? = null
            var bitrate: Int? = null
            for (group in tracks.groups) {
                val format = group.getTrackFormat(0)
                if (format.sampleMimeType?.startsWith("audio/") == true) {
                    mime = format.sampleMimeType
                    bitrate = format.bitrate.takeIf { it > 0 }
                    break
                }
            }
            Graph.playbackQuality.observed(songId, mime, bitrate)
            deckAudio[crossfade.activeDeck]?.let { publishReplayGain(it) }
        }
    }

    private fun applyReplayGainTags(processor: com.walktape.app.playback.dsp.ReplayGainProcessor,
        tags: com.walktape.app.playback.dsp.ReplayGainParser.Tags,
        mode: com.walktape.app.core.settings.ReplayGainMode = Graph.currentSettings.replayGainMode) {
        processor.targetGainDb = com.walktape.app.playback.dsp.ReplayGainParser.selectGainDb(tags, mode)
        processor.tagPeak = com.walktape.app.playback.dsp.ReplayGainParser.selectPeak(tags, mode)
    }

    private fun publishReplayGain(audio: DeckAudio,
        mode: com.walktape.app.core.settings.ReplayGainMode = Graph.currentSettings.replayGainMode) {
            val tags = audio.tags
            val present = when (mode) {
                com.walktape.app.core.settings.ReplayGainMode.OFF -> false
                com.walktape.app.core.settings.ReplayGainMode.TRACK -> tags.trackGainDb != null
                com.walktape.app.core.settings.ReplayGainMode.ALBUM -> tags.albumGainDb != null || tags.trackGainDb != null
            }
            Graph.replayGainInfo.value = com.walktape.app.playback.dsp.ReplayGainParser.Info(
                mode, present, audio.gain.targetGainDb)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.uid == applicationInfo.uid || controllerInfo.isTrusted) mediaSession else null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        tracker.close()
        persistence.close()
        scope.cancel()
        crossfade.release()
        Graph.systemEffects.release()
        mediaSession.run {
            player.removeListener(automationListener)
            player.removeListener(unavailableListener)
            player.removeListener(qualityListener)
            Graph.playbackTimer.detach()
            Graph.suggestion.reset()
            player.release()
            release()
        }
        Graph.audioOutput.reset()
        Graph.scrobbleSender.trigger() // last chance to flush pending events while alive
        super.onDestroy()
    }
}
