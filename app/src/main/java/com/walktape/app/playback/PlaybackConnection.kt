package com.walktape.app.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.walktape.app.core.db.SongEntity
import com.walktape.app.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-wide bridge to the playback service. The activity connects on start and disconnects
 * on stop; play requests wait briefly for the controller, so a tap right after process start
 * still works. All controller state flows through the main executor, keeping calls serialized.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@androidx.annotation.MainThread
class PlaybackConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var future: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mutableState = MutableStateFlow(PlaybackSnapshot())
    val state = mutableState.asStateFlow()
    private var ticker: Job? = null
    private var pendingPlay: Job? = null
    private var requestFailure: PlaybackFailure? = null
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val built = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (_controller.value === controller) {
                        detach()
                        future = null
                        mutableState.value = PlaybackSnapshot(failure = PlaybackFailure.CONNECTION)
                    }
                }
            }).buildAsync()
        future = built
        Futures.addCallback(built, object : FutureCallback<MediaController> {
            override fun onSuccess(result: MediaController) {
                if (future === built) {
                    _controller.value = result
                    requestFailure = null
                    result.addListener(playerListener)
                    refresh()
                    ticker = scope.launch {
                        while (isActive) { delay(500); if (state.value.hasMedia) refresh() }
                    }
                }
            }

            override fun onFailure(t: Throwable) {
                if (future === built) {
                    future = null
                    mutableState.value = PlaybackSnapshot(failure = PlaybackFailure.CONNECTION)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        val built = future
        future = null
        detach()
        built?.let { MediaController.releaseFuture(it) }
        mutableState.value = PlaybackSnapshot()
    }

    private fun detach() {
        pendingPlay?.cancel()
        ticker?.cancel()
        ticker = null
        _controller.value?.removeListener(playerListener)
        _controller.value = null
        requestFailure = null
    }

    fun playQueue(songs: List<SongEntity>, startIndex: Int) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        pendingPlay?.cancel()
        pendingPlay = scope.launch {
            val mediaController = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                controller.first { it != null }
            }
            if (mediaController == null) {
                requestFailure = PlaybackFailure.CONNECTION
                refresh()
                return@launch
            }
            val profileId = songs.first().profileId
            val activeId = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
            if (songs.any { it.profileId != profileId } || activeId != profileId ||
                !mediaController.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                requestFailure = PlaybackFailure.REQUEST
                refresh()
                return@launch
            }
            requestFailure = null
            mediaController.setMediaItems(songs.map(::songMediaItem), safeIndex, 0)
            mediaController.prepare()
            mediaController.play()
            refresh()
        }
    }

    fun togglePlayPause() = command(Player.COMMAND_PLAY_PAUSE) { player ->
        if (player.playWhenReady && player.playerError == null && player.playbackState != Player.STATE_ENDED) player.pause()
        else {
            if (player.playbackState == Player.STATE_ENDED &&
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) player.seekTo(0, 0)
            if (player.playbackState == Player.STATE_IDLE &&
                player.isCommandAvailable(Player.COMMAND_PREPARE)) player.prepare()
            player.play()
        }
    }

    fun previous() = command(Player.COMMAND_SEEK_TO_PREVIOUS) { it.seekToPrevious() }
    fun next() = command(Player.COMMAND_SEEK_TO_NEXT) { it.seekToNext() }

    /** Do not apply an in-flight slider gesture to a track/profile that has since changed. */
    fun seekTo(positionMs: Long, itemKey: String?) = command(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) {
        val duration = it.duration
        if (itemKey == key(it) && duration != C.TIME_UNSET && duration > 0 && it.isCurrentMediaItemSeekable) {
            it.seekTo(positionMs.coerceIn(0, duration))
        }
    }

    fun cycleRepeat() = command(Player.COMMAND_SET_REPEAT_MODE) {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSystemVolume(value: Int) {
        if (audio.isVolumeFixed) return
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                value.coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
            requestFailure = null
        } catch (_: SecurityException) { requestFailure = PlaybackFailure.VOLUME }
        refresh()
    }

    private fun command(command: Int, action: (MediaController) -> Unit) {
        val player = _controller.value ?: return
        if (player.currentMediaItem == null || !player.isCommandAvailable(command)) return
        requestFailure = null
        action(player)
        refresh()
    }

    private fun key(player: Player): String? = player.currentMediaItem?.let {
        "${it.localConfiguration?.uri ?: it.mediaId}:${player.currentMediaItemIndex}"
    }

    private fun refresh() {
        val player = _controller.value
        val hasMedia = player?.currentMediaItem != null
        val duration = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0 }
        mutableState.value = PlaybackSnapshot(
            connected = player != null,
            itemKey = player?.let(::key),
            title = player?.mediaMetadata?.title?.toString().orEmpty(),
            artist = player?.mediaMetadata?.artist?.toString().orEmpty(),
            album = player?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            positionMs = player?.currentPosition?.coerceAtLeast(0) ?: 0,
            durationMs = duration,
            isPlaying = player?.isPlaying == true,
            playWhenReady = player?.playWhenReady == true,
            buffering = player?.playbackState == Player.STATE_BUFFERING,
            ended = player?.playbackState == Player.STATE_ENDED,
            repeatMode = player?.repeatMode ?: Player.REPEAT_MODE_OFF,
            canPlayPause = hasMedia && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
            canPrevious = hasMedia && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            canNext = hasMedia && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT),
            canSeek = duration != null && player.isCurrentMediaItemSeekable &&
                player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            canRepeat = hasMedia && player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE),
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            volumeFixed = audio.isVolumeFixed,
            failure = if (player?.playerError != null) PlaybackFailure.PLAYBACK else requestFailure,
        )
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
