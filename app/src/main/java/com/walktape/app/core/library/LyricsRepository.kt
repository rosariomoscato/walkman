package com.walktape.app.core.library

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.walktape.app.core.db.LyricsDao
import com.walktape.app.core.db.LyricsEntity
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient

/**
 * Server lyrics with a per-song Room cache. "None" is an honest state and is cached too,
 * so a song without lyrics does not produce a request on every open.
 */
class LyricsRepository(
    private val dao: LyricsDao,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
    private val offlineOnly: (Long) -> Boolean = { false },
) {
    data class LyricsLine(val value: String, val startMs: Long?, val endMs: Long?)
    data class SongLyrics(
        val displayArtist: String?,
        val displayTitle: String?,
        val lang: String?,
        val synced: Boolean,
        val lines: List<LyricsLine>,
    )

    sealed interface Result {
        data class Available(val lyrics: SongLyrics, val fromCache: Boolean) : Result
        data object None : Result
        data class Unavailable(val error: ApiError) : Result
    }

    suspend fun lyrics(profileId: Long, songId: String, refresh: Boolean = false): Result {
        if (!refresh) {
            dao.get(profileId, songId)?.let { cached ->
                return cached.toResult(fromCache = true)
            }
        }
        if (offlineOnly(profileId)) return Result.Unavailable(ApiError.Unreachable)
        val client = clientProvider(profileId) ?: return Result.Unavailable(ApiError.CredentialsUnavailable)
        return when (val result = client.lyrics(songId)) {
            is ApiResult.Failure -> Result.Unavailable(result.error)
            is ApiResult.Success -> {
                val structured = result.value.firstOrNull { it.synced && it.lines.isNotEmpty() }
                    ?: result.value.firstOrNull { it.lines.isNotEmpty() }
                val entity = structured?.let {
                    LyricsEntity(profileId, songId, it.displayArtist, it.displayTitle, it.lang,
                        it.synced, gson.toJson(it.lines.map { line ->
                            LyricsLine(line.value, line.startMs, line.endMs) }), System.currentTimeMillis())
                } ?: LyricsEntity(profileId, songId, null, null, null, false, "[]",
                    System.currentTimeMillis())
                dao.insert(entity)
                entity.toResult(fromCache = false)
            }
        }
    }

    private fun LyricsEntity.toResult(fromCache: Boolean): Result {
        val parsed: List<LyricsLine> = gson.fromJson(lines, lineType)
            ?: return Result.Unavailable(ApiError.InvalidResponse)
        if (parsed.isEmpty()) return Result.None
        return Result.Available(SongLyrics(displayArtist, displayTitle, lang, synced, parsed), fromCache)
    }

    companion object {
        private val gson = Gson()
        private val lineType = object : TypeToken<List<LyricsLine>>() {}.type
    }
}
