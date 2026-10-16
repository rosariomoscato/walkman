package com.walktape.app.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * W09-T02 crossfade engine. The session player (deck A) keeps owning the queue, the
 * session, focus, timer, scrobbling and persistence; a short-lived secondary deck plays
 * only the entering track during the fade window. Deck B never requests audio focus and
 * never starts unless deck A is actually playing, so pause, timer expiry, teardown and
 * profile switches cannot leave an incoming song running by itself. Volumes compose with
 * every other writer (sleep timer fade, focus ducking) by tracking the baseline deck A
 * volume before each ramp step.
 *
 * Gapless playback itself stays native ExoPlayer behaviour: when crossfade is off, or the
 * conditions for a fade are not met (repeat one, end of queue, unavailable secondary
 * source in offline-only), the transition is the ordinary Media3 one.
 */
class CrossfadeCoordinator(
    private val scope: CoroutineScope,
    private val main: ExoPlayer,
    private val deckBuilder: () -> ExoPlayer,
    private val enabled: () -> Boolean,
    private val durationMs: () -> Int,
    private val transitionAllowed: () -> Boolean = { true },
    private val focusGain: () -> Float = { 1f },
    private val report: (Status) -> Unit = {},
) {
    data class Status(val active: Boolean = false, val incomingPlaying: Boolean = false)
    private var deck: ExoPlayer? = null
    private var ticker: Job? = null
    private var fading = false
    private var fadeIndex = -1
    private var fadeDurationMs = 0
    private var fadeStartRemainingMs = 0L
    private var baseline = 1f
    private var lastWrittenA = -1f
    private var completing = false
    private var attemptedMediaId: String? = null

    private val deckListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // Secondary source failed (including offline-only refusals): the fade is
            // abandoned and the main deck continues with its normal transition.
            cancel()
        }
    }

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && fading) cancel()
        }
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            // Deck A advanced on its own (a missed tick under load): adopt the new item.
            if (fading && !completing) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) completeSwap() else cancel()
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> if (fading) cancel()
                Player.STATE_ENDED -> if (fading) completeSwap()
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && fading && !completing) cancel()
            else if (fading && main.playWhenReady) deck?.play()
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo, reason: Int) {
            if (fading && !completing && reason == Player.DISCONTINUITY_REASON_SEEK) cancel()
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            if (fading && repeatMode == Player.REPEAT_MODE_ONE) cancel()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (fading) cancel()
        }
    }

    fun start() {
        main.addListener(listener)
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    fun release() {
        ticker?.cancel()
        main.removeListener(listener)
        if (fading) cancel() else releaseDeck()
    }

    private fun tick() {
        val fadeWindow = durationMs()
        val active = enabled() && transitionAllowed() && main.isPlaying &&
            main.repeatMode != Player.REPEAT_MODE_ONE
        if (!active) {
            if (fading) cancel()
            return
        }
        val duration = main.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val remaining = duration - main.currentPosition
        if (remaining > fadeWindow + LOOKAHEAD_MS) {
            if (fading) cancel() // a seek moved the position away from the end
            return
        }
        if (!fading) {
            if (!main.hasNextMediaItem()) return // end of queue: no incoming track
            if (attemptedMediaId == main.currentMediaItem?.mediaId) return
            attemptedMediaId = main.currentMediaItem?.mediaId
            startFade(main.nextMediaItemIndex, fadeWindow, remaining)
        }
        updateRamp(remaining)
        if (remaining <= SWAP_EPSILON_MS) completeSwap()
    }

    private fun startFade(nextIndex: Int, window: Int, remaining: Long) {
        val item = main.getMediaItemAt(nextIndex)
        baseline = main.volume
        lastWrittenA = baseline
        fadeDurationMs = window
        fadeStartRemainingMs = remaining
        fadeIndex = nextIndex
        val secondary = deckBuilder()
        secondary.addListener(deckListener)
        secondary.volume = 0f
        secondary.setMediaItem(item)
        secondary.prepare()
        if (main.playWhenReady) secondary.play()
        deck = secondary
        fading = true
    }

    private fun updateRamp(remaining: Long) {
        val secondary = deck ?: return
        val elapsed = (fadeStartRemainingMs - remaining).coerceIn(0L, fadeDurationMs.toLong())
        val t = elapsed.toFloat() / fadeDurationMs.toFloat()
        val currentA = main.volume
        if (currentA != lastWrittenA) baseline = currentA // timer fade or focus ducking wrote it
        val out = (baseline * (1f - t)).coerceIn(0f, 1f)
        val into = (baseline * t).coerceIn(0f, 1f)
        main.setVolume(out)
        lastWrittenA = out
        secondary.setVolume(into * focusGain().coerceIn(0f, 1f))
        report(Status(true, secondary.isPlaying))
    }

    private fun completeSwap() {
        if (!fading || completing || !main.playWhenReady || !transitionAllowed()) {
            if (fading && !completing) cancel()
            return
        }
        completing = true
        val secondary = deck
        val position = if (secondary != null && secondary.playbackState != Player.STATE_ENDED)
            secondary.currentPosition.coerceAtLeast(0L) else 0L
        main.setVolume(baseline)
        lastWrittenA = baseline
        main.seekTo(fadeIndex, position)
        releaseDeck()
        completing = false
    }

    private fun cancel(restoreVolume: Boolean = true) {
        if (restoreVolume) {
            if (main.volume != lastWrittenA) baseline = main.volume
            main.setVolume(baseline)
            lastWrittenA = baseline
        }
        releaseDeck()
    }

    private fun releaseDeck() {
        deck?.run {
            removeListener(deckListener)
            stop()
            clearMediaItems()
            release()
        }
        deck = null
        fading = false
        fadeIndex = -1
        report(Status())
    }

    companion object {
        private const val TICK_MS = 100L
        private const val LOOKAHEAD_MS = 150L
        private const val SWAP_EPSILON_MS = 60L
    }
}
