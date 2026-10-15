package com.walktape.app.core.db

import androidx.room.Entity
import androidx.room.ForeignKey

/** Server lyrics cached per song; `lines` is a JSON array of {value,startMs,endMs}. */
@Entity(tableName = "lyrics", primaryKeys = ["profileId", "songId"],
    foreignKeys = [ForeignKey(entity = SongEntity::class, parentColumns = ["profileId", "id"],
        childColumns = ["profileId", "songId"], onDelete = ForeignKey.CASCADE)])
data class LyricsEntity(
    val profileId: Long,
    val songId: String,
    val displayArtist: String?,
    val displayTitle: String?,
    val lang: String?,
    val synced: Boolean,
    val lines: String,
    val fetchedAt: Long,
)
