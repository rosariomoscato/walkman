package com.walktape.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.walktape.app.playback.SavedQueue
import com.walktape.app.playback.SavedQueueItem

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_state WHERE profileId=:profileId")
    suspend fun state(profileId: Long): QueueStateEntity?

    @Query("SELECT * FROM queue_entry WHERE profileId=:profileId ORDER BY position")
    suspend fun entries(profileId: Long): List<QueueEntryEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM profile WHERE id=:profileId)")
    suspend fun profileExists(profileId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: QueueStateEntity)

    @Insert
    suspend fun putEntries(entries: List<QueueEntryEntity>)

    @Update
    suspend fun updateState(state: QueueStateEntity): Int

    @Query("DELETE FROM queue_entry WHERE profileId=:profileId")
    suspend fun deleteEntries(profileId: Long)

    @Transaction
    suspend fun replace(queue: SavedQueue): Boolean {
        require(queue.isValid())
        if (!profileExists(queue.profileId)) return false
        val ranks = queue.traversal.withIndex().associate { it.value to it.index }
        deleteEntries(queue.profileId)
        putState(queue.toState())
        putEntries(queue.items.mapIndexed { index, item -> QueueEntryEntity(queue.profileId, index,
            ranks.getValue(item.entryId), item.entryId, item.songId, item.title, item.artist, item.album, item.durationMs, item.originName) })
        return true
    }

    @Transaction
    suspend fun load(profileId: Long): SavedQueue? {
        val state = state(profileId) ?: return null
        val rows = entries(profileId)
        require(rows.map { it.position } == rows.indices.toList())
        require(rows.map { it.shufflePosition }.sorted() == rows.indices.toList())
        val queue = SavedQueue(profileId, rows.map { SavedQueueItem(it.entryId, it.songId, it.title,
            it.artist, it.album, it.durationMs, it.originName) }, rows.sortedBy { it.shufflePosition }.map { it.entryId },
            state.currentId, state.positionMs, state.repeatMode, state.shuffle, state.updatedAt)
        require(queue.isValid())
        return queue
    }
}

fun SavedQueue.toState() = QueueStateEntity(profileId, currentId, positionMs, repeatMode, shuffle, updatedAt)
