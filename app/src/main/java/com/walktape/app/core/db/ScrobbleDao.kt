package com.walktape.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrobbleDao {
    @Query("SELECT * FROM listening_state WHERE profileId=:profileId")
    suspend fun listeningState(profileId: Long): ListeningStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putListeningState(state: ListeningStateEntity)

    @Query("SELECT * FROM overlap_listening_state WHERE profileId=:profileId")
    suspend fun overlapState(profileId: Long): OverlapListeningStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOverlapState(state: OverlapListeningStateEntity)

    @Query("DELETE FROM overlap_listening_state WHERE profileId=:profileId")
    suspend fun deleteOverlap(profileId: Long)

    @Transaction
    suspend fun recordOverlap(state: ListeningStateEntity, event: ScrobbleEventEntity?) {
        putOverlapState(state.asOverlap())
        if (event != null) {
            insert(event)
            markAlbumPlayed(event.profileId, event.songId, java.time.Instant.ofEpochMilli(event.playedAtMs).toString())
        }
    }

    @Transaction
    suspend fun promoteOverlap(state: ListeningStateEntity, outgoingSnapshot: String?) {
        putListeningState(state)
        putOverlapState(state.asOverlap(promoted = true, outgoingSnapshot = outgoingSnapshot))
    }

    @Transaction
    suspend fun record(state: ListeningStateEntity, event: ScrobbleEventEntity?) {
        putListeningState(state)
        val receipt = overlapState(state.profileId)
        if (receipt?.promoted == true && receipt.mediaId == state.mediaId)
            putOverlapState(state.asOverlap(promoted = true, outgoingSnapshot = receipt.outgoingSnapshot))
        if (event != null) {
            insert(event)
            markAlbumPlayed(event.profileId, event.songId, java.time.Instant.ofEpochMilli(event.playedAtMs).toString())
        }
    }

    @Query("UPDATE album SET played=:timestamp WHERE profileId=:profileId AND id IN (SELECT albumId FROM song WHERE profileId=:profileId AND id=:songId)")
    suspend fun markAlbumPlayed(profileId: Long, songId: String, timestamp: String)

    @Query("SELECT * FROM scrobble_event WHERE profileId=:profileId ORDER BY playedAtMs DESC LIMIT 30")
    fun history(profileId: Long): Flow<List<ScrobbleEventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ScrobbleEventEntity)

    @Query("SELECT * FROM scrobble_event WHERE profileId=:profileId AND sentAt IS NULL ORDER BY createdAt LIMIT :limit")
    suspend fun pending(profileId: Long, limit: Int): List<ScrobbleEventEntity>

    @Query("SELECT profileId FROM scrobble_event WHERE sentAt IS NULL GROUP BY profileId")
    suspend fun profilesWithPending(): List<Long>

    @Query("UPDATE scrobble_event SET sentAt=:at WHERE profileId=:profileId AND eventId IN (:eventIds)")
    suspend fun markSent(profileId: Long, eventIds: List<String>, at: Long)

    @Query("SELECT COUNT(*) FROM scrobble_event WHERE profileId=:profileId AND sentAt IS NULL")
    fun pendingCount(profileId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM scrobble_event WHERE profileId=:profileId")
    suspend fun count(profileId: Long): Int

    /** Delivered rows are history; keep only the most recent `keep` per profile. */
    @Query("DELETE FROM scrobble_event WHERE profileId=:profileId AND sentAt IS NOT NULL AND eventId NOT IN (SELECT eventId FROM scrobble_event WHERE profileId=:profileId AND sentAt IS NOT NULL ORDER BY sentAt DESC LIMIT :keep)")
    suspend fun pruneSent(profileId: Long, keep: Int)
}
