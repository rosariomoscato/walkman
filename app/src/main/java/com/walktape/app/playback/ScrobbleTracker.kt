package com.walktape.app.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.walktape.app.core.db.ListeningStateEntity
import com.walktape.app.core.db.ScrobbleEventEntity
import java.util.UUID
import kotlinx.coroutines.*

/** Two audible clocks, one logical listening owner. Silent preload never creates a pass. */
class ScrobbleTracker(private val player: Player, private val store: ListeningStore,
    private val scope: CoroutineScope, private val mainAudible: () -> Boolean = { player.isPlaying }) : Player.Listener {
    private var owner: Long? = null
    private var state: ListeningStateEntity? = null
    private var anchor: Long? = null
    private var duration: Long? = null
    private var ended = false
    private var tick: Job? = null
    private var incoming: Player? = null
    private var incomingState: ListeningStateEntity? = null
    private var incomingAnchor: Long? = null
    private var incomingAudible = false
    private var restoredIncoming: ListeningStateEntity? = null

    init {
        player.addListener(this)
        tick = scope.launch { while (isActive) { delay(1_000); sample(); sampleIncoming() } }
    }

    suspend fun profile(profileId: Long?) {
        sample(); sampleIncoming()
        anchor = null; owner = null; state = null; duration = null
        incoming = null; incomingState = null; incomingAnchor = null; incomingAudible = false
        restoredIncoming = null
        if (profileId == null) return
        val saved = store.load(profileId)
        val overlap = store.loadOverlap(profileId)
        val currentId = player.currentMediaItem?.mediaId
        val outgoingReceipt = overlap?.outgoingSnapshot?.let {
            runCatching { com.google.gson.Gson().fromJson(it, ListeningStateEntity::class.java) }.getOrNull()
        }?.takeIf { it.profileId == profileId && it.mediaId == currentId }
        owner = profileId
        state = saved?.takeIf { it.mediaId == currentId } ?: outgoingReceipt
        if (overlap != null) {
            when {
                !overlap.promoted && overlap.mediaId == currentId -> {
                    state = overlap.asPrimary()
                    store.promoteOverlap(state!!, saved?.takeIf { it.mediaId != currentId })
                }
                !overlap.promoted -> restoredIncoming = overlap.asPrimary()
                outgoingReceipt != null && (saved?.mediaId != currentId || saved?.eventId == outgoingReceipt.eventId) ->
                    restoredIncoming = overlap.asPrimary()
                else -> store.clearOverlap(profileId)
            }
        }
        sample()
    }

    /** Called only with the real deck and its actual non-zero envelope/isPlaying state. */
    fun overlap(deck: Player?, audible: Boolean) {
        if (owner == null) return
        if (incoming !== deck) {
            sampleIncoming()
            incoming = deck; incomingState = null; incomingAnchor = null; incomingAudible = false
        }
        if (incomingAudible != audible) {
            sampleIncoming() // settle the old interval before changing audibility
            incomingAudible = audible
            sampleIncoming()
        }
    }

    /** Must happen before the logical owner switches decks; the event ID is retained. */
    fun promoteOverlap() {
        settlePrimary()
        anchor = null
        sampleIncoming()
        val promoted = incomingState
        if (promoted != null) {
            store.promoteOverlap(promoted, state)
            state = promoted
            anchor = incomingAnchor
            duration = incoming?.duration?.takeIf { it > 0 && it != C.TIME_UNSET }
        } else state = null
        incoming = null; incomingState = null; incomingAnchor = null; incomingAudible = false
        restoredIncoming = null
        ended = false
    }

    fun cancelOverlap(preserve: Boolean) {
        sampleIncoming()
        val retained = incomingState ?: restoredIncoming
        incoming = null; incomingState = null; incomingAnchor = null; incomingAudible = false
        // Queue edits may retain the successor UUID, or remove the current item and
        // select that successor immediately. Keep its already-audible prefix/ACK ID.
        // A different next audible UUID replaces it; a different actual transition
        // explicitly discards it in changePass. Profile teardown reloads it from Room.
        restoredIncoming = if (preserve) null else retained
    }

    private fun newState(item: MediaItem): ListeningStateEntity? {
        val id = owner ?: return null
        val song = item.mediaMetadata.extras?.getString(QueueIdentity.SONG) ?: return null
        return ListeningStateEntity(id, item.mediaId, UUID.randomUUID().toString(), song,
            item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString(),
            item.mediaMetadata.albumTitle?.toString(), System.currentTimeMillis(), 0, false)
    }

    private fun settlePrimary() {
        val current = state ?: return
        val now = SystemClock.elapsedRealtime()
        val elapsed = anchor?.let { (now - it).coerceAtLeast(0) } ?: 0L
        val (updated, event) = accrue(current, elapsed, duration)
        state = updated
        if (anchor != null) anchor = now
        store.save(updated, event)
    }

    private fun sample() {
        if (owner == null) return
        val item = player.currentMediaItem ?: run { settlePrimary(); anchor = null; return }
        if (state?.mediaId != item.mediaId) {
            changePass(item)
        }
        if (state == null && !mainAudible()) return
        if (state == null) state = newState(item)
        duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: item.mediaMetadata.durationMs?.takeIf { it > 0 }
        settlePrimary()
        anchor = if (mainAudible()) SystemClock.elapsedRealtime() else null
    }

    private fun sampleIncoming() {
        val deck = incoming ?: return
        val item = deck.currentMediaItem ?: return
        if (incomingState == null && incomingAudible) {
            incomingState = restoredIncoming?.takeIf { it.mediaId == item.mediaId } ?: newState(item)
            restoredIncoming = null
        }
        val current = incomingState ?: return
        val now = SystemClock.elapsedRealtime()
        val elapsed = incomingAnchor?.let { (now - it).coerceAtLeast(0) } ?: 0L
        val length = deck.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: item.mediaMetadata.durationMs?.takeIf { it > 0 }
        val (updated, event) = accrue(current, elapsed, length)
        incomingState = updated
        incomingAnchor = if (incomingAudible) now else null
        store.saveOverlap(updated, event)
    }

    private fun accrue(current: ListeningStateEntity, elapsed: Long, length: Long?): Pair<ListeningStateEntity, ScrobbleEventEntity?> {
        var updated = current.copy(listenedMs = current.listenedMs + elapsed)
        val earned = !updated.emitted && length != null && updated.listenedMs >= minOf(length / 2, 240_000L)
        val event = if (earned) ScrobbleEventEntity(updated.profileId, updated.eventId, updated.songId,
            updated.title, updated.artist, updated.album, updated.playedAtMs, updated.listenedMs,
            System.currentTimeMillis()) else null
        if (earned) updated = updated.copy(emitted = true)
        return updated to event
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) { sample() }
    override fun onVolumeChanged(volume: Float) { sample() }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT || state?.mediaId != mediaItem?.mediaId) {
            changePass(mediaItem, resumePrefix = reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        }
        ended = false
        sample()
    }

    private fun changePass(item: MediaItem?, resumePrefix: Boolean = true) {
        settlePrimary()
        val outgoing = state
        val retained = if (resumePrefix) restoredIncoming?.takeIf { it.mediaId == item?.mediaId } else null
        state = retained; anchor = null; duration = null
        if (restoredIncoming != null) {
            if (retained != null) store.promoteOverlap(retained, outgoing)
            else owner?.let { store.clearOverlap(it) }
            restoredIncoming = null
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) { sample(); ended = true; anchor = null }
        else if (ended && playbackState == Player.STATE_READY) {
            state = null; anchor = null; ended = false; sample()
        }
    }

    fun close() {
        sample(); sampleIncoming()
        tick?.cancel()
        player.removeListener(this)
        anchor = null; owner = null; incoming = null; incomingAnchor = null
    }
}
