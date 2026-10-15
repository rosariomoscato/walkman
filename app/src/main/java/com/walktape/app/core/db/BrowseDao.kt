package com.walktape.app.core.db

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)
    @Query("DELETE FROM playlist_entry WHERE profileId=:profileId AND playlistId=:id")
    suspend fun deleteEntries(profileId: Long, id: String)
    @Transaction
    suspend fun replacePlaylist(playlist: PlaylistEntity, entries: List<PlaylistEntryEntity>) {
        deleteEntries(playlist.profileId, playlist.id)
        insertPlaylist(playlist)
        insertEntries(entries)
    }
    @Query("DELETE FROM playlist WHERE profileId=:profileId AND id NOT IN (:keep)")
    suspend fun prunePlaylists(profileId: Long, keep: List<String>)
    @Query("SELECT * FROM playlist WHERE profileId=:profileId AND id=:id")
    fun playlist(profileId: Long, id: String): Flow<PlaylistEntity?>
    @Query("SELECT * FROM playlist_entry WHERE profileId=:profileId AND playlistId=:id ORDER BY position")
    fun entries(profileId: Long, id: String): PagingSource<Int, PlaylistEntryEntity>
    @Query("SELECT * FROM playlist_entry WHERE profileId=:profileId AND playlistId=:id ORDER BY position")
    suspend fun queueEntries(profileId: Long, id: String): List<PlaylistEntryEntity>

    @RawQuery(observedEntities = [AlbumEntity::class, SongEntity::class, LocalAudioEntity::class])
    fun albums(query: SupportSQLiteQuery): PagingSource<Int, AlbumEntity>
    @RawQuery(observedEntities = [ArtistEntity::class, AlbumEntity::class, SongEntity::class, LocalAudioEntity::class])
    fun artists(query: SupportSQLiteQuery): PagingSource<Int, ArtistEntity>
    @RawQuery(observedEntities = [SongEntity::class, LocalAudioEntity::class])
    fun songs(query: SupportSQLiteQuery): PagingSource<Int, SongEntity>
    @RawQuery(observedEntities = [GenreEntity::class, SongEntity::class, LocalAudioEntity::class])
    fun genres(query: SupportSQLiteQuery): PagingSource<Int, GenreEntity>
    @RawQuery(observedEntities = [PlaylistEntity::class, PlaylistEntryEntity::class, LocalAudioEntity::class])
    fun playlists(query: SupportSQLiteQuery): PagingSource<Int, PlaylistEntity>
    @RawQuery(observedEntities = [ArtistEntity::class, AlbumEntity::class, SongEntity::class,
        GenreEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class, LocalAudioEntity::class])
    fun search(query: SupportSQLiteQuery): PagingSource<Int, BrowseResult>

    @Query("SELECT * FROM album WHERE profileId=:profileId ORDER BY created DESC, id LIMIT 8")
    fun recentAlbums(profileId: Long): Flow<List<AlbumEntity>>
    @Query("SELECT * FROM album WHERE profileId=:profileId AND played IS NOT NULL AND played != '' ORDER BY played DESC, id LIMIT 8")
    fun playedAlbums(profileId: Long): Flow<List<AlbumEntity>>
    @Query("SELECT * FROM album WHERE profileId=:profileId AND starred IS NOT NULL AND starred != '' ORDER BY starred DESC, id LIMIT 8")
    fun favoriteAlbums(profileId: Long): Flow<List<AlbumEntity>>
    @Query("SELECT * FROM song WHERE profileId=:profileId AND starred IS NOT NULL AND starred != '' ORDER BY starred DESC, id LIMIT 8")
    fun favoriteSongs(profileId: Long): Flow<List<SongEntity>>
    @Query("SELECT * FROM artist WHERE profileId=:profileId AND starred IS NOT NULL AND starred != '' ORDER BY starred DESC, id LIMIT 8")
    fun favoriteArtists(profileId: Long): Flow<List<ArtistEntity>>
    @Query("SELECT s.* FROM song s JOIN local_audio l ON l.profileId=s.profileId AND l.songId=s.id WHERE s.profileId=:profileId AND l.bytes>0 ORDER BY s.title LIMIT 8")
    fun localSongs(profileId: Long): Flow<List<SongEntity>>
    @Query("SELECT * FROM playlist WHERE profileId=:profileId ORDER BY name COLLATE NOCASE, id LIMIT 8")
    fun homePlaylists(profileId: Long): Flow<List<PlaylistEntity>>
    @Query("SELECT name FROM genre WHERE profileId=:profileId ORDER BY name COLLATE NOCASE")
    fun genreNames(profileId: Long): Flow<List<String>>
    @Query("SELECT DISTINCT year FROM song WHERE profileId=:profileId AND year>0 ORDER BY year DESC")
    fun years(profileId: Long): Flow<List<Int>>
    @Query("SELECT COUNT(*) FROM playlist WHERE profileId=:profileId")
    suspend fun playlistCount(profileId: Long): Int
    @Query("SELECT COUNT(*) FROM playlist_entry WHERE profileId=:profileId AND playlistId=:id")
    suspend fun entryCount(profileId: Long, id: String): Int
    @Query("DELETE FROM playlist WHERE profileId=:profileId")
    suspend fun deletePlaylists(profileId: Long)
    @Query("DELETE FROM playlist WHERE profileId=:profileId AND id=:id")
    suspend fun deletePlaylist(profileId: Long, id: String)
    @Query("DELETE FROM local_audio WHERE profileId=:profileId")
    suspend fun deleteLocalAudio(profileId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalAudio(audio: LocalAudioEntity)
}
