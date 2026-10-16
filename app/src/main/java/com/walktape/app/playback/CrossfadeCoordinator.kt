package com.walktape.app.playback

import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One stable logical ExoPlayer for the session, queue, timer and persistence. Internal decks
 * may change, but the already audible incoming deck becomes the owner WITHOUT seek, prepare,
 * rebuilding media sources or restarting its AudioTrack. All calls stay on the app looper.
 *
 * The routing proxy implements the public ExoPlayer interface (including setShuffleOrder,
 * used by ServiceShuffle). It does not reflect into Media3 internals. Listener registration
 * and events belong to this owner, never to whichever deck happened to exist at registration.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CrossfadeCoordinator(
    private val scope: CoroutineScope,
    main: ExoPlayer,
    private val deckBuilder: () -> ExoPlayer,
    private val enabled: () -> Boolean,
    private val durationMs: () -> Int,
    private val transitionAllowed: (Int) -> Boolean = { true },
    private val report: (Status) -> Unit = {},
    private val deckReleased: (ExoPlayer) -> Unit = {},
) {
    data class Status(val active: Boolean = false, val incomingPlaying: Boolean = false)
    @Volatile var activeDeck: ExoPlayer = main
        private set
    private var incoming: ExoPlayer? = null
    private val listeners = linkedSetOf<Player.Listener>()
    private val bridges = java.util.IdentityHashMap<ExoPlayer, Player.Listener>()
    private var ticker: Job? = null
    private var released = false
    private var desiredPlay = main.playWhenReady
    private var baseVolume = main.volume
    private var duck = 1f
    private var focusGranted = true
    private var focus: LogicalAudioFocus? = null
    private var fading = false
    private var fadeWindow = 0L
    private var fadeStartRemaining = 0L
    private var fraction = 0f
    private var attempted: String? = null
    private var targetId: String? = null
    private var outgoingInfo: Player.PositionInfo? = null

    /** The only player exposed outside the engine. Identity never changes on handover. */
    val player: ExoPlayer = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,
        arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "WalkTapeLogicalPlayer"
            "addListener" -> { listeners.add(args!![0] as Player.Listener); null }
            "removeListener" -> { listeners.remove(args!![0] as Player.Listener); null }
            "getVolume" -> baseVolume
            "setVolume" -> { setVolume(args!![0] as Float); null }
            "getPlayWhenReady" -> desiredPlay
            "getPlaybackSuppressionReason" -> if (desiredPlay && !focusGranted)
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS else activeDeck.playbackSuppressionReason
            "play", "pause", "setPlayWhenReady" -> {
                val value = when (method.name) { "play" -> true; "pause" -> false; else -> args!![0] as Boolean }
                setPlaying(value, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                null
            }
            "release" -> { release(); null }
            else -> {
                // A manual command invalidates the prepared successor and settles its
                // listening pass before it can affect a stale UUID/index/profile.
                if (isStructuralCommand(method.name)) {
                    cancelIncoming()
                    attempted = null
                }
                val result = call(method, activeDeck, args)
                if (method.name == "prepare" || method.name == "stop") updateFocus()
                result
            }
        }
    } as ExoPlayer

    /** Service-owned listening hooks. Preparing an inaudible deck never calls audible=true. */
    var overlap: (ExoPlayer?, Boolean) -> Unit = { _, _ -> }
    var promoting: () -> Unit = {}
    var cancelOverlap: (Boolean) -> Unit = {}

    init { attach(main) }

    fun attachFocus(controller: LogicalAudioFocus) {
        focus = controller
        focusGranted = false
        applyPlayState()
        updateFocus()
    }

    fun onFocusCommand(command: Int) {
        if (released) return
        when (command) {
            androidx.media3.common.audio.AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY -> {
                focusGranted = false
                setPlaying(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            }
            androidx.media3.common.audio.AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK -> setFocusGranted(false)
            else -> setFocusGranted(true)
        }
    }

    fun onDucking(multiplier: Float) {
        if (released) return
        duck = multiplier.coerceIn(0f, 1f)
        applyVolumes()
        sampleOverlap()
    }

    private fun setFocusGranted(value: Boolean) {
        if (focusGranted == value) return
        focusGranted = value
        applyPlayState()
        sampleOverlap()
        listeners.toList().forEach { it.onPlaybackSuppressionReasonChanged(player.playbackSuppressionReason) }
        events(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED, Player.EVENT_IS_PLAYING_CHANGED)
    }

    private fun updateFocus() { focus?.update(desiredPlay, activeDeck.playbackState) }

    private fun setPlaying(value: Boolean, reason: Int) {
        val changed = desiredPlay != value
        desiredPlay = value
        applyPlayState()
        sampleOverlap()
        updateFocus()
        if (changed) {
            listeners.toList().forEach { it.onPlayWhenReadyChanged(value, reason) }
            events(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_IS_PLAYING_CHANGED)
        }
    }

    private fun setVolume(value: Float) {
        val new = value.coerceIn(0f, 1f)
        if (baseVolume == new) return
        baseVolume = new
        applyVolumes()
        sampleOverlap()
        listeners.toList().forEach { it.onVolumeChanged(new) }
        events(Player.EVENT_VOLUME_CHANGED)
    }

    private fun applyPlayState() {
        val play = desiredPlay && focusGranted
        activeDeck.playWhenReady = play
        incoming?.playWhenReady = play && fading
    }

    private fun applyVolumes() {
        activeDeck.volume = baseVolume * duck * if (fading) 1f - fraction else 1f
        incoming?.volume = baseVolume * duck * if (fading) fraction else 0f
    }

    private fun attach(deck: ExoPlayer) {
        val bridge = Proxy.newProxyInstance(Player.Listener::class.java.classLoader,
            arrayOf(Player.Listener::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "WalkTapeDeckListener"
                else -> { onDeckEvent(deck, method, args); null }
            }
        } as Player.Listener
        bridges[deck] = bridge
        deck.addListener(bridge)
    }

    private fun onDeckEvent(deck: ExoPlayer, method: Method, args: Array<out Any?>?) {
        if (released) return
        if (deck !== activeDeck) {
            if (deck === incoming) {
                if (method.name == "onPlayerError") cancelIncoming()
                else if (method.name == "onMediaItemTransition" && fading &&
                    deck.currentMediaItem?.mediaId != targetId) cancelIncoming()
                else if (method.name == "onIsPlayingChanged") sampleOverlap()
            }
            return
        }
        when (method.name) {
            "onVolumeChanged" -> return // deck envelope is not the public/Android volume
            "onPlayWhenReadyChanged" -> {
                if (args?.get(1) == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
                    setPlaying(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
                return
            }
            "onMediaItemTransition" -> {
                if (fading && args?.get(1) == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    promote()
                    return
                }
                cancelIncoming()
                attempted = null
            }
            "onPositionDiscontinuity" -> {
                if (fading && args?.lastOrNull() == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    promote()
                    return
                }
            }
            "onPlaybackStateChanged" -> {
                if (activeDeck.playbackState == Player.STATE_IDLE && focus != null) focusGranted = false
                updateFocus()
            }
            "onIsPlayingChanged" -> {
                if (!activeDeck.isPlaying && fading && activeDeck.currentMediaItem?.mediaId == targetId &&
                    incoming?.isPlaying == true && desiredPlay && focusGranted) {
                    promote()
                    return
                }
                if (!activeDeck.isPlaying) incoming?.pause()
                else applyPlayState()
                sampleOverlap()
            }
        }
        if (deck !== activeDeck) return
        if (method.name == "onEvents") {
            listeners.toList().forEach { it.onEvents(player, args!![1] as Player.Events) }
        } else listeners.toList().forEach { call(method, it, args) }
    }

    fun start() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                delay(if (enabled() && desiredPlay && activeDeck.mediaItemCount > 0) 50L else 500L)
                tick()
            }
        }
    }

    private fun tick() {
        if (released) return
        if (!enabled() || !transitionAllowed(activeDeck.currentMediaItemIndex) || activeDeck.repeatMode == Player.REPEAT_MODE_ONE) {
            cancelIncoming(); return
        }
        if (!desiredPlay || !focusGranted || !activeDeck.isPlaying) {
            incoming?.pause(); sampleOverlap(); return
        }
        val duration = activeDeck.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: activeDeck.mediaMetadata.durationMs?.takeIf { it > 0 } ?: return
        val remaining = (duration - activeDeck.currentPosition).coerceAtLeast(0)
        val configured = durationMs().toLong().coerceAtLeast(1)
        if (incoming == null && remaining <= configured + PRELOAD_MS && activeDeck.hasNextMediaItem() &&
            attempted != activeDeck.currentMediaItem?.mediaId) prepareIncoming()
        val next = incoming ?: return
        if (next.playbackState != Player.STATE_READY || next.playerError != null) return
        val effective = minOf(configured, (duration / 2).coerceAtLeast(1),
            (next.duration.takeIf { it > 0 && it != C.TIME_UNSET }
                ?: next.mediaMetadata.durationMs?.takeIf { it > 0 })?.div(2)?.coerceAtLeast(1) ?: configured)
        if (!fading && remaining <= effective) {
            if (remaining < 100) { cancelIncoming(); return }
            fading = true
            fadeWindow = minOf(effective, remaining)
            fadeStartRemaining = remaining
            outgoingInfo = positionInfo(activeDeck)
            applyPlayState()
        }
        if (!fading) return
        fraction = ((fadeStartRemaining - remaining).toFloat() / fadeWindow).coerceIn(0f, 1f)
        applyVolumes()
        sampleOverlap()
        report(Status(true, next.isPlaying))
        if (fraction >= 1f || remaining <= 25) promote()
    }

    private fun prepareIncoming() {
        val nextIndex = activeDeck.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex == activeDeck.currentMediaItemIndex) return
        attempted = activeDeck.currentMediaItem?.mediaId
        val deck = deckBuilder()
        attach(deck)
        incoming = deck
        val items = (0 until activeDeck.mediaItemCount).map { activeDeck.getMediaItemAt(it) }
        targetId = items[nextIndex].mediaId
        deck.volume = 0f
        deck.playWhenReady = false
        deck.setMediaItems(items, nextIndex, 0)
        deck.repeatMode = activeDeck.repeatMode
        deck.shuffleModeEnabled = activeDeck.shuffleModeEnabled
        deck.playbackParameters = activeDeck.playbackParameters
        deck.skipSilenceEnabled = activeDeck.skipSilenceEnabled
        val traversal = mutableListOf<Int>()
        val timeline = activeDeck.currentTimeline
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && traversal.size < items.size) {
            traversal.add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        if (traversal.size == items.size)
            deck.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(traversal.toIntArray(), System.nanoTime()))
        deck.prepare()
    }

    private fun sampleOverlap() {
        val deck = incoming ?: return
        overlap(deck, fading && desiredPlay && focusGranted && activeDeck.isPlaying &&
            deck.isPlaying && baseVolume * duck * fraction > 0f)
        report(Status(fading && desiredPlay && focusGranted && activeDeck.isPlaying,
            deck.isPlaying && desiredPlay && focusGranted))
    }

    private fun promote() {
        val next = incoming ?: return
        if (!fading || !desiredPlay || !focusGranted || !transitionAllowed(outgoingInfo?.mediaItemIndex ?: activeDeck.currentMediaItemIndex) ||
            !next.isPlaying || next.currentPosition <= 0 || next.playbackState != Player.STATE_READY ||
            next.currentMediaItem?.mediaId != targetId) {
            cancelIncoming(); return
        }
        sampleOverlap()
        val old = activeDeck
        val oldPosition = outgoingInfo ?: positionInfo(old)
        promoting() // settle both clocks and atomically promote the same incoming event ID
        activeDeck = next
        incoming = null
        fading = false
        fraction = 0f
        targetId = null
        attempted = null
        applyVolumes()
        // The incoming decoder/source/output KEEP RUNNING. Only the old deck is released.
        dispose(old)
        val newPosition = positionInfo(next)
        listeners.toList().forEach {
            it.onTimelineChanged(next.currentTimeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            it.onPositionDiscontinuity(oldPosition, newPosition, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
            it.onMediaItemTransition(next.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            it.onMediaMetadataChanged(next.mediaMetadata)
            it.onTracksChanged(next.currentTracks)
            it.onAvailableCommandsChanged(next.availableCommands)
        }
        events(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED,
            Player.EVENT_TRACKS_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED)
        report(Status())
        updateFocus()
    }

    fun resetForProfile() { cancelIncoming(preserveListening = true); attempted = null }

    private fun cancelIncoming(preserveListening: Boolean = false) {
        val deck = incoming ?: return
        sampleOverlap()
        cancelOverlap(preserveListening)
        incoming = null
        fading = false
        fraction = 0f
        targetId = null
        applyVolumes()
        dispose(deck)
        report(Status())
    }

    private fun dispose(deck: ExoPlayer) {
        bridges.remove(deck)?.let { deck.removeListener(it) }
        deck.release()
        deckReleased(deck)
    }

    fun release() {
        if (released) return
        ticker?.cancel()
        cancelIncoming(preserveListening = true)
        released = true
        focus?.release()
        dispose(activeDeck)
        listeners.clear()
    }

    private fun events(vararg flags: Int) {
        val builder = FlagSet.Builder()
        flags.forEach { builder.add(it) }
        val events = Player.Events(builder.build())
        listeners.toList().forEach { it.onEvents(player, events) }
    }

    private fun positionInfo(deck: ExoPlayer): Player.PositionInfo = Player.PositionInfo(
        null, deck.currentMediaItemIndex, deck.currentMediaItem, null, deck.currentPeriodIndex,
        deck.currentPosition, deck.contentPosition, deck.currentAdGroupIndex, deck.currentAdIndexInAdGroup)

    private fun isStructuralCommand(name: String): Boolean = name.startsWith("seek") ||
        name in STRUCTURAL_COMMANDS

    private fun call(method: Method, target: Any, args: Array<out Any?>?): Any? = try {
        method.invoke(target, *(args ?: emptyArray()))
    } catch (e: InvocationTargetException) { throw e.targetException }

    companion object {
        private const val PRELOAD_MS = 10_000L
        private val STRUCTURAL_COMMANDS = setOf("stop", "clearMediaItems", "setMediaItem", "setMediaItems", "addMediaItem", "addMediaItems",
            "removeMediaItem", "removeMediaItems", "moveMediaItem", "moveMediaItems", "replaceMediaItem",
            "replaceMediaItems", "setShuffleModeEnabled", "setShuffleOrder", "setRepeatMode", "setPlaybackParameters",
            "setPlaybackSpeed", "setSkipSilenceEnabled")
    }
}
