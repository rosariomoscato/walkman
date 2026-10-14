package com.walktape.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.UUID
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Media3 session service: owns the ExoPlayer, feeds it through the authenticated stream data
 * source and follows the active profile — switching profile stops playback before restoring
 * the profile's saved queue without preparing or starting audio.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val connection = MutableStateFlow<StreamProfile?>(null)
    private lateinit var mediaSession: MediaSession
    private lateinit var persistence: PlaybackPersistence
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = SubsonicStreamDataSourceFactory(
            OpenSubsonicClient.streamingHttpClient(), "WalkTape"
        ) { connection.value }
        Graph.audioOutput.reset()
        val player = ExoPlayer.Builder(this, Graph.audioOutput.renderers(this))
            .setDeviceVolumeControlEnabled(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val shuffle = ServiceShuffle(player)
        player.addListener(shuffle)
        Graph.playbackTimer.attach(player)
        player.addListener(automationListener)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(session: MediaSession,
                    controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val result = SettableFuture.create<MutableList<MediaItem>>()
                    val job = scope.launch {
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
            if (it == null) { Graph.playbackTimer.cancel(); Graph.suggestion.reset() }
            connection.value = it
        }
        persistence.start()
        scope.launch {
            Graph.settingsRepository.settings.map { it.suggestionsEnabled }
                .distinctUntilChanged().collect {
                    if (!it) Graph.suggestion.reset()
                }
        }
    }

    /** Queue-end automation: the suggestion is fetched only for the active profile's server. */
    private val automationListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) Graph.suggestion.reset()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = mediaSession.player
            if (playbackState == Player.STATE_ENDED) {
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.uid == applicationInfo.uid || controllerInfo.isTrusted) mediaSession else null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        persistence.close()
        scope.cancel()
        mediaSession.run {
            player.removeListener(automationListener)
            Graph.playbackTimer.detach()
            Graph.suggestion.reset()
            player.release()
            release()
        }
        Graph.audioOutput.reset()
        super.onDestroy()
    }
}
