package com.walktape.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    @Query("SELECT * FROM profile ORDER BY name")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Insert
    suspend fun insert(profile: ProfileEntity): Long
}
