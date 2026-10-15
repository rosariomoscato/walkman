package com.walktape.app.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "playlist", primaryKeys = ["profileId", "id"])
data class PlaylistEntity(
    val profileId: Long,
    val id: String,
    val name: String,
    val owner: String?,
    val isPublic: Boolean,
    val comment: String?,
    val coverArt: String?,
    val songCount: Int,
    val duration: Int,
    val created: String?,
    val changed: String?,
    val fetchedAt: Long,
    /** Server-side write permission flag (Navidrome "readonly"); own playlists are always false. */
    @ColumnInfo(defaultValue = "0") val readOnly: Boolean = false,
)

@Entity(tableName = "playlist_entry", primaryKeys = ["profileId", "playlistId", "position"],
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["profileId", "id"],
        childColumns = ["profileId", "playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId", "playlistId")])
data class PlaylistEntryEntity(
    val profileId: Long,
    val playlistId: String,
    val position: Int,
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int,
    val year: Int?,
    val genre: String?,
)

@Entity(tableName = "local_audio", primaryKeys = ["profileId", "songId"])
data class LocalAudioEntity(val profileId: Long, val songId: String, val relativePath: String, val bytes: Long)

data class BrowseResult(val kind: String, val profileId: Long, val id: String, val title: String, val subtitle: String?)
