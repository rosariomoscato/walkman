package com.walktape.app.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val editMutex = Mutex()
    private val editJobs = mutableSetOf<Job>()
    private var requestFailure: PlaybackFailure? = null
    private var queueCacheKey: Triple<Any?, Int, Boolean>? = null
    private var queueCache: List<PlaybackQueueEntry> = emptyList()
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
        editJobs.toList().forEach { it.cancel() }
        editJobs.clear()
        ticker?.cancel()
        ticker = null
        _controller.value?.removeListener(playerListener)
        _controller.value = null
        requestFailure = null
        queueCacheKey = null
        queueCache = emptyList()
    }

    fun playQueue(songs: List<SongEntity>, startIndex: Int, autoPlay: Boolean = true) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        editJobs.toList().forEach { it.cancel() }
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
            mediaController.shuffleModeEnabled = false
            mediaController.setMediaItems(songs.map(::songMediaItem), safeIndex, 0)
            mediaController.prepare()
            if (autoPlay) mediaController.play() else mediaController.pause()
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

    /** Play the selection right away: insert after the current item and jump to it. */
    fun playNow(songs: List<SongEntity>) = insertSongs(songs, afterCurrent = true, startPlayback = true)

    /** Insert the selection between the current item and the next one. */
    fun playNext(songs: List<SongEntity>) = insertSongs(songs, afterCurrent = true, startPlayback = false)

    /** Append the selection at the end of the queue. */
    fun appendToQueue(songs: List<SongEntity>) = insertSongs(songs, afterCurrent = false, startPlayback = false)

    private fun insertSongs(songs: List<SongEntity>, afterCurrent: Boolean, startPlayback: Boolean) {
        if (songs.isEmpty()) return
        val job = scope.launch { editMutex.withLock {
            if (!waitForActiveProfile(songs)) return@withLock
            val player = _controller.value ?: return@withLock
            val ready = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                while ((0 until player.mediaItemCount).any { !player.getMediaItemAt(it).mediaId.startsWith(QueueIdentity.PREFIX) }) delay(25)
                true
            } == true
            if (!ready || !player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                requestFailure = PlaybackFailure.REQUEST; refresh(); return@withLock
            }
            if (player.mediaItemCount == 0) {
                player.shuffleModeEnabled = false
                player.setMediaItems(songs.map(::songMediaItem), 0, 0)
                player.prepare()
                if (startPlayback) player.play() else player.pause()
                refresh()
                return@withLock
            }
            val anchor = if (afterCurrent) player.currentMediaItem?.mediaId else null
            val items = songs.map { song ->
                val item = songMediaItem(song)
                if (anchor == null) item else item.buildUpon().setMediaMetadata(
                    item.mediaMetadata.buildUpon().setExtras(Bundle().apply {
                        putString(QueueIdentity.AFTER, anchor)
                    }).build()).build()
            }
            val insertAt = if (afterCurrent) player.currentMediaItemIndex + 1 else player.mediaItemCount
            player.addMediaItems(insertAt, items)
            if (startPlayback) {
                if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) player.seekTo(insertAt, 0)
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) player.play()
            }
            requestFailure = null
            refresh()
        } }
        editJobs.add(job)
        job.invokeOnCompletion { editJobs.remove(job) }
    }

    /** Shuffle the queue without repetitions, keeping the current track; off restores the saved order. */
    fun toggleShuffle() {
        val player = _controller.value ?: return
        if (player.currentMediaItem == null ||
            !player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        requestFailure = null
        refresh()
    }

    /** Stable occurrence ids, even if a row gesture spans a queue edit or a repeated song. */
    fun moveQueueItem(fromId: String, targetId: String) {
        val player = _controller.value ?: return
        val fromIndex = canonicalIndex(player, fromId)
        val targetIndex = canonicalIndex(player, targetId)
        if (fromId == targetId || fromIndex < 0 || targetIndex < 0 ||
            !player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
        val displayed = state.value.queue.map { it.entryId }
        val down = displayed.indexOf(fromId) < displayed.indexOf(targetId)
        // An explicit manual order restores linear traversal, then anchors the move to target.
        if (player.shuffleModeEnabled) player.shuffleModeEnabled = false
        var destination = targetIndex + if (down) 1 else 0
        if (fromIndex < destination) destination--
        player.moveMediaItem(fromIndex, destination.coerceIn(0, player.mediaItemCount - 1))
        refresh()
    }

    fun removeQueueItem(entryId: String) {
        val player = _controller.value ?: return
        val index = canonicalIndex(player, entryId)
        if (index !in 0 until player.mediaItemCount ||
            !player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
        player.removeMediaItem(index)
        refresh()
    }

    fun playQueueEntry(entryId: String) {
        val player = _controller.value ?: return
        val index = canonicalIndex(player, entryId)
        if (index !in 0 until player.mediaItemCount ||
            !player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) return
        player.seekTo(index, 0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) player.play()
        refresh()
    }

    private fun canonicalIndex(player: Player, entryId: String): Int =
        (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == entryId } ?: -1

    private suspend fun waitForActiveProfile(songs: List<SongEntity>): Boolean {
        val mediaController = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { controller.first { it != null } }
        if (mediaController == null) {
            requestFailure = PlaybackFailure.CONNECTION
            refresh()
            return false
        }
        val profileId = songs.first().profileId
        val activeId = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
        if (songs.any { it.profileId != profileId } || activeId != profileId) {
            requestFailure = PlaybackFailure.REQUEST
            refresh()
            return false
        }
        return true
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
        val itemCount = player?.mediaItemCount ?: 0
        val currentIndex = player?.currentMediaItemIndex ?: -1
        val queue = queueSnapshot(player, itemCount, currentIndex)
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
            queue = queue,
            shuffleOn = player?.shuffleModeEnabled == true,
            canEditQueue = itemCount > 0 && queue.all { it.entryId.startsWith(QueueIdentity.PREFIX) } &&
                player?.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) == true,
            canShuffle = itemCount > 1 && player?.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE) == true,
            failure = if (player?.playerError != null) PlaybackFailure.PLAYBACK else requestFailure,
        )
    }

    /** Rebuild the queue rows only when timeline, size or current index actually changed. */
    private fun queueSnapshot(player: MediaController?, count: Int, currentIndex: Int): List<PlaybackQueueEntry> {
        if (player == null || count == 0) {
            queueCacheKey = null
            queueCache = emptyList()
            return emptyList()
        }
        val timeline = player.currentTimeline
        val key = Triple(timeline, currentIndex, player.shuffleModeEnabled)
        if (key == queueCacheKey) return queueCache
        val window = Timeline.Window()
        val indices = mutableListOf<Int>()
        var next = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
        while (next != C.INDEX_UNSET && indices.size < count) {
            indices.add(next)
            next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        queueCache = indices.map { index ->
            val item = player.getMediaItemAt(index)
            val windowDuration = timeline.getWindow(index, window).durationMs
            PlaybackQueueEntry(
                entryId = item.mediaId,
                mediaId = item.mediaMetadata.extras?.getString(QueueIdentity.SONG) ?: item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                durationMs = windowDuration.takeIf { it != C.TIME_UNSET && it > 0 } ?: item.mediaMetadata.durationMs,
                isCurrent = index == currentIndex,
            )
        }
        queueCacheKey = key
        return queueCache
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
