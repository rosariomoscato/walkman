package com.walktape.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    // --- downloads ---

    @Upsert
    suspend fun upsertDownload(row: DownloadEntity)

    @Update
    suspend fun updateExisting(row: DownloadEntity): Int

    @Query("UPDATE download SET serverMissing=CASE WHEN EXISTS (SELECT 1 FROM song WHERE song.profileId=download.profileId AND song.id=download.songId) THEN 0 ELSE 1 END WHERE profileId=:profileId AND status='COMPLETED'")
    suspend fun reconcileServerPresence(profileId: Long)

    @Query("UPDATE download SET bytes=:bytes,totalBytes=:total,relativePath=:path,updatedAt=:now WHERE profileId=:profileId AND songId=:songId AND status='RUNNING'")
    suspend fun checkpoint(profileId: Long, songId: String, bytes: Long, total: Long, path: String, now: Long): Int

    @Query("UPDATE download SET status='COMPLETED',bytes=:bytes,totalBytes=:bytes,relativePath=:path,completedAt=:now,updatedAt=:now,lastError=NULL,attempts=0 WHERE profileId=:profileId AND songId=:songId AND status='RUNNING'")
    suspend fun complete(profileId: Long, songId: String, bytes: Long, path: String, now: Long): Int

    @Query("UPDATE download SET status='FAILED',lastError=:error,updatedAt=:now WHERE profileId=:profileId AND songId=:songId AND status='RUNNING'")
    suspend fun failRunning(profileId: Long, songId: String, error: String, now: Long): Int

    @Query("UPDATE download SET bytes=:bytes,updatedAt=:now WHERE profileId=:profileId AND songId=:songId AND status!='COMPLETED'")
    suspend fun partialBytes(profileId: Long, songId: String, bytes: Long, now: Long)

    @Query("SELECT * FROM download WHERE profileId=:profileId ORDER BY createdAt, songId")
    fun downloads(profileId: Long): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE profileId=:profileId ORDER BY createdAt, songId")
    suspend fun downloadsOnce(profileId: Long): List<DownloadEntity>

    @Query("SELECT * FROM download WHERE profileId=:profileId AND status='COMPLETED' ORDER BY createdAt DESC, songId LIMIT 8")
    fun completedDownloads(profileId: Long): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM download WHERE profileId=:profileId AND status IN ('QUEUED','RUNNING','PAUSED','FAILED')")
    fun observeInactiveCount(profileId: Long): Flow<Int>

    @Query("SELECT * FROM download WHERE profileId=:profileId AND songId=:songId")
    suspend fun download(profileId: Long, songId: String): DownloadEntity?

    @Query("SELECT * FROM download WHERE profileId=:profileId AND songId=:songId")
    fun observeDownload(profileId: Long, songId: String): Flow<DownloadEntity?>

    @Query("SELECT COUNT(*) FROM download WHERE profileId=:profileId AND status IN ('QUEUED','RUNNING')")
    suspend fun activeCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM download WHERE profileId=:profileId AND status IN ('QUEUED','RUNNING')")
    fun observeActiveCount(profileId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM download WHERE profileId=:profileId AND status='FAILED'")
    fun observeFailedCount(profileId: Long): Flow<Int>

    /**
     * Claims the next queued download and recovers RUNNING rows orphaned by process
     * death: only one worker per profile runs at a time (unique work), so the reset
     * is safe. Returns the claimed row or null when nothing is queued.
     */
    @Transaction
    suspend fun claimNext(profileId: Long, now: Long): DownloadEntity? {
        resetRunning(profileId, now)
        val row = nextQueued(profileId)
        return row?.let {
            upsertDownload(it.copy(status = DownloadStatus.RUNNING.name, updatedAt = now, lastError = null))
            it.copy(status = DownloadStatus.RUNNING.name, updatedAt = now)
        }
    }

    @Query("UPDATE download SET status='QUEUED', updatedAt=:now WHERE profileId=:profileId AND status='RUNNING'")
    suspend fun resetRunning(profileId: Long, now: Long)

    @Query("SELECT * FROM download WHERE profileId=:profileId AND status='QUEUED' ORDER BY createdAt, songId LIMIT 1")
    suspend fun nextQueued(profileId: Long): DownloadEntity?

    @Query("DELETE FROM download WHERE profileId=:profileId AND songId=:songId")
    suspend fun deleteDownload(profileId: Long, songId: String)

    @Query("DELETE FROM download WHERE profileId=:profileId")
    suspend fun deleteDownloads(profileId: Long)

    // --- origins ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrigin(origin: DownloadOriginEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrigins(origins: List<DownloadOriginEntity>)

    @Query("SELECT * FROM download_origin WHERE profileId=:profileId AND songId=:songId")
    suspend fun origins(profileId: Long, songId: String): List<DownloadOriginEntity>

    @Query("SELECT * FROM download_origin WHERE profileId=:profileId AND origin=:origin")
    suspend fun originsFor(profileId: Long, origin: String): List<DownloadOriginEntity>

    @Query("SELECT origin FROM download_group WHERE profileId=:profileId AND origin LIKE 'playlist:%' UNION SELECT origin FROM download_origin WHERE profileId=:profileId AND origin LIKE 'playlist:%'")
    suspend fun downloadedPlaylistOrigins(profileId: Long): List<String>

    @Upsert
    suspend fun upsertGroup(group: DownloadGroupEntity)
    @Query("SELECT * FROM download_group WHERE profileId=:profileId AND origin=:origin")
    suspend fun group(profileId: Long, origin: String): DownloadGroupEntity?
    @Query("SELECT * FROM download_group WHERE profileId=:profileId ORDER BY name,origin")
    fun groups(profileId: Long): Flow<List<DownloadGroupEntity>>
    @Query("DELETE FROM download_group WHERE profileId=:profileId AND origin=:origin")
    suspend fun deleteGroup(profileId: Long, origin: String)

    @Query("DELETE FROM download_origin WHERE profileId=:profileId AND songId=:songId AND origin=:origin")
    suspend fun deleteOrigin(profileId: Long, songId: String, origin: String)

    @Query("SELECT COUNT(*) FROM download_origin WHERE profileId=:profileId AND songId=:songId")
    suspend fun originCount(profileId: Long, songId: String): Int

    // --- profile settings ---

    @Query("SELECT * FROM profile_settings WHERE profileId=:profileId")
    suspend fun settings(profileId: Long): ProfileSettingsEntity?

    @Query("SELECT * FROM profile_settings WHERE profileId=:profileId")
    fun observeSettings(profileId: Long): Flow<ProfileSettingsEntity?>

    @Query("SELECT * FROM profile_settings")
    fun observeAllSettings(): Flow<List<ProfileSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: ProfileSettingsEntity)

    // --- pending operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOp(op: PendingOpEntity)

    @Query("SELECT * FROM pending_op WHERE profileId=:profileId ORDER BY createdAt, opId")
    fun ops(profileId: Long): Flow<List<PendingOpEntity>>

    @Query("SELECT * FROM pending_op WHERE profileId=:profileId ORDER BY createdAt, opId")
    suspend fun opsOnce(profileId: Long): List<PendingOpEntity>

    @Query("SELECT * FROM pending_op WHERE profileId=:profileId AND opId=:opId")
    suspend fun op(profileId: Long, opId: String): PendingOpEntity?

    @Query("SELECT COUNT(*) FROM pending_op WHERE profileId=:profileId AND state IN ('PENDING','SYNCING')")
    suspend fun pendingCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM pending_op WHERE profileId=:profileId AND state IN ('PENDING','SYNCING','CONFLICT','FAILED')")
    fun observeUnsyncedCount(profileId: Long): Flow<Int>

    @Query("DELETE FROM pending_op WHERE profileId=:profileId AND opId=:opId")
    suspend fun deleteOp(profileId: Long, opId: String)

    @Query("DELETE FROM pending_op WHERE profileId=:profileId")
    suspend fun deleteOps(profileId: Long)
}
