package com.walktape.app.playback

import androidx.media3.common.C
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
class SleepTimer {
    enum class Mode { DURATION, TRACK, QUEUE }
    data class State(val mode: Mode? = null, val remainingMs: Long? = null) {
        val armed: Boolean get() = mode != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var player: Player? = null
    private var listener: Player.Listener? = null
    private var job: Job? = null

    fun attach(attachedPlayer: Player) {
        detach()
        player = attachedPlayer
        val watch = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mutable.value.mode == Mode.TRACK && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) pauseAtBoundary()
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
        listener?.let { player?.removeListener(it) }
        listener = null
        player = null
    }

    fun arm(mode: Mode, durationMs: Long = 0L) {
        cancel()
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
        val deadline = System.currentTimeMillis() + totalMs
        while (isActive) {
            val remaining = deadline - System.currentTimeMillis()
            mutable.value = mutable.value.copy(remainingMs = remaining.coerceAtLeast(0))
            if (remaining <= 0) break
            delay(remaining.coerceAtMost(250))
        }
        val attachedPlayer = player
        if (attachedPlayer != null && attachedPlayer.playWhenReady) {
            val start = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= FADE_MS) break
                attachedPlayer.volume = gainFromElapsed(elapsed)
                delay(FADE_STEP_MS)
            }
            attachedPlayer.pause()
        }
        restoreGain()
        mutable.value = State()
    }

    private suspend fun CoroutineScope.runBoundaryWatch(mode: Mode) {
        while (isActive && mutable.value.mode == mode) {
            val attachedPlayer = player
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
        if (!attachedPlayer.playWhenReady) return null
        val timeline = attachedPlayer.currentTimeline
        if (mode == Mode.QUEUE && attachedPlayer.mediaItemCount > 0 &&
            timeline.getNextWindowIndex(attachedPlayer.currentMediaItemIndex, Player.REPEAT_MODE_OFF,
                attachedPlayer.shuffleModeEnabled) != C.INDEX_UNSET
        ) return null
        val duration = attachedPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: attachedPlayer.mediaMetadata.durationMs?.takeIf { it > 0 } ?: return null
        return duration - attachedPlayer.currentPosition
    }

    private fun pauseAtBoundary() {
        if (mutable.value.mode != Mode.TRACK) return
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

        /** Gain during a countdown fade: 1 at the deadline-FADE_MS, 0 at the pause. */
        fun gainFromElapsed(elapsedMs: Long): Float = (1f - elapsedMs.toFloat() / FADE_MS).coerceIn(0f, 1f)

        /** Gain during a boundary fade: 1 when far from the end, 0 at the boundary itself. */
        fun gainFromRemaining(remainingMs: Long): Float = (remainingMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }
}
