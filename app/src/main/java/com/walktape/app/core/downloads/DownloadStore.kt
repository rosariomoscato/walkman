package com.walktape.app.core.downloads

import com.walktape.app.core.db.BrowseDao
import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.db.DownloadError
import com.walktape.app.core.db.DownloadOriginEntity
import com.walktape.app.core.db.DownloadStatus
import com.walktape.app.core.db.LibraryDao
import com.walktape.app.core.db.DownloadDao
import com.walktape.app.core.db.DownloadGroupEntity
import com.google.gson.Gson
import com.walktape.app.core.db.LocalAudioEntity
import com.walktape.app.core.db.SongEntity
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.randomSalt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import javax.net.ssl.SSLException

/**
 * Persistent download state and file transfer for one process; all state lives in Room so
 * progress, pause/resume and cancellation survive process death and reboots. One audio file
 * per (profile, song); origins record why the file is kept without copying audio.
 */
class DownloadStore(
    private val downloadDao: DownloadDao,
    private val libraryDao: LibraryDao,
    private val browseDao: BrowseDao,
    private val profileDirectory: (Long) -> File,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
    private val commitLocal: suspend (suspend () -> Unit) -> Unit = { it() },
) {

    /** Outcomes of a single transfer attempt; the worker maps them to retry/stop decisions. */
    sealed interface Outcome {
        data object Completed : Outcome
        data object Transient : Outcome
        data object Paused : Outcome
        data object Cancelled : Outcome
        data object Space : Outcome
        data class Permanent(val error: DownloadError) : Outcome
    }

    val dao: DownloadDao get() = downloadDao

    fun downloads(profileId: Long) = downloadDao.downloads(profileId)
    suspend fun download(profileId: Long, songId: String) = downloadDao.download(profileId, songId)

    /** Completed local file for playback priority, or null when the song is not downloaded. */
    suspend fun completedFile(profileId: Long, songId: String): File? {
        val row = downloadDao.download(profileId, songId)
            ?.takeIf { it.status == DownloadStatus.COMPLETED.name } ?: return null
        val path = row.relativePath ?: return null
        val root = profileDirectory(profileId).canonicalFile
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile || file.length() <= 0) return null
        return file
    }

    /** Enqueues songs (metadata snapshot from the index); completed files are only re-linked. */
    suspend fun enqueueSongs(profileId: Long, songs: List<SongEntity>, origin: String): Int = withContext(Dispatchers.IO) {
        if (profileId <= 0 || songs.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        var queued = 0
        commitLocal {
        songs.forEach { song ->
            if (song.profileId != profileId) return@forEach
            val existing = downloadDao.download(profileId, song.id)
            when {
                existing == null -> {
                    downloadDao.upsertDownload(newRow(profileId, song, now))
                    queued++
                }
                existing.status == DownloadStatus.COMPLETED.name -> {
                    // file already on disk: only the origin membership changes
                }
                existing.status == DownloadStatus.PAUSED.name || existing.status == DownloadStatus.FAILED.name -> {
                    downloadDao.upsertDownload(existing.copy(
                        status = DownloadStatus.QUEUED.name, lastError = null, updatedAt = now))
                    queued++
                }
                else -> { /* QUEUED or RUNNING: nothing to do */ }
            }
            downloadDao.insertOrigin(DownloadOriginEntity(profileId, song.id, origin, now))
        }
        }
        queued
    }

    /** Distinct song ids in visual order; playlist duplicates never require audio copies. */
    suspend fun enqueuePlaylist(profileId: Long, playlistId: String, resumePaused: Boolean = true): Int = withContext(Dispatchers.IO) {
        val playlist = browseDao.playlistOnce(profileId, playlistId) ?: return@withContext 0
        val entries = browseDao.queueEntries(profileId, playlistId)
        val distinct = entries.distinctBy { it.songId }
        val metadata = distinct.chunked(500)
            .flatMap { libraryDao.songsByIds(profileId, it.map { e -> e.songId }) }
            .associateBy { it.id }
        val now = System.currentTimeMillis()
        var queued = 0
        commitLocal {
        downloadDao.upsertGroup(DownloadGroupEntity(profileId, playlistOrigin(playlistId), playlist.name,
            Gson().toJson(entries.map { it.songId }), now, serverMissing = false))
        distinct.forEach { entry ->
            val song = metadata[entry.songId]
            val existing = downloadDao.download(profileId, entry.songId)
            when {
                existing == null -> {
                    val row = if (song != null) newRow(profileId, song, now) else DownloadEntity(
                        profileId = profileId, songId = entry.songId,
                        status = DownloadStatus.QUEUED.name, relativePath = null, bytes = 0,
                        totalBytes = -1, title = entry.title, artist = entry.artist,
                        album = entry.album, albumId = null, coverArt = null,
                        duration = entry.duration, suffix = null, contentType = null,
                        serverSize = -1, createdAt = now, completedAt = null, updatedAt = now,
                        lastError = null, attempts = 0)
                    downloadDao.upsertDownload(row)
                    queued++
                }
                existing.status == DownloadStatus.COMPLETED.name -> { /* link only */ }
                (resumePaused && existing.status == DownloadStatus.PAUSED.name) || existing.status == DownloadStatus.FAILED.name -> {
                    downloadDao.upsertDownload(existing.copy(
                        status = DownloadStatus.QUEUED.name, lastError = null, updatedAt = now))
                    queued++
                }
                else -> {}
            }
            downloadDao.insertOrigin(DownloadOriginEntity(profileId, entry.songId, playlistOrigin(playlistId), now))
        }
        }
        queued
    }

    suspend fun pause(profileId: Long, songId: String) = withContext(Dispatchers.IO) {
        val row = downloadDao.download(profileId, songId) ?: return@withContext
        if (row.status != DownloadStatus.COMPLETED.name) {
            downloadDao.upsertDownload(row.copy(status = DownloadStatus.PAUSED.name,
                lastError = null, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun pauseAll(profileId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        downloadDao.downloadsOnce(profileId).filter {
            it.status == DownloadStatus.QUEUED.name || it.status == DownloadStatus.RUNNING.name
        }.forEach {
            downloadDao.upsertDownload(it.copy(status = DownloadStatus.PAUSED.name, updatedAt = now))
        }
    }

    suspend fun resume(profileId: Long, songId: String): Boolean = withContext(Dispatchers.IO) {
        val row = downloadDao.download(profileId, songId) ?: return@withContext false
        if (row.status == DownloadStatus.PAUSED.name || row.status == DownloadStatus.FAILED.name) {
            downloadDao.upsertDownload(row.copy(status = DownloadStatus.QUEUED.name,
                lastError = null, attempts = 0, updatedAt = System.currentTimeMillis()))
            true
        } else false
    }

    suspend fun resumeAll(profileId: Long): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val rows = downloadDao.downloadsOnce(profileId).filter { it.status == DownloadStatus.PAUSED.name }
        rows.forEach {
            downloadDao.upsertDownload(it.copy(status = DownloadStatus.QUEUED.name,
                lastError = null, attempts = 0, updatedAt = now))
        }
        rows.size
    }

    /** Removes one download completely: row, origins, partial and final files, catalog entry. */
    suspend fun remove(profileId: Long, songId: String) = withContext(Dispatchers.IO) {
        val row = downloadDao.download(profileId, songId) ?: return@withContext
        row.relativePath?.let { path ->
            val root = profileDirectory(profileId).canonicalFile
            val file = File(root, path).canonicalFile
            if (file.path.startsWith(root.path + File.separator)) file.delete()
        }
        partFile(profileId, songId, row)?.delete()
        downloadDao.deleteDownload(profileId, songId)
        browseDao.deleteLocalAudioSong(profileId, songId)
    }

    data class PlaylistReconcile(
        val enqueued: Int = 0,
        val requeued: Int = 0,
        /** Songs that left the playlist but whose file is kept for other origins. */
        val keptForOtherOrigins: List<String> = emptyList(),
        /** Songs that left every origin: file and row removed. */
        val removed: List<String> = emptyList(),
    )

    /**
     * Mirrors a downloaded playlist to its current cached content (W08-T05): enqueues new
     * songs, requeues files whose server size changed, retries transient failures and
     * removes the playlist origin from songs that left. A file is deleted only when no
     * origin (other playlists, album, single) remains — single downloads and files used
     * by other playlists are never touched.
     */
    suspend fun reconcilePlaylistDownload(profileId: Long, playlistId: String, resumePaused: Boolean = true): PlaylistReconcile = withContext(Dispatchers.IO) {
        val origin = playlistOrigin(playlistId)
        if (browseDao.playlistOnce(profileId, playlistId) == null) {
            // A missing server playlist is not an empty playlist. Preserve its downloaded
            // snapshot and audio until the user explicitly removes this subscription.
            downloadDao.group(profileId, origin)?.let { downloadDao.upsertGroup(it.copy(serverMissing = true)) }
            return@withContext PlaylistReconcile()
        }
        val entries = browseDao.queueEntries(profileId, playlistId)
        val desired = entries.distinctBy { it.songId }.map { it.songId }
        val desiredSet = desired.toSet()
        val previous = downloadDao.originsFor(profileId, origin).map { it.songId }.toSet()
        val now = System.currentTimeMillis()

        // 1) enqueue/requeue everything currently in the playlist (completed = link only)
        val enqueuedCount = enqueuePlaylist(profileId, playlistId, resumePaused)

        // 2) requeue completed files whose server size changed (different file on server)
        val metadata = desired.chunked(500)
            .flatMap { libraryDao.songsByIds(profileId, it) }
            .associateBy { it.id }
        var requeued = 0
        metadata.values.forEach { song ->
            val row = downloadDao.download(profileId, song.id) ?: return@forEach
            val differs = song.size > 0 && row.status == DownloadStatus.COMPLETED.name &&
                row.serverSize in 1..Long.MAX_VALUE && row.serverSize != song.size
            if (differs) {
                row.relativePath?.let { path ->
                    val root = profileDirectory(profileId).canonicalFile
                    val file = File(root, path).canonicalFile
                    if (file.path.startsWith(root.path + File.separator)) file.delete()
                }
                partFile(profileId, song.id, row)?.delete()
                browseDao.deleteLocalAudioSong(profileId, song.id)
                downloadDao.upsertDownload(newRow(profileId, song, now).let { fresh ->
                    fresh.copy(createdAt = row.createdAt)
                })
                downloadDao.insertOrigin(DownloadOriginEntity(profileId, song.id, origin, now))
                requeued++
            } else if (row.status == DownloadStatus.FAILED.name &&
                (row.lastError == DownloadError.NETWORK.name || row.lastError == DownloadError.SPACE.name)) {
                downloadDao.upsertDownload(row.copy(status = DownloadStatus.QUEUED.name,
                    lastError = null, attempts = 0, updatedAt = now))
                requeued++
            }
        }

        // 3) songs no longer in the playlist: drop this origin; delete the file only if
        //    it is not referenced anywhere else
        val kept = mutableListOf<String>()
        val removed = mutableListOf<String>()
        previous.filter { it !in desiredSet }.forEach { songId ->
            val row = downloadDao.download(profileId, songId) ?: return@forEach
            downloadDao.deleteOrigin(profileId, songId, origin)
            if (downloadDao.originCount(profileId, songId) == 0) {
                row.relativePath?.let { path ->
                    val root = profileDirectory(profileId).canonicalFile
                    val file = File(root, path).canonicalFile
                    if (file.path.startsWith(root.path + File.separator)) file.delete()
                }
                partFile(profileId, songId, row)?.delete()
                downloadDao.deleteDownload(profileId, songId)
                browseDao.deleteLocalAudioSong(profileId, songId)
                removed.add(songId)
            } else {
                kept.add(songId)
            }
        }
        PlaylistReconcile(enqueuedCount, requeued, kept, removed)
    }

    /** Reconciles every downloaded playlist of the profile (auto path after a sync on Wi-Fi). */
    suspend fun reconcileDownloadedPlaylists(profileId: Long): Int {
        val origins = downloadDao.downloadedPlaylistOrigins(profileId)
        origins.forEach { origin ->
            val playlistId = origin.removePrefix(ORIGIN_PLAYLIST_PREFIX)
            reconcilePlaylistDownload(profileId, playlistId, resumePaused = false)
        }
        return origins.size
    }

    suspend fun removePlaylistDownload(profileId: Long, playlistId: String) {
        val origin = playlistOrigin(playlistId)
        for (membership in downloadDao.originsFor(profileId, origin)) {
            downloadDao.deleteOrigin(profileId, membership.songId, origin)
            if (downloadDao.originCount(profileId, membership.songId) == 0) remove(profileId, membership.songId)
        }
        downloadDao.deleteGroup(profileId, origin)
    }

    /**
     * Transfers one claimed row. Resumes from the .part file via HTTP Range when the server
     * confirms the offset (206 + matching Content-Range); a 200 answer to a ranged request
     * restarts from scratch. Status changes (pause/remove) are honoured mid-transfer.
     */
    suspend fun transfer(
        profileId: Long,
        songId: String,
        http: OkHttpClient,
        shouldStop: () -> Boolean,
        onBytes: suspend (Long, Long) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        val client = clientProvider(profileId) ?: return@withContext Outcome.Cancelled
        val current = downloadDao.download(profileId, songId)
            ?: return@withContext Outcome.Cancelled
        if (current.status != DownloadStatus.RUNNING.name) {
            return@withContext if (current.status == DownloadStatus.PAUSED.name) Outcome.Paused else Outcome.Cancelled
        }
        val dir = File(profileDirectory(profileId), MUSIC_DIR).apply { mkdirs() }
        val part = partFile(profileId, songId, current) ?: return@withContext Outcome.Permanent(DownloadError.INVALID)
        val validatorFile = File(part.path + ".validator")
        val validator = validatorFile.takeIf { it.isFile }?.let {
            HttpResume.strongEtag(it.readText())
        }
        // Without a strong representation validator it is safer to restart than splice
        // audio from different versions of an original after a process/network restart.
        if (part.isFile && validator == null) part.delete()
        val resumeFrom = if (part.isFile && part.length() > 0) part.length() else 0L
        val rangeEnd = if (resumeFrom > 0) resumeFrom - 1 else -1L

        if (!ensureSpace(dir, current.totalBytes.takeIf { it > 0 }?.minus(resumeFrom) ?: 0)) {
            fail(profileId, songId, current, DownloadError.SPACE)
            return@withContext Outcome.Space
        }

        val salt = randomSalt()
        val url = client.downloadUrl(songId, salt)
        val request = Request.Builder().url(url).apply {
            if (rangeEnd >= 0) header("Range", "bytes=$resumeFrom-")
            if (rangeEnd >= 0 && validator != null) header("If-Range", validator)
        }.build()
        val call = http.newCall(request)
        try {
            val response = call.execute()
            val outcome = response.use { resp ->
                when {
                    resp.code == 404 || resp.code == 410 -> {
                        fail(profileId, songId, current, DownloadError.NOT_FOUND)
                        Outcome.Permanent(DownloadError.NOT_FOUND)
                    }
                    resp.code == 401 || resp.code == 403 -> {
                        fail(profileId, songId, current, DownloadError.FORBIDDEN)
                        Outcome.Permanent(DownloadError.FORBIDDEN)
                    }
                    resp.code == 416 -> {
                        part.delete()
                        validatorFile.delete()
                        Outcome.Transient
                    }
                    resp.code in 500..599 -> Outcome.Transient
                    !resp.isSuccessful -> {
                        fail(profileId, songId, current, DownloadError.INVALID)
                        Outcome.Permanent(DownloadError.INVALID)
                    }
                    else -> copyBody(profileId, songId, current, resp, part, resumeFrom, shouldStop, onBytes)
                }
            }
            ensureActive()
            outcome
        } catch (e: IOException) {
            when (e) {
                is SocketTimeoutException, is UnknownHostException, is ConnectException,
                is SSLException, is java.io.InterruptedIOException -> Outcome.Transient
                else -> {
                    val space = runCatching { android.os.StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
                    if (space < 1024 * 1024) {
                        fail(profileId, songId, current, DownloadError.SPACE)
                        Outcome.Space
                    } else Outcome.Transient
                }
            }
        } finally {
            call.cancel()
            // Pauses and errors also checkpoint actual file truth, including the final
            // chunk written since the last throttled UI update. Never recreate a row.
            downloadDao.partialBytes(profileId, songId, if (part.isFile) part.length() else 0,
                System.currentTimeMillis())
            if (downloadDao.download(profileId, songId) == null) {
                // Cancellation/profile deletion may race the final read. A late writer
                // must not leave a newly recreated partial after profile cleanup.
                part.delete()
                validatorFile.delete()
            }
        }
    }

    private suspend fun copyBody(
        profileId: Long,
        songId: String,
        row: DownloadEntity,
        response: Response,
        part: File,
        requestedOffset: Long,
        shouldStop: () -> Boolean,
        onBytes: suspend (Long, Long) -> Unit,
    ): Outcome {
        val type = response.header("Content-Type").orEmpty().lowercase()
        if (type.contains("json") || type.contains("xml") || type.startsWith("text/")) {
            fail(profileId, songId, row, DownloadError.INVALID)
            return Outcome.Permanent(DownloadError.INVALID)
        }
        val body = response.body
        val contentRange = response.header("Content-Range")
        val resumed = response.code == 206
        var offset = 0L
        var total = body.contentLength().takeIf { it >= 0 } ?: -1L
        if (resumed) {
            if (requestedOffset > 0) {
                val savedValidator = File(part.path + ".validator").takeIf { it.isFile }?.readText()
                if (HttpResume.strongEtag(response.header("ETag")) != savedValidator) {
                    part.delete()
                    return Outcome.Transient
                }
            }
            val tail = HttpResume.tail(contentRange, requestedOffset, body.contentLength())
            if (tail == null) {
                // server cannot confirm our offset: drop the partial and restart from zero
                part.delete()
                return Outcome.Transient
            }
            total = tail.total
            offset = requestedOffset
        } else if (requestedOffset > 0) {
            // 200 for a ranged request: the server ignored Range, restart cleanly
            part.delete()
        }
        val expectedTotal = when {
            total > 0 -> total
            row.totalBytes > 0 -> row.totalBytes
            else -> -1L
        }
        if (expectedTotal > 0 && ensureSpace(part.parentFile!!, (expectedTotal - offset).coerceAtLeast(0))) {
            // ok
        } else if (expectedTotal > 0) {
            fail(profileId, songId, row, DownloadError.SPACE)
            return Outcome.Space
        }
        val source = body.byteStream()
        val validatorFile = File(part.path + ".validator")
        HttpResume.strongEtag(response.header("ETag"))?.let { validatorFile.writeText(it) }
        val output = java.io.RandomAccessFile(part, "rw").apply { setLength(offset); seek(offset) }
        var lastUpdate = 0L
        try {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (shouldStop()) {
                    return statusOnStop(profileId, songId)
                }
                val read = source.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                offset += read
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 900) {
                    lastUpdate = now
                    val stored = downloadDao.download(profileId, songId)
                        ?: return Outcome.Cancelled
                    if (stored.status != DownloadStatus.RUNNING.name) {
                        return if (stored.status == DownloadStatus.PAUSED.name) Outcome.Paused else Outcome.Cancelled
                    }
                    if (downloadDao.checkpoint(profileId, songId, offset, expectedTotal,
                            stored.relativePath ?: relativePathFor(songId, row), now) == 0)
                        return statusOnStop(profileId, songId)
                    onBytes(offset, expectedTotal)
                }
            }
            output.fd.sync()
        } finally {
            runCatching { output.close() }
            runCatching { source.close() }
        }
        if (shouldStop()) return statusOnStop(profileId, songId)
        val stored = downloadDao.download(profileId, songId) ?: return Outcome.Cancelled
        if (stored.status != DownloadStatus.RUNNING.name) {
            return if (stored.status == DownloadStatus.PAUSED.name) Outcome.Paused else Outcome.Cancelled
        }
        if (expectedTotal > 0 && offset != expectedTotal) {
            part.delete()
            downloadDao.upsertDownload(stored.copy(bytes = 0, totalBytes = expectedTotal,
                lastError = null, updatedAt = System.currentTimeMillis()))
            return Outcome.Transient
        }
        val relative = relativePathFor(songId, row)
        val final = File(profileDirectory(profileId).canonicalFile, relative)
        val canonical = final.canonicalFile
        if (!canonical.path.startsWith(profileDirectory(profileId).canonicalFile.path + File.separator)) {
            part.delete()
            fail(profileId, songId, stored, DownloadError.INVALID)
            return Outcome.Permanent(DownloadError.INVALID)
        }
        canonical.parentFile?.mkdirs()
        canonical.delete()
        if (!part.renameTo(canonical)) {
            return Outcome.Transient
        }
        validatorFile.delete()
        val extractor = android.media.MediaExtractor()
        val valid = try {
            extractor.setDataSource(canonical.path)
            (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } catch (_: Exception) { false }
        finally { extractor.release() }
        if (!valid) {
            canonical.delete()
            fail(profileId, songId, stored, DownloadError.INVALID)
            return Outcome.Permanent(DownloadError.INVALID)
        }
        val now = System.currentTimeMillis()
        var completed = false
        commitLocal {
            if (downloadDao.complete(profileId, songId, canonical.length(), relative, now) > 0) {
                browseDao.insertLocalAudio(LocalAudioEntity(profileId, songId, relative, canonical.length()))
                completed = true
            }
        }
        if (!completed) {
            canonical.delete()
            return statusOnStop(profileId, songId)
        }
        return Outcome.Completed
    }

    private suspend fun statusOnStop(profileId: Long, songId: String): Outcome {
        val stored = downloadDao.download(profileId, songId) ?: return Outcome.Cancelled
        return when (stored.status) {
            DownloadStatus.PAUSED.name -> Outcome.Paused
            DownloadStatus.RUNNING.name -> Outcome.Cancelled // system stop: worker re-queues
            else -> Outcome.Cancelled
        }
    }

    private suspend fun fail(profileId: Long, songId: String, row: DownloadEntity, error: DownloadError) {
        downloadDao.failRunning(profileId, songId, error.name, System.currentTimeMillis())
    }

    private suspend fun failOutcome(profileId: Long, songId: String, row: DownloadEntity,
                                    error: DownloadError): Outcome {
        fail(profileId, songId, row, error)
        return Outcome.Permanent(error)
    }

    private fun ensureSpace(dir: File, needed: Long): Boolean = try {
        val stats = android.os.StatFs(dir.path)
        stats.availableBytes > needed + SPACE_MARGIN
    } catch (_: IllegalArgumentException) { true }

    private fun newRow(profileId: Long, song: SongEntity, now: Long) = DownloadEntity(
        profileId = profileId, songId = song.id, status = DownloadStatus.QUEUED.name,
        relativePath = null, bytes = 0,
        totalBytes = song.size.takeIf { it > 0 } ?: -1,
        title = song.title, artist = song.displayArtist ?: song.artist, album = song.album,
        albumId = song.albumId, coverArt = song.coverArt, duration = song.duration,
        suffix = song.suffix, contentType = song.contentType, serverSize = song.size,
        createdAt = now, completedAt = null, updatedAt = now, lastError = null, attempts = 0)

    private fun partFile(profileId: Long, songId: String, row: DownloadEntity): File? {
        val root = profileDirectory(profileId).canonicalFile
        return File(root, "${MUSIC_DIR}/${File(relativePathFor(songId, row)).name}.part")
    }

    private fun relativePathFor(songId: String, row: DownloadEntity): String {
        val existing = row.relativePath
        if (existing != null && !existing.isBlank()) return existing
        val ext = extension(row)
        return "$MUSIC_DIR/${safeName(songId)}.$ext"
    }

    private fun extension(row: DownloadEntity): String {
        row.suffix?.takeIf { it.isNotBlank() && it.matches(Regex("[A-Za-z0-9]{1,8}")) }?.let { return it.lowercase() }
        row.contentType?.substringAfter('/')?.takeIf { it.isNotBlank() && it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?.let { return it.lowercase() }
        return "audio"
    }

    private fun safeName(songId: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(songId.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return hash
    }

    companion object {
        const val MUSIC_DIR = "music"
        const val ORIGIN_SINGLE = "single"
        const val ORIGIN_ALBUM_PREFIX = "album:"
        const val ORIGIN_PLAYLIST_PREFIX = "playlist:"
        private const val SPACE_MARGIN = 16L * 1024 * 1024

        fun albumOrigin(albumId: String) = ORIGIN_ALBUM_PREFIX + albumId
        fun playlistOrigin(playlistId: String) = ORIGIN_PLAYLIST_PREFIX + playlistId

        /** Bytes actually used by download files (and partials) on disk. */
        suspend fun musicBytes(profileDirectory: File): Long = withContext(Dispatchers.IO) {
            val dir = File(profileDirectory, MUSIC_DIR)
            if (!dir.isDirectory) 0 else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
}
