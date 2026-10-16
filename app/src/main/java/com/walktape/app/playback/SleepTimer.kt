package com.walktape.app.playback

import androidx.media3.common.C
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sleep timer owned by the process, surviving UI rebinds: a wall-clock countdown or the
 * track/queue boundary fades the player gain briefly and then pauses. The gain is restored
 * afterwards and the Android media volume is never touched. Not persisted across process
 * death by design: a restored queue never starts playing on its own.
 */
@androidx.annotation.MainThread
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SleepTimer {
    enum class Mode { DURATION, TRACK, ALBUM, QUEUE }
    data class State(val mode: Mode? = null, val remainingMs: Long? = null) {
        val armed: Boolean get() = mode != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    /** True only when advancing to the next item cannot pass an armed stop boundary. */
    fun allowsOverlap(mediaItemIndex: Int? = null): Boolean {
        val attachedPlayer = player ?: return false
        return when (val mode = mutable.value.mode) {
            null, Mode.DURATION -> true
            Mode.TRACK -> false
            else -> {
                // Pure query: do not overwrite the watcher's outgoing lastBoundary
                // when a native AUTO callback has already moved the physical cursor.
                val timeline = attachedPlayer.currentTimeline
                val index = mediaItemIndex ?: attachedPlayer.currentMediaItemIndex
                if (timeline.isEmpty || index !in 0 until attachedPlayer.mediaItemCount) false
                else {
                    val next = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, attachedPlayer.shuffleModeEnabled)
                    next != C.INDEX_UNSET && (mode == Mode.QUEUE ||
                        (attachedPlayer.getMediaItemAt(index).mediaMetadata.extras?.getString(ALBUM_ID) == albumId &&
                            attachedPlayer.getMediaItemAt(next).mediaMetadata.extras?.getString(ALBUM_ID) == albumId))
                }
            }
        }
    }
    private var player: Player? = null
    private var listener: Player.Listener? = null
    private var job: Job? = null
    private var albumId: String? = null
    private var lastBoundary = false

    fun attach(attachedPlayer: Player) {
        detach()
        player = attachedPlayer
        val watch = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mode = mutable.value.mode ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    if (mode == Mode.TRACK || (mode == Mode.ALBUM &&
                            (mediaItem?.mediaMetadata?.extras?.getString(ALBUM_ID) != albumId || lastBoundary)) ||
                        (mode == Mode.QUEUE && lastBoundary)) pauseAtBoundary()
                } else if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ||
                    (mode != Mode.DURATION && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)) cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val mode = mutable.value.mode ?: return
                if (playbackState == Player.STATE_ENDED) {
                    if (mode == Mode.TRACK) pauseAtBoundary() else restoreAndDisarm()
                }
            }
        }
        attachedPlayer.addListener(watch)
        listener = watch
    }

    fun detach() {
        cancel() // never retain a released player inside an in-flight fade
        listener?.let { player?.removeListener(it) }
        listener = null
        player = null
    }

    fun arm(mode: Mode, durationMs: Long = 0L) {
        cancel()
        if (player?.currentMediaItem == null || (mode == Mode.DURATION && durationMs !in 1..86_400_000L)) return
        albumId = player?.currentMediaItem?.mediaMetadata?.extras?.getString(ALBUM_ID)
        if (mode == Mode.ALBUM && albumId.isNullOrBlank()) return
        mutable.value = State(mode, durationMs.takeIf { mode == Mode.DURATION && it > 0 })
        job = scope.launch {
            when (mode) {
                Mode.DURATION -> runCountdown(durationMs)
                else -> runBoundaryWatch(mode)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        restoreGain()
        mutable.value = State()
    }

    private suspend fun CoroutineScope.runCountdown(totalMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + totalMs
        while (isActive) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            mutable.value = mutable.value.copy(remainingMs = remaining.coerceAtLeast(0))
            player?.let { if (it.isPlaying && remaining <= FADE_MS) it.volume = gainFromRemaining(remaining) }
            if (remaining <= 0) break
            delay(remaining.coerceAtMost(FADE_STEP_MS))
        }
        player?.pause()
        restoreGain()
        mutable.value = State()
    }

    private suspend fun CoroutineScope.runBoundaryWatch(mode: Mode) {
        while (isActive && mutable.value.mode == mode) {
            val attachedPlayer = player
            if (attachedPlayer != null && attachedPlayer.mediaItemCount == 0) { cancel(); return }
            val remaining = attachedPlayer?.let { boundaryRemainingMs(it, mode) }
            if (attachedPlayer != null && remaining != null && remaining <= FADE_MS) {
                attachedPlayer.volume = gainFromRemaining(remaining)
            } else if (attachedPlayer != null && attachedPlayer.volume != 1f) {
                restoreGain() // the user seeked away from the boundary
            }
            delay(FADE_STEP_MS)
        }
    }

    private fun boundaryRemainingMs(attachedPlayer: Player, mode: Mode): Long? {
        if (!attachedPlayer.isPlaying) return null
        val timeline = attachedPlayer.currentTimeline
        val next = if (timeline.isEmpty) C.INDEX_UNSET else timeline.getNextWindowIndex(
            attachedPlayer.currentMediaItemIndex, Player.REPEAT_MODE_OFF, attachedPlayer.shuffleModeEnabled)
        lastBoundary = next == C.INDEX_UNSET
        if (mode == Mode.ALBUM) {
            if (attachedPlayer.currentMediaItem?.mediaMetadata?.extras?.getString(ALBUM_ID) != albumId) { cancel(); return null }
            lastBoundary = lastBoundary || attachedPlayer.getMediaItemAt(next).mediaMetadata.extras?.getString(ALBUM_ID) != albumId
        }
        if ((mode == Mode.QUEUE || mode == Mode.ALBUM) && !lastBoundary) return null
        val duration = attachedPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: attachedPlayer.mediaMetadata.durationMs?.takeIf { it > 0 } ?: return null
        return duration - attachedPlayer.currentPosition
    }

    private fun pauseAtBoundary() {
        if (mutable.value.mode == null || mutable.value.mode == Mode.DURATION) return
        job?.cancel()
        job = null
        player?.pause()
        restoreGain()
        mutable.value = State()
    }

    private fun restoreAndDisarm() {
        if (mutable.value.mode == null) return
        job?.cancel()
        job = null
        restoreGain()
        mutable.value = State()
    }

    private fun restoreGain() {
        val attachedPlayer = player ?: return
        if (attachedPlayer.volume != 1f) attachedPlayer.volume = 1f
    }

    companion object {
        const val FADE_MS = 3_000L
        const val FADE_STEP_MS = 100L
        const val ALBUM_ID = "wt.albumId"

        /** Gain during a countdown fade: 1 at the deadline-FADE_MS, 0 at the pause. */
        fun gainFromElapsed(elapsedMs: Long): Float = (1f - elapsedMs.toFloat() / FADE_MS).coerceIn(0f, 1f)

        /** Gain during a boundary fade: 1 when far from the end, 0 at the boundary itself. */
        fun gainFromRemaining(remainingMs: Long): Float = (remainingMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }
}
