package com.walktape.app.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.walktape.app.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Captures on the player's main looper; Room writes/read barriers live in the application store. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackPersistence(
    private val player: ExoPlayer,
    private val shuffle: ServiceShuffle,
    private val store: QueueStore,
    private val scope: CoroutineScope,
    private val setConnection: (StreamProfile?) -> Unit,
) : Player.Listener {
    private var owner: Long? = null
    private var changing = true
    private var timeline: Timeline? = null
    private var cachedItems = emptyList<SavedQueueItem>()
    private var cachedTraversal = emptyList<String>()
    private var cachedShuffle = false
    private var profileJob: Job? = null
    private var tickJob: Job? = null

    fun start() {
        player.addListener(this)
        profileJob = scope.launch {
            Graph.profiles.profiles.map { profiles -> profiles.firstOrNull { it.isActive }?.id }
                .distinctUntilChanged().collectLatest { changeProfile(it) }
        }
        tickJob = scope.launch {
            while (isActive) { delay(1000); if (player.isPlaying) save() }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) { save() }

    private fun save() {
        val id = owner ?: return
        if (changing) return
        val current = player.currentTimeline
        val changed = timeline !== current
        if (changed) {
            val window = Timeline.Window()
            cachedItems = (0 until player.mediaItemCount).map { index ->
                val item = player.getMediaItemAt(index)
                SavedQueueItem(item.mediaId, item.mediaMetadata.extras?.getString(QueueIdentity.SONG).orEmpty(),
                    item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString(),
                    item.mediaMetadata.albumTitle?.toString(),
                    current.getWindow(index, window).durationMs.takeIf { it > 0 && it != C.TIME_UNSET }
                        ?: item.mediaMetadata.durationMs?.takeIf { it > 0 },
                    item.mediaMetadata.extras?.getString(QueueIdentity.ORIGIN))
            }
            timeline = current
        }
        if (changed || cachedShuffle != player.shuffleModeEnabled) {
            val traversal = mutableListOf<String>()
            var index = current.getFirstWindowIndex(player.shuffleModeEnabled)
            while (index != C.INDEX_UNSET && traversal.size < cachedItems.size) {
                traversal.add(player.getMediaItemAt(index).mediaId)
                index = current.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            }
            cachedTraversal = traversal
            cachedShuffle = player.shuffleModeEnabled
        }
        val queue = SavedQueue(id, cachedItems, cachedTraversal, player.currentMediaItem?.mediaId,
            if (cachedItems.isEmpty()) 0 else player.currentPosition.coerceAtLeast(0), player.repeatMode,
            player.shuffleModeEnabled && cachedItems.isNotEmpty(), System.currentTimeMillis())
        store.enqueue(queue) // Complete structural validation happens off the player's looper.
    }

    private suspend fun changeProfile(id: Long?) {
        // Save the old owner's paused position before any stop/clear event can overwrite it.
        player.pause()
        save()
        owner = null
        changing = true
        setConnection(null)
        player.stop()
        player.clearMediaItems()
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        timeline = null
        cachedItems = emptyList()
        cachedTraversal = emptyList()
        if (id == null) { changing = false; return }
        val stored = store.load(id) // FIFO barrier: includes final saves from the old service.
        val client = Graph.profiles.clientFor(id)
        if (Graph.database.profileDao().active()?.id != id) return
        if (stored != null) restore(stored)
        // A corrupt/unreadable saved queue must not be overwritten by an empty default queue.
        if (id !in store.failedProfiles.value) owner = id
        changing = false
        setConnection(client?.let { StreamProfile(id, it) })
    }

    private suspend fun restore(queue: SavedQueue) {
        val songs = queue.items.map { it.songId }.distinct().chunked(500).flatMap {
            Graph.database.libraryDao().songsByIds(queue.profileId, it)
        }.associateBy { it.id }
        val items = queue.items.map { item ->
            val extras = Bundle().apply {
                putString(QueueIdentity.SONG, item.songId)
                putString(SleepTimer.ALBUM_ID, songs[item.songId]?.albumId)
                putString(QueueIdentity.ORIGIN, item.originName)
                songs[item.songId]?.coverArt?.let { putString(QueueIdentity.COVER_ART, it) }
            }
            MediaItem.Builder().setMediaId(item.entryId).setUri(SubsonicStream.uri(queue.profileId, item.songId).toUri())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist)
                    .setAlbumTitle(item.album).setDurationMs(item.durationMs).setExtras(extras).build()).build()
        }
        val index = queue.items.indexOfFirst { it.entryId == queue.currentId }.coerceAtLeast(0)
        val duration = queue.items.getOrNull(index)?.durationMs
        val position = if (duration != null) queue.positionMs.coerceAtMost(duration) else queue.positionMs
        if (items.isNotEmpty()) player.setMediaItems(items, index, position)
        player.repeatMode = queue.repeatMode
        shuffle.restore(queue.traversal, queue.shuffle)
        player.pause()
        // Deliberately no prepare/play: restoring metadata must work offline without HTTP/audio.
    }

    fun close() {
        player.pause()
        save()
        owner = null
        changing = true
        profileJob?.cancel()
        tickJob?.cancel()
        player.removeListener(this)
        setConnection(null)
    }
}
