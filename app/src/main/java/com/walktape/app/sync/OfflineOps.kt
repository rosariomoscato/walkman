package com.walktape.app.sync

import com.google.gson.Gson
import com.walktape.app.core.db.BrowseDao
import com.walktape.app.core.db.DownloadDao
import com.walktape.app.core.db.LibraryDao
import com.walktape.app.core.db.PendingOpEntity
import com.walktape.app.core.db.PendingOpState
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Durable per-profile queue of offline mutations (W08-T06). Playlist structural edits are
 * recorded as SET_CONTENT with the full intended order, so a resend after a lost ACK ends
 * in the same server state; creates are preflighted by name+content before any (re)send,
 * because createPlaylist is not idempotent. Favorites are idempotent star/unstar calls.
 * Payloads carry identifiers and metadata only — never credentials or authenticated URLs.
 */
class OfflineOps(
    private val downloadDao: DownloadDao,
    private val browseDao: BrowseDao,
    private val libraryDao: LibraryDao,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
    private val usernameProvider: suspend (Long) -> String?,
    private val offlineOnly: (Long) -> Boolean,
    private val scope: CoroutineScope,
    private val gson: Gson = Gson(),
    private val autoSync: Boolean = true,
    private val schedule: ((Long) -> Unit)? = null,
) {

    // --- payloads (identifier/metadata only) ---
    data class CreatePayload(val localId: String, val name: String, val songIds: List<String>,
        val knownBefore: List<String>? = null)
    data class RenamePayload(val playlistId: String, val oldName: String, val newName: String)
    data class DeletePayload(val playlistId: String, val name: String,
        val entries: List<EntrySnapshot>)
    data class EntrySnapshot(val songId: String, val title: String, val artist: String?,
        val album: String?, val duration: Int)
    data class SetContentPayload(val playlistId: String, val orderedSongIds: List<String>,
        val baselineContent: String)
    data class StarPayload(val kind: String, val id: String, val starred: Boolean)

    companion object {
        const val KIND_CREATE = "PLAYLIST_CREATE"
        const val KIND_RENAME = "PLAYLIST_RENAME"
        const val KIND_DELETE = "PLAYLIST_DELETE"
        const val KIND_SET_CONTENT = "PLAYLIST_SET_CONTENT"
        const val KIND_STAR = "STAR"

        fun signature(songIds: List<String>): String = Gson().toJson(songIds)
        fun localPlaylistId(): String = "local-" + UUID.randomUUID().toString().take(13)
    }

    fun ops(profileId: Long): Flow<List<PendingOpEntity>> = downloadDao.ops(profileId)
    fun pendingCount(profileId: Long): Flow<Int> = downloadDao.observeUnsyncedCount(profileId)

    // --- queueing (local apply happens in the repositories before/with these calls) ---

    suspend fun queueCreate(profileId: Long, localId: String, name: String, songIds: List<String>, attempted: Boolean = false) {
        insertOp(profileId, KIND_CREATE, gson.toJson(CreatePayload(localId, name, songIds)))
        if (attempted) {
            downloadDao.opsOnce(profileId).firstOrNull { it.kind == KIND_CREATE &&
                conflictPlaylistId(it) == localId }?.let { downloadDao.upsertOp(it.copy(attempts = 1)) }
        }
        triggerSync(profileId)
    }

    suspend fun queueRename(profileId: Long, playlistId: String, oldName: String, newName: String,
                            baselineChanged: String?) {
        val creation = pendingCreation(profileId, playlistId)
        if (creation != null) {
            val payload = gson.fromJson(creation.payload, CreatePayload::class.java)
            downloadDao.upsertOp(creation.copy(payload = gson.toJson(payload.copy(name = newName))))
            triggerSync(profileId)
            return
        }
        insertOp(profileId, KIND_RENAME, gson.toJson(RenamePayload(playlistId, oldName, newName)),
            baselineChanged = baselineChanged)
        triggerSync(profileId)
    }

    suspend fun queueDelete(profileId: Long, playlistId: String) {
        val creation = pendingCreation(profileId, playlistId)
        if (creation != null) {
            downloadDao.opsOnce(profileId).filter { conflictPlaylistId(it) == playlistId }
                .forEach { downloadDao.deleteOp(profileId, it.opId) }
            return
        }
        val playlist = browseDao.playlistOnce(profileId, playlistId)
        val entries = browseDao.queueEntries(profileId, playlistId).map {
            EntrySnapshot(it.songId, it.title, it.artist, it.album, it.duration)
        }
        insertOp(profileId, KIND_DELETE,
            gson.toJson(DeletePayload(playlistId, playlist?.name ?: "", entries)),
            baselineChanged = playlist?.changed)
        triggerSync(profileId)
    }

    suspend fun queueSetContent(profileId: Long, playlistId: String, orderedSongIds: List<String>,
                                baselineChanged: String?, baselineContent: List<String>) {
        val creation = pendingCreation(profileId, playlistId)
        if (creation != null) {
            val payload = gson.fromJson(creation.payload, CreatePayload::class.java)
            downloadDao.upsertOp(creation.copy(payload = gson.toJson(payload.copy(songIds = orderedSongIds))))
            triggerSync(profileId)
            return
        }
        val previous = downloadDao.opsOnce(profileId).lastOrNull {
            it.kind == KIND_SET_CONTENT && it.attempts == 0 &&
                it.state in listOf(PendingOpState.PENDING.name, PendingOpState.CONFLICT.name) &&
                conflictPlaylistId(it) == playlistId
        }
        if (previous != null) {
            val payload = gson.fromJson(previous.payload, SetContentPayload::class.java)
            downloadDao.upsertOp(previous.copy(payload = gson.toJson(payload.copy(orderedSongIds = orderedSongIds))))
            triggerSync(profileId)
            return
        }
        insertOp(profileId, KIND_SET_CONTENT,
            gson.toJson(SetContentPayload(playlistId, orderedSongIds, signature(baselineContent))),
            baselineChanged = baselineChanged)
        triggerSync(profileId)
    }

    private suspend fun pendingCreation(profileId: Long, playlistId: String): PendingOpEntity? =
        downloadDao.opsOnce(profileId).firstOrNull { it.kind == KIND_CREATE && it.attempts == 0 &&
            it.state == PendingOpState.PENDING.name && conflictPlaylistId(it) == playlistId }

    suspend fun queueStar(profileId: Long, kind: String, id: String, starred: Boolean) {
        insertOp(profileId, KIND_STAR, gson.toJson(StarPayload(kind, id, starred)))
        triggerSync(profileId)
    }

    private suspend fun insertOp(profileId: Long, kind: String, payload: String,
                                 baselineChanged: String? = null, baselineSignature: String? = null) {
        val now = maxOf(System.currentTimeMillis(),
            (downloadDao.opsOnce(profileId).maxOfOrNull { it.createdAt } ?: 0L) + 1)
        downloadDao.upsertOp(PendingOpEntity(
            profileId = profileId, opId = UUID.randomUUID().toString(), kind = kind,
            payload = payload, baselineChanged = baselineChanged,
            baselineSignature = baselineSignature, createdAt = now,
            state = PendingOpState.PENDING.name, attempts = 0, lastError = null, updatedAt = now))
    }

    // --- draining ---

    private val drainMutex = Mutex()
    private val editMutex = Mutex()

    suspend fun <T> serializeEdit(action: suspend () -> T): T = editMutex.withLock { action() }

    fun triggerSync(profileId: Long) {
        if (autoSync) {
            if (schedule != null) schedule.invoke(profileId)
            else scope.launch { drain(profileId) }
        }
    }

    suspend fun drain(profileId: Long): Boolean = drainMutex.withLock {
        editMutex.lock()
        try {
        // recover SYNCING rows orphaned by process death (single drainer per process)
        val recovered = System.currentTimeMillis()
        downloadDao.opsOnce(profileId).filter { it.state == PendingOpState.SYNCING.name }
            .forEach { downloadDao.upsertOp(it.copy(state = PendingOpState.PENDING.name,
                attempts = it.attempts + 1, updatedAt = recovered)) }
        while (true) {
            if (offlineOnly(profileId)) return@withLock false
            val all = downloadDao.opsOnce(profileId)
            val blocked = all.filter { it.state in listOf(PendingOpState.CONFLICT.name, PendingOpState.FAILED.name) }
                .map(::conflictPlaylistId).filter { it.isNotEmpty() }.toSet()
            val op = all.firstOrNull { it.state == PendingOpState.PENDING.name &&
                conflictPlaylistId(it) !in blocked } ?: break
            downloadDao.upsertOp(op.copy(state = PendingOpState.SYNCING.name,
                updatedAt = System.currentTimeMillis()))
            when (val outcome = runOp(profileId, op)) {
                OpOutcome.Done -> downloadDao.deleteOp(profileId, op.opId)
                OpOutcome.Transient -> {
                    val latest = downloadDao.op(profileId, op.opId) ?: return@withLock false
                    downloadDao.upsertOp(latest.copy(state = PendingOpState.PENDING.name,
                        attempts = latest.attempts + 1, updatedAt = System.currentTimeMillis()))
                    return@withLock false // network uncertain: stop the whole queue
                }
                is OpOutcome.Failed -> {
                    val latest = downloadDao.op(profileId, op.opId) ?: return@withLock false
                    downloadDao.upsertOp(latest.copy(state = PendingOpState.FAILED.name,
                        lastError = outcome.error, attempts = latest.attempts + 1,
                        updatedAt = System.currentTimeMillis()))
                }
                is OpOutcome.Conflict -> {
                    downloadDao.upsertOp(op.copy(state = PendingOpState.CONFLICT.name,
                        lastError = "CONFLICT", updatedAt = System.currentTimeMillis()))
                    markPlaylistConflict(profileId, op, true)
                }
            }
        }
        clearSyncedFlags(profileId)
        downloadDao.opsOnce(profileId).isEmpty()
        } finally { editMutex.unlock() }
    }

    private sealed interface OpOutcome {
        data object Done : OpOutcome
        data object Transient : OpOutcome
        data class Failed(val error: String) : OpOutcome
        data object Conflict : OpOutcome
    }

    private suspend fun runOp(profileId: Long, op: PendingOpEntity): OpOutcome = withContext(Dispatchers.IO) {
        val client = clientProvider(profileId) ?: return@withContext OpOutcome.Failed("CREDENTIALS")
        when (op.kind) {
            KIND_CREATE -> runCreate(profileId, op, client)
            KIND_RENAME -> runRename(profileId, op, client)
            KIND_DELETE -> runDelete(profileId, op, client)
            KIND_SET_CONTENT -> runSetContent(profileId, op, client)
            KIND_STAR -> runStar(op, client)
            else -> OpOutcome.Failed("UNSUPPORTED")
        }
    }

    /**
     * createPlaylist is NOT idempotent: before any (re)send look for an own playlist with
     * the same name and content — a previous ACK may have been lost. Only a fresh send
     * creates; the result is remapped onto the local placeholder id either way.
     */
    private suspend fun runCreate(profileId: Long, op: PendingOpEntity, client: OpenSubsonicClient): OpOutcome {
        val payload = gson.fromJson(op.payload, CreatePayload::class.java)
        val username = usernameProvider(profileId) ?: return OpOutcome.Failed("CREDENTIALS")
        val playlists = when (val list = client.playlists()) {
            is ApiResult.Failure -> return transientOrFailed(list.error)
            is ApiResult.Success -> list.value.filter { it.owner == username }
        }
        val detail: com.walktape.app.core.net.PlaylistDetailDto
        if (op.attempts > 0) {
            // Only a new server ID compared with our durable PRE-send baseline can be
            // reconciled. A same-name pre-existing playlist is never adopted arbitrarily.
            val baseline = payload.knownBefore ?: return OpOutcome.Conflict
            val candidates = playlists.filter { it.name == payload.name && it.id !in baseline }
            if (candidates.size != 1) return OpOutcome.Conflict
            detail = when (val fetched = client.playlist(candidates.single().id)) {
                is ApiResult.Failure -> return transientOrFailed(fetched.error)
                is ApiResult.Success -> fetched.value
            }
            if (detail.entries.map { it.id } != payload.songIds) return OpOutcome.Conflict
        } else {
            // Persist the list of old IDs AND the possibly-sent marker before POST.
            downloadDao.upsertOp(op.copy(payload = gson.toJson(payload.copy(
                knownBefore = playlists.map { it.id })), attempts = 1, state = PendingOpState.SYNCING.name))
            detail = when (val result = client.createPlaylist(payload.name, payload.songIds)) {
                is ApiResult.Failure -> return transientOrFailed(result.error)
                is ApiResult.Success -> result.value
            }
        }
        val targetId = detail.playlist.id
        // Cache the acknowledged server state; the local placeholder disappears.
        for (dependent in downloadDao.opsOnce(profileId).filter { it.opId != op.opId }) {
            val replacement = when (dependent.kind) {
                KIND_RENAME -> gson.fromJson(dependent.payload, RenamePayload::class.java).let {
                    if (it.playlistId == payload.localId) gson.toJson(it.copy(playlistId = targetId)) else null
                }
                KIND_SET_CONTENT -> gson.fromJson(dependent.payload, SetContentPayload::class.java).let {
                    if (it.playlistId == payload.localId) gson.toJson(it.copy(playlistId = targetId)) else null
                }
                KIND_DELETE -> gson.fromJson(dependent.payload, DeletePayload::class.java).let {
                    if (it.playlistId == payload.localId) gson.toJson(it.copy(playlistId = targetId)) else null
                }
                else -> null
            }
            if (replacement != null) downloadDao.upsertOp(dependent.copy(payload = replacement))
        }
        browseDao.deletePlaylist(profileId, payload.localId)
        cachePlaylist(profileId, targetId) { detail }
        return OpOutcome.Done
    }

    private suspend fun runRename(profileId: Long, op: PendingOpEntity, client: OpenSubsonicClient): OpOutcome {
        val payload = gson.fromJson(op.payload, RenamePayload::class.java)
        val current = when (val fetched = client.playlist(payload.playlistId)) {
            is ApiResult.Failure -> {
                return if (fetched.error is ApiError.Protocol &&
                    (fetched.error as ApiError.Protocol).kind == com.walktape.app.core.net.ProtocolErrorKind.NOT_FOUND)
                    OpOutcome.Conflict // local rename must survive a server-side deletion
                else transientOrFailed(fetched.error)
            }
            is ApiResult.Success -> fetched.value
        }
        val owner = usernameProvider(profileId) ?: return OpOutcome.Failed("CREDENTIALS")
        if (current.playlist.readOnly || current.playlist.owner != owner) return OpOutcome.Failed("FORBIDDEN")
        if (current.playlist.name == payload.newName) return OpOutcome.Done
        // concurrent rename: baseline name no longer matches
        if (current.playlist.name != payload.oldName) return OpOutcome.Conflict
        return when (val result = client.updatePlaylist(payload.playlistId, name = payload.newName)) {
            is ApiResult.Failure -> transientOrFailed(result.error)
            is ApiResult.Success -> {
                when (val fresh = client.playlist(payload.playlistId)) {
                    is ApiResult.Success -> cachePlaylist(profileId, payload.playlistId) { fresh.value }
                    is ApiResult.Failure -> return OpOutcome.Transient
                }
                OpOutcome.Done
            }
        }
    }

    private suspend fun runDelete(profileId: Long, op: PendingOpEntity, client: OpenSubsonicClient): OpOutcome {
        val payload = gson.fromJson(op.payload, DeletePayload::class.java)
        return when (val fetched = client.playlist(payload.playlistId)) {
            is ApiResult.Failure -> {
                val error = fetched.error
                if (error is ApiError.Protocol &&
                    error.kind == com.walktape.app.core.net.ProtocolErrorKind.NOT_FOUND)
                    OpOutcome.Done // already gone: a lost ACK must not resurrect the playlist
                else transientOrFailed(error)
            }
            is ApiResult.Success -> {
                val username = usernameProvider(profileId)
                if (fetched.value.playlist.readOnly ||
                    username?.let { it != fetched.value.playlist.owner } == true)
                    return OpOutcome.Failed("FORBIDDEN") // permission changed ≠ success
                if (fetched.value.playlist.name != payload.name ||
                    fetched.value.entries.map { it.id } != payload.entries.map { it.songId })
                    return OpOutcome.Conflict
                when (val result = client.deletePlaylist(payload.playlistId)) {
                    is ApiResult.Failure -> transientOrFailed(result.error)
                    is ApiResult.Success -> OpOutcome.Done
                }
            }
        }
    }

    /**
     * Structural edits: compare the server list with the intended content first. Equal
     * content means a previous ACK was lost — done without resending. A changed server
     * "changed" timestamp with different content from our baseline is a real conflict.
     */
    private suspend fun runSetContent(profileId: Long, op: PendingOpEntity, client: OpenSubsonicClient): OpOutcome {
        val payload = gson.fromJson(op.payload, SetContentPayload::class.java)
        val current = when (val fetched = client.playlist(payload.playlistId)) {
            is ApiResult.Failure -> {
                val error = fetched.error
                return if (error is ApiError.Protocol &&
                    error.kind == com.walktape.app.core.net.ProtocolErrorKind.NOT_FOUND)
                    OpOutcome.Conflict // deleted on the server while edited offline
                else transientOrFailed(error)
            }
            is ApiResult.Success -> fetched.value
        }
        val serverIds = current.entries.map { it.id }
        val username = usernameProvider(profileId) ?: return OpOutcome.Failed("CREDENTIALS")
        if (current.playlist.readOnly || current.playlist.owner != username)
            return OpOutcome.Failed("FORBIDDEN")
        if (serverIds == payload.orderedSongIds) return OpOutcome.Done
        if (signature(serverIds) != payload.baselineContent) {
            return OpOutcome.Conflict
        }
        return when (val result = client.updatePlaylist(payload.playlistId,
            songIdsToAdd = payload.orderedSongIds,
            songIndexesToRemove = serverIds.indices.toList())) {
            is ApiResult.Failure -> transientOrFailed(result.error)
            is ApiResult.Success -> {
                // refresh the cache with the acknowledged state (order/copies preserved)
                when (val refreshed = client.playlist(payload.playlistId)) {
                    is ApiResult.Failure -> OpOutcome.Done // ACK received; cache retried later
                    is ApiResult.Success -> {
                        cachePlaylist(profileId, payload.playlistId) { refreshed.value }
                        OpOutcome.Done
                    }
                }
            }
        }
    }

    private suspend fun runStar(op: PendingOpEntity, client: OpenSubsonicClient): OpOutcome {
        val payload = gson.fromJson(op.payload, StarPayload::class.java)
        val result = when (payload.kind) {
            "SONG" -> if (payload.starred) client.star(songIds = listOf(payload.id))
            else client.unstar(songIds = listOf(payload.id))
            "ALBUM" -> if (payload.starred) client.star(albumIds = listOf(payload.id))
            else client.unstar(albumIds = listOf(payload.id))
            "ARTIST" -> if (payload.starred) client.star(artistIds = listOf(payload.id))
            else client.unstar(artistIds = listOf(payload.id))
            else -> return OpOutcome.Failed("UNSUPPORTED")
        }
        return when (result) {
            is ApiResult.Failure -> transientOrFailed(result.error)
            is ApiResult.Success -> OpOutcome.Done
        }
    }

    private fun transientOrFailed(error: ApiError): OpOutcome = when (error) {
        ApiError.Unreachable, ApiError.Timeout, ApiError.Network, ApiError.Tls -> OpOutcome.Transient
        is ApiError.Http -> if (error.status >= 500) OpOutcome.Transient else OpOutcome.Failed("HTTP_${error.status}")
        is ApiError.Protocol -> if (error.code == 0 && error.kind == com.walktape.app.core.net.ProtocolErrorKind.GENERIC)
            OpOutcome.Transient else OpOutcome.Failed("SERVER_${error.code}")
        else -> OpOutcome.Failed("REQUEST")
    }

    // --- conflict resolution (explicit user choices; local version is preserved) ---

    /** Keep local: re-apply the intended state on the fresh server baseline. */
    suspend fun resolveConflictKeepLocal(profileId: Long, opId: String): Boolean {
        editMutex.lock()
        try {
        if (offlineOnly(profileId)) return false
        val op = downloadDao.op(profileId, opId) ?: return false
        if (op.state != PendingOpState.CONFLICT.name) return false
        val client = clientProvider(profileId) ?: return false
        val fresh = when (val fetched = client.playlist(conflictPlaylistId(op))) {
            is ApiResult.Failure -> return false
            is ApiResult.Success -> fetched.value
        }
        val owner = usernameProvider(profileId) ?: return false
        if (fresh.playlist.readOnly || fresh.playlist.owner != owner) return false
        when (op.kind) {
            KIND_SET_CONTENT, KIND_RENAME -> {
                val playlistId = conflictPlaylistId(op)
                val local = browseDao.playlistOnce(profileId, playlistId) ?: return false
                val ordered = browseDao.queueEntries(profileId, playlistId).map { it.songId }
                val result = client.updatePlaylist(playlistId, name = local.name,
                    songIdsToAdd = ordered,
                    songIndexesToRemove = fresh.entries.indices.toList())
                if (result is ApiResult.Failure) return false
                when (val refreshed = client.playlist(playlistId)) {
                    is ApiResult.Success -> cachePlaylist(profileId, playlistId) { refreshed.value }
                    is ApiResult.Failure -> return false
                }
            }
            KIND_DELETE -> {
                val payload = gson.fromJson(op.payload, DeletePayload::class.java)
                if (client.deletePlaylist(payload.playlistId) !is ApiResult.Success) return false
                browseDao.deletePlaylist(profileId, payload.playlistId)
            }
            else -> return false
        }
        consumePlaylistOps(profileId, conflictPlaylistId(op))
        markPlaylistConflict(profileId, op, false)
        clearSyncedFlags(profileId)
        return true
        } finally { editMutex.unlock() }
    }

    /** Save the local version as a NEW playlist; the original returns to server state. */
    suspend fun resolveConflictAsNewPlaylist(profileId: Long, opId: String): Boolean {
        editMutex.lock()
        try {
        if (offlineOnly(profileId)) return false
        val op = downloadDao.op(profileId, opId) ?: return false
        if (op.state != PendingOpState.CONFLICT.name) return false
        val client = clientProvider(profileId) ?: return false
        val playlistId = conflictPlaylistId(op)
        if (op.kind == KIND_CREATE) {
            val payload = gson.fromJson(op.payload, CreatePayload::class.java)
            downloadDao.upsertOp(op.copy(payload = gson.toJson(payload.copy(
                name = "${payload.name} • ${op.opId.take(8)}")),
                attempts = 0, state = PendingOpState.PENDING.name, lastError = null))
            triggerSync(profileId)
            return true
        }
        val serverVersion = when (val fetched = client.playlist(playlistId)) {
            is ApiResult.Success -> fetched.value
            is ApiResult.Failure -> return false
        }
        when (op.kind) {
            KIND_SET_CONTENT, KIND_RENAME -> {
                val local = browseDao.playlistOnce(profileId, playlistId) ?: return false
                val entries = browseDao.queueEntries(profileId, playlistId)
                val cloneId = "local-new-${op.opId}"
                val cloneName = "${local.name} • ${op.opId.take(8)}"
                browseDao.replacePlaylist(local.copy(id = cloneId, name = cloneName,
                    pendingSync = true, pendingConflict = false),
                    entries.map { it.copy(playlistId = cloneId) })
                queueCreate(profileId, cloneId, cloneName, entries.map { it.songId })
            }
            else -> return false
        }
        // original playlist returns to the server version
        cachePlaylist(profileId, playlistId) { serverVersion }
        consumePlaylistOps(profileId, playlistId)
        markPlaylistConflict(profileId, op, false)
        clearSyncedFlags(profileId)
        return true
        } finally { editMutex.unlock() }
    }

    /** Keep the server version: local edits for this op are discarded, nothing is silent. */
    suspend fun resolveConflictKeepServer(profileId: Long, opId: String): Boolean {
        editMutex.lock()
        try {
        if (offlineOnly(profileId)) return false
        val op = downloadDao.op(profileId, opId) ?: return false
        if (op.state != PendingOpState.CONFLICT.name) return false
        val client = clientProvider(profileId) ?: return false
        val playlistId = conflictPlaylistId(op)
        when (val refreshed = client.playlist(playlistId)) {
            is ApiResult.Failure -> return false
            is ApiResult.Success -> cachePlaylist(profileId, playlistId) { refreshed.value }
        }
        consumePlaylistOps(profileId, playlistId)
        markPlaylistConflict(profileId, op, false)
        clearSyncedFlags(profileId)
        return true
        } finally { editMutex.unlock() }
    }

    private suspend fun consumePlaylistOps(profileId: Long, playlistId: String) {
        downloadDao.opsOnce(profileId).filter { conflictPlaylistId(it) == playlistId }
            .forEach { downloadDao.deleteOp(profileId, it.opId) }
    }

    fun conflictPlaylistId(op: PendingOpEntity): String = when (op.kind) {
        KIND_CREATE -> gson.fromJson(op.payload, CreatePayload::class.java).localId
        KIND_SET_CONTENT -> gson.fromJson(op.payload, SetContentPayload::class.java).playlistId
        KIND_RENAME -> gson.fromJson(op.payload, RenamePayload::class.java).playlistId
        KIND_DELETE -> gson.fromJson(op.payload, DeletePayload::class.java).playlistId
        else -> ""
    }

    private suspend fun markPlaylistConflict(profileId: Long, op: PendingOpEntity, conflict: Boolean) {
        val playlistId = conflictPlaylistId(op)
        if (playlistId.isEmpty()) return
        val row = browseDao.playlistOnce(profileId, playlistId) ?: return
        browseDao.updatePlaylistFlags(profileId, playlistId,
            pendingSync = conflict || row.pendingSync, conflict = conflict)
    }

    /** Clears pendingSync on playlists without remaining queue operations. */
    private suspend fun clearSyncedFlags(profileId: Long) {
        val remaining = downloadDao.opsOnce(profileId)
        browseDao.pendingPlaylistsOnce(profileId).forEach { playlist ->
            val used = remaining.any { conflictPlaylistId(it) == playlist.id }
            if (!used && playlist.pendingSync) {
                browseDao.updatePlaylistFlags(profileId, playlist.id, pendingSync = false,
                    conflict = playlist.pendingConflict)
            }
        }
    }

    /** Writes a server-acknowledged playlist into the cache with fresh entries. */
    private suspend fun cachePlaylist(profileId: Long, playlistId: String,
                                       detailProvider: suspend () -> com.walktape.app.core.net.PlaylistDetailDto) {
        val detail = detailProvider()
        val p = detail.playlist
        browseDao.replacePlaylist(PlaylistEntity(profileId, p.id, p.name, p.owner, p.isPublic,
            p.comment, p.coverArt, p.songCount, p.duration, p.created, p.changed,
            System.currentTimeMillis(), p.readOnly),
            detail.entries.mapIndexed { position, song ->
                PlaylistEntryEntity(profileId, p.id, position, song.id, song.title,
                    song.displayArtist?.takeIf { it.isNotBlank() } ?: song.artist,
                    song.album, song.duration, song.year, song.genre)
            })
    }
}
