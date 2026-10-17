package com.walktape.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    @Query("SELECT * FROM profile ORDER BY name")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile ORDER BY name")
    suspend fun all(): List<ProfileEntity>

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT * FROM profile WHERE isActive = 1 LIMIT 1")
    suspend fun active(): ProfileEntity?

    @Query("UPDATE profile SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE profile SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun delete(id: Long)
}
