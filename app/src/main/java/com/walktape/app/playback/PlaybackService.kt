package com.walktape.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Media3 session service: owns the ExoPlayer, feeds it through the authenticated stream data
 * source and follows the active profile — switching profile stops playback and clears the queue
 * (queue restore per profile belongs to W05-T04).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val connection = MutableStateFlow<StreamProfile?>(null)
    private lateinit var mediaSession: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = SubsonicStreamDataSourceFactory(
            OpenSubsonicClient.streamingHttpClient(), "WalkTape"
        ) { connection.value }
        val player = ExoPlayer.Builder(this)
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
                            }) result.set(mediaItems)
                        else result.setException(IOException("Playback profile unavailable"))
                    }
                    job.invokeOnCompletion { if (!result.isDone) result.cancel(false) }
                    return result
                }
            })
            .build()
        scope.launch {
            var previous: Long? = null
            Graph.profiles.profiles.collect { profiles ->
                val active = profiles.firstOrNull { it.isActive }
                if (active?.id != previous) {
                    connection.value = null
                    previous = active?.id
                    mediaSession.player.pause()
                    mediaSession.player.stop()
                    mediaSession.player.clearMediaItems()
                    mediaSession.player.repeatMode = Player.REPEAT_MODE_OFF
                    connection.value = active?.let { profile ->
                        Graph.profiles.clientFor(profile.id)?.let { StreamProfile(profile.id, it) }
                    }
                }
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
        scope.cancel()
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
