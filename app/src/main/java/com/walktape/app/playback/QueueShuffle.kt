package com.walktape.app.playback

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * A complete permutation starting at the current occurrence. Native Media3 shuffle uses these
 * indices without changing the canonical playlist or reloading the currently playing source.
 */
fun <T> shuffledKeepingCurrent(items: List<T>, currentIndex: Int,
                               random: Random = SecureRandom().asKotlinRandom()): List<T> {
    if (items.size <= 1) return items
    val safeIndex = currentIndex.coerceIn(0, items.lastIndex)
    val rest = items.filterIndexed { index, _ -> index != safeIndex }.toMutableList()
    for (i in rest.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val temporary = rest[i]
        rest[i] = rest[j]
        rest[j] = temporary
    }
    return listOf(items[safeIndex]) + rest
}

/** Preserve traversal across queue edits, remove only missing occurrences and pin new groups. */
fun reconcileShuffleOrder(previous: List<String>, canonical: List<String>,
    after: Map<String, String?> = emptyMap()): List<String> {
    val present = canonical.toHashSet()
    val known = previous.toHashSet()
    val result = previous.filter { it in present }.toMutableList()
    var offset = 0
    val added = canonical.filter { it !in known }
    while (offset < added.size) {
        val anchor = after[added[offset]]
        val group = mutableListOf(added[offset++])
        while (offset < added.size && after[added[offset]] == anchor) group.add(added[offset++])
        val anchorIndex = result.indexOf(anchor)
        result.addAll(if (anchorIndex >= 0) anchorIndex + 1 else result.size, group)
    }
    return result
}

object QueueIdentity {
    const val PREFIX = "queue/"
    const val SONG = "walktape.queue.song"
    const val AFTER = "walktape.queue.after"
    const val COVER_ART = "walktape.queue.coverArt"
    const val ORIGIN = "walktape.queue.origin"
}
