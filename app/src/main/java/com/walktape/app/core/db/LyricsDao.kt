package com.walktape.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LyricsEntity)

    @Query("SELECT * FROM lyrics WHERE profileId=:profileId AND songId=:songId")
    suspend fun get(profileId: Long, songId: String): LyricsEntity?

    @Query("DELETE FROM lyrics WHERE profileId=:profileId AND songId=:songId")
    suspend fun delete(profileId: Long, songId: String)

    @Query("SELECT COUNT(*) FROM lyrics WHERE profileId=:profileId")
    suspend fun count(profileId: Long): Int
}
