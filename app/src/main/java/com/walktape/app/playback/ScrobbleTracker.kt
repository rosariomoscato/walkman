package com.walktape.app.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.walktape.app.core.db.ListeningStateEntity
import com.walktape.app.core.db.ScrobbleEventEntity
import java.util.UUID
import kotlinx.coroutines.*

/** Service-owned listening clock: neither position seeks nor controller rebinds add time. */
class ScrobbleTracker(private val player: Player, private val store: ListeningStore,
    private val scope: CoroutineScope) : Player.Listener {
    private var owner: Long? = null
    private var state: ListeningStateEntity? = null
    private var anchor: Long? = null
    private var ended = false
    private var tick: Job? = null

    init {
        player.addListener(this)
        tick = scope.launch { while (isActive) { delay(1_000); sample() } }
    }

    suspend fun profile(profileId: Long?) {
        sample()
        anchor = null
        owner = null
        state = null
        if (profileId == null) return
        val saved = store.load(profileId)
        owner = profileId
        state = saved?.takeIf { it.mediaId == player.currentMediaItem?.mediaId }
        sample()
    }

    private fun sample() {
        val id = owner ?: return
        val item = player.currentMediaItem ?: run { anchor = null; return }
        val song = item.mediaMetadata.extras?.getString(QueueIdentity.SONG) ?: return
        if (state == null && !player.isPlaying) return
        var current = state?.takeIf { it.mediaId == item.mediaId } ?: ListeningStateEntity(
            id, item.mediaId, UUID.randomUUID().toString(), song,
            item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString(),
            item.mediaMetadata.albumTitle?.toString(), System.currentTimeMillis(), 0, false)
        val now = SystemClock.elapsedRealtime()
        anchor?.let { current = current.copy(listenedMs = current.listenedMs + (now - it).coerceAtLeast(0)) }
        anchor = if (player.isPlaying) now else null
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
        val earned = !current.emitted && duration != null &&
            current.listenedMs >= minOf(duration / 2, 240_000L)
        val event = if (earned) ScrobbleEventEntity(id, current.eventId, song, current.title,
            current.artist, current.album, current.playedAtMs, current.listenedMs, System.currentTimeMillis()) else null
        if (earned) current = current.copy(emitted = true)
        state = current
        store.save(current, event)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) { sample() }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Automatic repeat creates a new listening pass, including repeat-one on the same UUID.
        anchor = null
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT || state?.mediaId != mediaItem?.mediaId) state = null
        ended = false
        sample()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) { sample(); ended = true; anchor = null }
        else if (ended && playbackState == Player.STATE_READY) {
            state = null; anchor = null; ended = false; sample()
        }
    }

    fun close() {
        sample()
        tick?.cancel()
        player.removeListener(this)
        anchor = null
        owner = null
    }
}
