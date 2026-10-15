package com.walktape.app.core.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Transaction
    suspend fun replaceAlbum(profileId: Long, album: AlbumEntity, songs: List<SongEntity>) {
        deleteSongsInAlbums(profileId, listOf(album.id))
        upsertAlbums(listOf(album))
        upsertSongs(songs)
    }

    @Transaction
    suspend fun replaceArtistsAndGenres(profileId: Long, artists: List<ArtistEntity>, genres: List<GenreEntity>) {
        deleteArtistsFor(profileId)
        deleteGenresFor(profileId)
        upsertArtists(artists)
        upsertGenres(genres)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGenres(genres: List<GenreEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: LibraryStateEntity)

    @Query("SELECT * FROM library_state WHERE profileId = :profileId")
    fun observeState(profileId: Long): Flow<LibraryStateEntity?>

    @Query("SELECT * FROM library_state WHERE profileId = :profileId")
    suspend fun state(profileId: Long): LibraryStateEntity?

    fun albums(profileId: Long, sort: String, query: String): PagingSource<Int, AlbumEntity> = when (sort) {
        "artist" -> albumsByArtist(profileId, query)
        "year" -> albumsByYear(profileId, query)
        "recent" -> albumsByRecent(profileId, query)
        else -> albumsByName(profileId, query)
    }

    @Query("SELECT * FROM album WHERE profileId = :profileId AND (name LIKE :query OR artist LIKE :query) ORDER BY name COLLATE NOCASE")
    fun albumsByName(profileId: Long, query: String): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM album WHERE profileId = :profileId AND (name LIKE :query OR artist LIKE :query) ORDER BY artist COLLATE NOCASE, name COLLATE NOCASE")
    fun albumsByArtist(profileId: Long, query: String): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM album WHERE profileId = :profileId AND (name LIKE :query OR artist LIKE :query) ORDER BY year IS NULL, year, name COLLATE NOCASE")
    fun albumsByYear(profileId: Long, query: String): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM album WHERE profileId = :profileId AND (name LIKE :query OR artist LIKE :query) ORDER BY created IS NULL, created DESC")
    fun albumsByRecent(profileId: Long, query: String): PagingSource<Int, AlbumEntity>

    fun artists(profileId: Long, query: String): PagingSource<Int, ArtistEntity> = artistsByName(profileId, query)

    @Query("SELECT * FROM artist WHERE profileId = :profileId AND name LIKE :query ORDER BY name COLLATE NOCASE")
    fun artistsByName(profileId: Long, query: String): PagingSource<Int, ArtistEntity>

    fun songs(profileId: Long, sort: String, query: String): PagingSource<Int, SongEntity> = when (sort) {
        "artist" -> songsByArtist(profileId, query)
        "album" -> songsByAlbum(profileId, query)
        "duration" -> songsByDuration(profileId, query)
        else -> songsByTitle(profileId, query)
    }

    @Query("SELECT * FROM song WHERE profileId = :profileId AND (title LIKE :query OR artist LIKE :query OR album LIKE :query) ORDER BY title COLLATE NOCASE")
    fun songsByTitle(profileId: Long, query: String): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM song WHERE profileId = :profileId AND (title LIKE :query OR artist LIKE :query OR album LIKE :query) ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, discNumber, track")
    fun songsByArtist(profileId: Long, query: String): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM song WHERE profileId = :profileId AND (title LIKE :query OR artist LIKE :query OR album LIKE :query) ORDER BY album COLLATE NOCASE, discNumber, track")
    fun songsByAlbum(profileId: Long, query: String): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM song WHERE profileId = :profileId AND (title LIKE :query OR artist LIKE :query OR album LIKE :query) ORDER BY duration, title COLLATE NOCASE")
    fun songsByDuration(profileId: Long, query: String): PagingSource<Int, SongEntity>

    fun genres(profileId: Long, query: String): PagingSource<Int, GenreEntity> = genresByName(profileId, query)

    @Query("SELECT * FROM genre WHERE profileId = :profileId AND name LIKE :query ORDER BY name COLLATE NOCASE")
    fun genresByName(profileId: Long, query: String): PagingSource<Int, GenreEntity>

    @Query("SELECT * FROM album WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun album(profileId: Long, id: String): AlbumEntity?

    @Query("SELECT * FROM album WHERE profileId = :profileId AND id = :id")
    fun observeAlbum(profileId: Long, id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM song WHERE profileId = :profileId AND albumId = :albumId ORDER BY discNumber, track")
    fun observeAlbumSongs(profileId: Long, albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM album WHERE profileId = :profileId AND artistId = :artistId ORDER BY year IS NULL, year, name COLLATE NOCASE")
    fun observeArtistAlbums(profileId: Long, artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM song WHERE profileId = :profileId AND albumId IN (:albumIds) ORDER BY albumId, discNumber, track")
    suspend fun songsInAlbums(profileId: Long, albumIds: List<String>): List<SongEntity>

    @Query("SELECT * FROM song WHERE profileId = :profileId AND id IN (:ids)")
    suspend fun songsByIds(profileId: Long, ids: List<String>): List<SongEntity>

    @Query("SELECT * FROM artist WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun artist(profileId: Long, id: String): ArtistEntity?

    @Query("SELECT * FROM artist WHERE profileId = :profileId AND id = :id")
    fun observeArtist(profileId: Long, id: String): Flow<ArtistEntity?>

    /** Targeted starred updates keep optimistic toggles cheap between full syncs. */
    @Query("UPDATE song SET starred=:timestamp WHERE profileId=:profileId AND id=:id")
    suspend fun setSongStarred(profileId: Long, id: String, timestamp: String?): Int

    @Query("UPDATE album SET starred=:timestamp WHERE profileId=:profileId AND id=:id")
    suspend fun setAlbumStarred(profileId: Long, id: String, timestamp: String?): Int

    @Query("UPDATE artist SET starred=:timestamp WHERE profileId=:profileId AND id=:id")
    suspend fun setArtistStarred(profileId: Long, id: String, timestamp: String?): Int

    @Query("SELECT * FROM song WHERE profileId = :profileId AND genre = :genre ORDER BY album COLLATE NOCASE, discNumber, track")
    fun observeGenreSongs(profileId: Long, genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM song WHERE profileId = :profileId AND genre = :genre ORDER BY album COLLATE NOCASE, discNumber, track")
    fun genreSongsPaging(profileId: Long, genre: String): PagingSource<Int, SongEntity>

    @Query("SELECT COUNT(*) FROM song WHERE profileId = :profileId")
    suspend fun songCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM album WHERE profileId = :profileId")
    suspend fun albumCount(profileId: Long): Int

    @Query("DELETE FROM song WHERE profileId = :profileId")
    suspend fun deleteSongsFor(profileId: Long)

    @Query("DELETE FROM album WHERE profileId = :profileId")
    suspend fun deleteAlbumsFor(profileId: Long)

    @Query("DELETE FROM artist WHERE profileId = :profileId")
    suspend fun deleteArtistsFor(profileId: Long)

    @Query("DELETE FROM genre WHERE profileId = :profileId")
    suspend fun deleteGenresFor(profileId: Long)

    @Query("DELETE FROM library_state WHERE profileId = :profileId")
    suspend fun deleteStateFor(profileId: Long)

    @Query("SELECT id FROM album WHERE profileId = :profileId AND id NOT IN (:keep)")
    suspend fun missingAlbumIds(profileId: Long, keep: List<String>): List<String>

    @Query("DELETE FROM song WHERE profileId = :profileId AND albumId IN (:albumIds)")
    suspend fun deleteSongsInAlbums(profileId: Long, albumIds: List<String>)

    @Query("DELETE FROM album WHERE profileId = :profileId AND id IN (:albumIds)")
    suspend fun deleteAlbums(profileId: Long, albumIds: List<String>)
}
