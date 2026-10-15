package com.walktape.app.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "queue_state", foreignKeys = [ForeignKey(entity = ProfileEntity::class,
    parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)])
data class QueueStateEntity(
    @PrimaryKey val profileId: Long,
    val currentId: String?,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val mixtapeName: String? = null,
)

@Entity(tableName = "queue_entry", primaryKeys = ["profileId", "position"],
    indices = [Index(value = ["profileId", "entryId"], unique = true)],
    foreignKeys = [ForeignKey(entity = QueueStateEntity::class, parentColumns = ["profileId"],
        childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)])
data class QueueEntryEntity(
    val profileId: Long,
    val position: Int,
    val shufflePosition: Int,
    val entryId: String,
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val originName: String? = null,
)
