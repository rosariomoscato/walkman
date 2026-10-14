package com.walktape.app.playback

import org.junit.Assert.*
import org.junit.Test

class SavedQueueTest {
    private val queue = SavedQueue(1, listOf(
        SavedQueueItem("queue/first", "same-song", "Title", "Artist", "Album", 90_000),
        SavedQueueItem("queue/second", "same-song", "Title", "Artist", "Album", 90_000)),
        listOf("queue/second", "queue/first"), "queue/second", 12_345, 2, true, 100)

    @Test fun duplicateSongsKeepExactCurrentOccurrence() {
        assertTrue(queue.isValid())
        assertEquals(2, queue.items.size)
        assertEquals("queue/second", queue.currentId)
        assertEquals(12_345L, queue.positionMs)
    }

    @Test fun missingOrDuplicateOccurrencesAndWrongTraversalAreRejected() {
        assertFalse(queue.copy(currentId = "queue/missing").isValid())
        assertFalse(queue.copy(items = listOf(queue.items[0], queue.items[0])).isValid())
        assertFalse(queue.copy(traversal = listOf("queue/first", "queue/first")).isValid())
        assertFalse(queue.copy(traversal = listOf("queue/second")).isValid())
    }

    @Test fun invalidPositionsProfilesDurationsAndRepeatModesAreRejected() {
        assertFalse(queue.copy(profileId = 0).isValid())
        assertFalse(queue.copy(positionMs = -1).isValid())
        assertFalse(queue.copy(repeatMode = 3).isValid())
        assertFalse(queue.copy(items = queue.items.map { it.copy(durationMs = 0) }).isValid())
    }

    @Test fun emptyQueueIsExplicitAndCannotCarryAnotherProfilesCurrentItem() {
        val empty = SavedQueue(2, emptyList(), emptyList(), null, 0, 0, false, 10)
        assertTrue(empty.isValid())
        assertFalse(empty.copy(currentId = queue.currentId).isValid())
        assertFalse(empty.copy(positionMs = 100).isValid())
        assertFalse(empty.copy(shuffle = true).isValid())
    }

    @Test fun unknownDurationDoesNotDiscardAPersistedPosition() {
        assertTrue(queue.copy(items = queue.items.map { it.copy(durationMs = null) }).isValid())
    }
}
