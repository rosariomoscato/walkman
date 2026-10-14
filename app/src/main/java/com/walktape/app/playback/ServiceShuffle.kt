package com.walktape.app.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder

/** Service-owned traversal: survives controller release/rebind; never replaces media sources. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ServiceShuffle(private val player: ExoPlayer) : Player.Listener {
    private var order = emptyList<String>()
    private var enabling = false
    private var restoring = false

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (restoring) return
        if (shuffleModeEnabled) prepareEnable() else order = emptyList()
    }

    /** Install a persisted permutation exactly, without generating another random order. */
    fun restore(saved: List<String>, enabled: Boolean) {
        restoring = true
        try {
            order = saved
            player.shuffleModeEnabled = enabled
            if (enabled) apply((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
        } finally { restoring = false }
    }

    fun prepareEnable() {
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        order = shuffledKeepingCurrent(ids, player.currentMediaItemIndex)
        enabling = true
        try { apply(ids) } finally { enabling = false }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            if (enabling || restoring) return
            if (!player.shuffleModeEnabled || player.mediaItemCount == 0) { order = emptyList(); return }
            val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            val ids = items.map { it.mediaId }
            val present = ids.toHashSet()
            if (order.none { it in present }) { prepareEnable(); return }
            order = reconcileShuffleOrder(order, ids,
                items.associate { it.mediaId to it.mediaMetadata.extras?.getString(QueueIdentity.AFTER) })
            apply(ids)
        }
    }

    private fun apply(ids: List<String>) {
        if (ids.isEmpty()) return
        val indices = ids.withIndex().associate { it.value to it.index }
        val target = order.mapNotNull { indices[it] }
        if (target.size != ids.size) return
        val existing = mutableListOf<Int>()
        val timeline = player.currentTimeline
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && existing.size < ids.size) {
            existing.add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        if (existing != target) player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(target.toIntArray(), System.nanoTime()))
    }
}
