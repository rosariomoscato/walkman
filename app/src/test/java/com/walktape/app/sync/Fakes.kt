package com.walktape.app.sync

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.walktape.app.core.db.AlbumEntity
import com.walktape.app.core.db.ArtistEntity
import com.walktape.app.core.db.BrowseDao
import com.walktape.app.core.db.BrowseResult
import com.walktape.app.core.db.DownloadDao
import com.walktape.app.core.db.DownloadGroupEntity
import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.db.DownloadOriginEntity
import com.walktape.app.core.db.GenreEntity
import com.walktape.app.core.db.LocalAudioEntity
import com.walktape.app.core.db.PendingOpEntity
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import com.walktape.app.core.db.ProfileSettingsEntity
import com.walktape.app.core.db.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory DownloadDao for JVM tests of the W08 queue and download state. */
class FakeDownloadDao : DownloadDao {
    val rows = MutableStateFlow<Map<Pair<Long, String>, DownloadEntity>>(emptyMap())
    val origins = MutableStateFlow<List<DownloadOriginEntity>>(emptyList())
    val ops = MutableStateFlow<List<PendingOpEntity>>(emptyList())
    val settings = MutableStateFlow<Map<Long, ProfileSettingsEntity>>(emptyMap())
    val groupRows = MutableStateFlow<Map<Pair<Long, String>, DownloadGroupEntity>>(emptyMap())
    override suspend fun upsertGroup(group: DownloadGroupEntity) {
        groupRows.value = groupRows.value + ((group.profileId to group.origin) to group)
    }
    override suspend fun group(profileId: Long, origin: String) = groupRows.value[profileId to origin]
    override fun groups(profileId: Long) = groupRows.map { rows -> rows.values.filter { it.profileId == profileId } }
    override suspend fun deleteGroup(profileId: Long, origin: String) {
        groupRows.value = groupRows.value - (profileId to origin)
    }

    override suspend fun upsertDownload(row: DownloadEntity) {
        rows.value = rows.value + ((row.profileId to row.songId) to row)
    }

    override suspend fun updateExisting(row: DownloadEntity): Int {
        if (download(row.profileId, row.songId) == null) return 0
        upsertDownload(row)
        return 1
    }

    override suspend fun reconcileServerPresence(profileId: Long) = Unit

    override suspend fun partialBytes(profileId: Long, songId: String, bytes: Long, now: Long) {
        val row = download(profileId, songId)?.takeIf { it.status != "COMPLETED" } ?: return
        upsertDownload(row.copy(bytes = bytes, updatedAt = now))
    }

    override suspend fun checkpoint(profileId: Long, songId: String, bytes: Long, total: Long, path: String?, now: Long): Int {
        val row = download(profileId, songId)?.takeIf { it.status == "RUNNING" } ?: return 0
        upsertDownload(row.copy(bytes = bytes, totalBytes = total, relativePath = path, updatedAt = now))
        return 1
    }

    override suspend fun complete(profileId: Long, songId: String, bytes: Long, path: String, now: Long): Int {
        val row = download(profileId, songId)?.takeIf { it.status == "RUNNING" } ?: return 0
        upsertDownload(row.copy(status = "COMPLETED", bytes = bytes, totalBytes = bytes,
            relativePath = path, completedAt = now, updatedAt = now, lastError = null, attempts = 0))
        return 1
    }

    override suspend fun failRunning(profileId: Long, songId: String, error: String, now: Long): Int {
        val row = download(profileId, songId)?.takeIf { it.status == "RUNNING" } ?: return 0
        upsertDownload(row.copy(status = "FAILED", lastError = error, updatedAt = now))
        return 1
    }

    override fun downloads(profileId: Long): Flow<List<DownloadEntity>> =
        rows.map { it.values.filter { r -> r.profileId == profileId } }

    // Android Auto browse windows (W10-T01)
    private fun completed(profileId: Long) = rows.value.values
        .filter { it.profileId == profileId && it.status == "COMPLETED" }
    override suspend fun completedWindow(profileId: Long, limit: Int, offset: Int): List<DownloadEntity> =
        completed(profileId).sortedWith(compareBy({ it.title.lowercase() }, { it.songId }))
            .drop(offset).take(limit)
    override suspend fun completedCount(profileId: Long): Int = completed(profileId).size
    override suspend fun completedAll(profileId: Long): List<DownloadEntity> =
        completed(profileId).sortedWith(compareBy({ it.title.lowercase() }, { it.songId }))
    override suspend fun completedFor(profileId: Long, songIds: List<String>): List<DownloadEntity> =
        completed(profileId).filter { it.songId in songIds }
    override suspend fun groupsOnce(profileId: Long): List<DownloadGroupEntity> =
        groupRows.value.values.filter { it.profileId == profileId }
            .sortedWith(compareBy({ it.name }, { it.origin }))

    override suspend fun downloadsOnce(profileId: Long): List<DownloadEntity> =
        rows.value.values.filter { it.profileId == profileId }.sortedWith(compareBy({ it.createdAt }, { it.songId }))

    override fun completedDownloads(profileId: Long): Flow<List<DownloadEntity>> =
        rows.map { it.values.filter { r -> r.profileId == profileId } }

    override fun observeInactiveCount(profileId: Long): Flow<Int> =
        rows.map { it.values.count { r -> r.profileId == profileId } }

    override suspend fun download(profileId: Long, songId: String): DownloadEntity? =
        rows.value[profileId to songId]

    override fun observeDownload(profileId: Long, songId: String): Flow<DownloadEntity?> =
        rows.map { it[profileId to songId] }

    override suspend fun activeCount(profileId: Long): Int = rows.value.values.count {
        it.profileId == profileId && (it.status == "QUEUED" || it.status == "RUNNING")
    }

    override fun observeActiveCount(profileId: Long): Flow<Int> =
        rows.map { map -> map.values.count { it.profileId == profileId && (it.status == "QUEUED" || it.status == "RUNNING") } }

    override fun observeFailedCount(profileId: Long): Flow<Int> =
        rows.map { map -> map.values.count { it.profileId == profileId && it.status == "FAILED" } }

    override suspend fun claimNext(profileId: Long, now: Long): DownloadEntity? {
        resetRunning(profileId, now)
        val next = downloadsOnce(profileId).firstOrNull { it.status == "QUEUED" }
        return next?.let {
            upsertDownload(it.copy(status = "RUNNING", updatedAt = now, lastError = null))
            it.copy(status = "RUNNING", updatedAt = now)
        }
    }

    override suspend fun resetRunning(profileId: Long, now: Long) {
        rows.value.values.filter { it.profileId == profileId && it.status == "RUNNING" }
            .forEach { upsertDownload(it.copy(status = "QUEUED", updatedAt = now)) }
    }

    override suspend fun nextQueued(profileId: Long): DownloadEntity? =
        downloadsOnce(profileId).firstOrNull { it.status == "QUEUED" }

    override suspend fun deleteDownload(profileId: Long, songId: String) {
        // mimic the Room FK cascade: origins die with their download row
        origins.value = origins.value.filterNot { it.profileId == profileId && it.songId == songId }
        rows.value = rows.value - (profileId to songId)
    }

    override suspend fun deleteDownloads(profileId: Long) {
        origins.value = origins.value.filterNot { it.profileId == profileId }
        rows.value = rows.value.filterKeys { it.first != profileId }
    }

    override suspend fun insertOrigin(origin: DownloadOriginEntity) {
        if (origins.value.none { it.profileId == origin.profileId && it.songId == origin.songId &&
                it.origin == origin.origin }) origins.value = origins.value + origin
    }

    override suspend fun insertOrigins(list: List<DownloadOriginEntity>) = list.forEach { insertOrigin(it) }

    override suspend fun origins(profileId: Long, songId: String): List<DownloadOriginEntity> =
        origins.value.filter { it.profileId == profileId && it.songId == songId }

    override suspend fun originsFor(profileId: Long, origin: String): List<DownloadOriginEntity> =
        origins.value.filter { it.profileId == profileId && it.origin == origin }

    override suspend fun allOrigins(profileId: Long): List<DownloadOriginEntity> =
        origins.value.filter { it.profileId == profileId }

    override fun observeProblemCount(profileId: Long): Flow<Int> = ops.map { list ->
        list.count { it.profileId == profileId && (it.state == "CONFLICT" || it.state == "FAILED") }
    }

    override fun observePausedCount(profileId: Long): Flow<Int> =
        rows.map { map -> map.values.count { it.profileId == profileId && it.status == "PAUSED" } }

    override suspend fun downloadedPlaylistOrigins(profileId: Long): List<String> =
        (origins.value.filter { it.profileId == profileId && it.origin.startsWith("playlist:") }.map { it.origin } +
            groupRows.value.values.filter { it.profileId == profileId && it.origin.startsWith("playlist:") }.map { it.origin }).distinct()

    override suspend fun deleteOrigin(profileId: Long, songId: String, origin: String) {
        origins.value = origins.value.filterNot {
            it.profileId == profileId && it.songId == songId && it.origin == origin
        }
    }

    override suspend fun originCount(profileId: Long, songId: String): Int =
        origins.value.count { it.profileId == profileId && it.songId == songId }

    override suspend fun settings(profileId: Long): ProfileSettingsEntity? = settings.value[profileId]

    override fun observeSettings(profileId: Long): Flow<ProfileSettingsEntity?> =
        settings.map { it[profileId] }

    override fun observeAllSettings(): Flow<List<ProfileSettingsEntity>> =
        settings.map { it.values.toList() }

    override suspend fun upsertSettings(entity: ProfileSettingsEntity) {
        settings.value = settings.value + (entity.profileId to entity)
    }

    override suspend fun upsertOp(op: PendingOpEntity) {
        ops.value = ops.value.filterNot { it.profileId == op.profileId && it.opId == op.opId } + op
    }

    override fun ops(profileId: Long): Flow<List<PendingOpEntity>> =
        ops.map { list -> list.filter { it.profileId == profileId } }

    override suspend fun opsOnce(profileId: Long): List<PendingOpEntity> =
        ops.value.filter { it.profileId == profileId }.sortedWith(compareBy({ it.createdAt }, { it.opId }))

    override suspend fun op(profileId: Long, opId: String): PendingOpEntity? =
        ops.value.firstOrNull { it.profileId == profileId && it.opId == opId }

    override suspend fun pendingCount(profileId: Long): Int = ops.value.count {
        it.profileId == profileId && (it.state == "PENDING" || it.state == "SYNCING")
    }

    override fun observeUnsyncedCount(profileId: Long): Flow<Int> = ops.map { list ->
        list.count { it.profileId == profileId && it.state in listOf("PENDING", "SYNCING", "CONFLICT", "FAILED") }
    }

    override suspend fun deleteOp(profileId: Long, opId: String) {
        ops.value = ops.value.filterNot { it.profileId == profileId && it.opId == opId }
    }

    override suspend fun deleteOps(profileId: Long) {
        ops.value = ops.value.filterNot { it.profileId == profileId }
    }
}

/** In-memory BrowseDao: playlist/entry/local-audio are real, paging queries are stubs. */
class FakeBrowseDao : BrowseDao {
    val playlists = MutableStateFlow<Map<Pair<Long, String>, PlaylistEntity>>(emptyMap())
    val entries = MutableStateFlow<Map<Triple<Long, String, Int>, PlaylistEntryEntity>>(emptyMap())
    val localAudio = MutableStateFlow<Map<Pair<Long, String>, LocalAudioEntity>>(emptyMap())

    override suspend fun insertPlaylist(playlist: PlaylistEntity) {
        playlists.value = playlists.value + ((playlist.profileId to playlist.id) to playlist)
    }

    override suspend fun insertEntries(list: List<PlaylistEntryEntity>) {
        list.forEach { entries.value = entries.value + (Triple(it.profileId, it.playlistId, it.position) to it) }
    }

    override suspend fun deleteEntries(profileId: Long, id: String) {
        entries.value = entries.value.filterKeys { it.first != profileId || it.second != id }
    }

    override suspend fun replacePlaylist(playlist: PlaylistEntity, list: List<PlaylistEntryEntity>) {
        deleteEntries(playlist.profileId, playlist.id)
        insertPlaylist(playlist)
        insertEntries(list)
    }

    override suspend fun prunePlaylists(profileId: Long, keep: List<String>) {
        playlists.value = playlists.value.filterKeys { it.first != profileId || it.second in keep }
    }

    override fun playlist(profileId: Long, id: String): Flow<PlaylistEntity?> =
        playlists.map { it[profileId to id] }

    override suspend fun playlistOnce(profileId: Long, id: String): PlaylistEntity? =
        playlists.value[profileId to id]

    override suspend fun pendingPlaylistsOnce(profileId: Long): List<PlaylistEntity> =
        playlists.value.values.filter { it.profileId == profileId && (it.pendingSync || it.pendingConflict) }

    override fun editablePlaylists(profileId: Long, owner: String): Flow<List<PlaylistEntity>> =
        playlists.map { list -> list.values.filter { it.profileId == profileId && it.owner == owner && !it.readOnly } }

    override fun entries(profileId: Long, id: String): PagingSource<Int, PlaylistEntryEntity> =
        throw UnsupportedOperationException()

    override suspend fun queueEntries(profileId: Long, id: String): List<PlaylistEntryEntity> =
        entries.value.values.filter { it.profileId == profileId && it.playlistId == id }
            .sortedBy { it.position }
    override suspend fun queueEntriesWindow(profileId: Long, id: String, limit: Int, offset: Int): List<PlaylistEntryEntity> =
        queueEntries(profileId, id).drop(offset).take(limit)

    override fun albums(query: SupportSQLiteQuery): PagingSource<Int, AlbumEntity> =
        throw UnsupportedOperationException()
    override fun artists(query: SupportSQLiteQuery): PagingSource<Int, ArtistEntity> =
        throw UnsupportedOperationException()
    override fun songs(query: SupportSQLiteQuery): PagingSource<Int, SongEntity> =
        throw UnsupportedOperationException()
    override fun genres(query: SupportSQLiteQuery): PagingSource<Int, GenreEntity> =
        throw UnsupportedOperationException()
    override fun playlists(query: SupportSQLiteQuery): PagingSource<Int, PlaylistEntity> =
        throw UnsupportedOperationException()
    override fun search(query: SupportSQLiteQuery): PagingSource<Int, BrowseResult> =
        throw UnsupportedOperationException()

    // Android Auto browse/search (W10-T01)
    override suspend fun playlistsWindow(profileId: Long, limit: Int, offset: Int): List<PlaylistEntity> =
        playlists.value.values.filter { it.profileId == profileId }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.id })).drop(offset).take(limit)
    override suspend fun searchWindow(query: SupportSQLiteQuery): List<BrowseResult> =
        throw UnsupportedOperationException()
    override fun playlistFingerprint(profileId: Long): Flow<String> =
        playlists.map { list -> "${list.values.count { it.profileId == profileId }}:${list.values.filter { it.profileId == profileId }.maxOfOrNull { it.fetchedAt } ?: 0}" }

    override fun recentAlbums(profileId: Long): Flow<List<AlbumEntity>> = MutableStateFlow(emptyList())
    override fun playedAlbums(profileId: Long): Flow<List<AlbumEntity>> = MutableStateFlow(emptyList())
    override fun favoriteAlbums(profileId: Long): Flow<List<AlbumEntity>> = MutableStateFlow(emptyList())
    override fun favoriteSongs(profileId: Long): Flow<List<SongEntity>> = MutableStateFlow(emptyList())
    override fun favoriteArtists(profileId: Long): Flow<List<ArtistEntity>> = MutableStateFlow(emptyList())
    override fun localSongs(profileId: Long): Flow<List<SongEntity>> = MutableStateFlow(emptyList())
    override fun homePlaylists(profileId: Long): Flow<List<PlaylistEntity>> = MutableStateFlow(emptyList())
    override fun genreNames(profileId: Long): Flow<List<String>> = MutableStateFlow(emptyList())
    override fun years(profileId: Long): Flow<List<Int>> = MutableStateFlow(emptyList())
    override suspend fun playlistCount(profileId: Long): Int =
        playlists.value.values.count { it.profileId == profileId }

    override suspend fun entryCount(profileId: Long, id: String): Int =
        entries.value.values.count { it.profileId == profileId && it.playlistId == id }

    override suspend fun deletePlaylists(profileId: Long) {
        playlists.value = playlists.value.filterKeys { it.first != profileId }
        entries.value = entries.value.filterKeys { it.first != profileId }
    }

    override suspend fun deletePlaylist(profileId: Long, id: String) {
        playlists.value = playlists.value.filterKeys { it.first != profileId || it.second != id }
        deleteEntries(profileId, id)
    }

    override suspend fun updatePlaylistFlags(profileId: Long, id: String, pendingSync: Boolean, conflict: Boolean) {
        playlists.value[profileId to id]?.let {
            playlists.value = playlists.value + ((profileId to id) to it.copy(pendingSync = pendingSync, pendingConflict = conflict))
        }
    }

    override fun conflictedPlaylists(profileId: Long): Flow<List<PlaylistEntity>> =
        playlists.map { list -> list.values.filter { it.profileId == profileId && it.pendingConflict } }

    override fun observePendingPlaylistCount(profileId: Long): Flow<Int> =
        playlists.map { list -> list.values.count { it.profileId == profileId && (it.pendingSync || it.pendingConflict) } }

    override suspend fun deleteLocalAudio(profileId: Long) {
        localAudio.value = localAudio.value.filterKeys { it.first != profileId }
    }

    override suspend fun deleteLocalAudioSong(profileId: Long, songId: String) {
        localAudio.value = localAudio.value - (profileId to songId)
    }

    override suspend fun localAudio(profileId: Long, songId: String): LocalAudioEntity? =
        localAudio.value[profileId to songId]

    override suspend fun insertLocalAudio(audio: LocalAudioEntity) {
        localAudio.value = localAudio.value + ((audio.profileId to audio.songId) to audio)
    }
}
