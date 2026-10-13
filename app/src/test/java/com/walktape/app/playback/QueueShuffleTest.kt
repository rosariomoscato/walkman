package com.walktape.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueShuffleTest {
    private val items = ('a'..'l').toList()

    @Test fun `shuffle keeps the current item first and permutes the rest without losses`() {
        repeat(8) { seed ->
            val currentIndex = seed * 3 % items.size
            val shuffled = shuffledKeepingCurrent(items, currentIndex, Random(seed))
            assertEquals(items[currentIndex], shuffled.first())
            assertEquals(items.size, shuffled.size)
            assertEquals(items.toSet(), shuffled.toSet())
        }
    }

    @Test fun `shuffle is deterministic for a seeded random`() {
        assertEquals(
            shuffledKeepingCurrent(items, 4, Random(42)),
            shuffledKeepingCurrent(items, 4, Random(42)),
        )
    }

    @Test fun `shuffle order differs from the original for a non trivial queue`() {
        val shuffled = shuffledKeepingCurrent(items, 0, Random(7))
        assertTrue("A full queue shuffle must change the order", items != shuffled)
    }

    @Test fun `trivial queues are returned unchanged`() {
        assertTrue(shuffledKeepingCurrent(emptyList<String>(), 0, Random(1)).isEmpty())
        assertEquals(listOf("only"), shuffledKeepingCurrent(listOf("only"), 0, Random(1)))
    }

    @Test fun `no item repeats before the whole selection is traversed`() {
        val shuffled = shuffledKeepingCurrent(items, 5, Random(3))
        assertEquals(items.size, shuffled.distinct().size)
    }

    @Test fun `duplicate songs remain separate occurrences through deletion and addition`() {
        val prior = listOf("copy-b", "other", "copy-a")
        assertEquals(listOf("copy-b", "other", "new"),
            reconcileShuffleOrder(prior, listOf("copy-b", "other", "new")))
    }

    @Test fun `play next groups are inserted after current and append groups stay at end`() {
        assertEquals(listOf("current", "next1", "next2", "old", "tail"),
            reconcileShuffleOrder(listOf("current", "old"),
                listOf("old", "current", "next1", "next2", "tail"),
                mapOf("next1" to "current", "next2" to "current")))
    }

    @Test fun `ten thousand occurrences preserve permutation under batched edits`() {
        val canonical = (0 until 10_000).map { "item-$it" }
        val initial = shuffledKeepingCurrent(canonical, 7342, Random(99))
        assertEquals(canonical[7342], initial.first())
        val remaining = canonical.filterIndexed { index, _ -> index % 7 != 0 } + listOf("new-a", "new-b")
        val result = reconcileShuffleOrder(initial, remaining)
        assertEquals(remaining.size, result.size)
        assertEquals(remaining.toSet(), result.toSet())
        val present = remaining.toHashSet()
        assertEquals(initial.filter { it in present }, result.dropLast(2))
        assertEquals(listOf("new-a", "new-b"), result.takeLast(2))
    }
}
