package com.walktape.app.core.net

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.StringReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

/** One instance per profile; no global mutable credentials, cookies, cache or HTTP logger. */
class OpenSubsonicClient(
    private val address: ServerAddress,
    private val credentials: Credentials,
    private val http: OkHttpClient = defaultHttpClient(),
    private val networkAllowed: suspend () -> Boolean = { true },
    private val salt: () -> String = ::randomSalt,
) {
    suspend fun ping(): ApiResult<ServerInfo> = when (val result = request("ping")) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            val root = result.value
            ApiResult.Success(ServerInfo(
                root.get("version").asString,
                root.get("type")?.asString,
                root.get("serverVersion")?.asString,
                root.get("openSubsonic")?.asBoolean ?: false,
            ))
        } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
    }

    suspend fun extensions(): ApiResult<List<ServerExtension>> =
        when (val result = request("getOpenSubsonicExtensions")) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> try {
                val extensions = result.value.getAsJsonArray("openSubsonicExtensions")
                    ?: return ApiResult.Failure(ApiError.InvalidResponse)
                ApiResult.Success(extensions.map {
                    val item = it.asJsonObject
                    ServerExtension(item.get("name").asString,
                        item.getAsJsonArray("versions").map { version -> version.asInt })
                })
            } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
        }

    suspend fun artists(): ApiResult<List<ArtistDto>> = when (val result = request("getArtists")) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            val list = mutableListOf<ArtistDto>()
            val artists = result.value.obj("artists") ?: return ApiResult.Failure(ApiError.InvalidResponse)
            val indexes = artists.getAsJsonArray("index") ?: emptyList()
            indexes.forEach { index ->
                index.asJsonObject.getAsJsonArray("artist")?.forEach { artist ->
                    list.add(parseArtist(artist.asJsonObject))
                }
            }
            ApiResult.Success(list)
        } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
    }

    suspend fun playlists(): ApiResult<List<PlaylistDto>> = when (val result = request("getPlaylists")) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            val root = result.value.obj("playlists") ?: return ApiResult.Failure(ApiError.InvalidResponse)
            ApiResult.Success(root.getAsJsonArray("playlist")?.map { parsePlaylist(it.asJsonObject) } ?: emptyList())
        } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
    }

    suspend fun playlist(id: String): ApiResult<PlaylistDetailDto> = when (val result = request("getPlaylist", listOf("id" to id))) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            val root = result.value.obj("playlist") ?: return ApiResult.Failure(ApiError.InvalidResponse)
            val playlist = parsePlaylist(root)
            val entries = root.getAsJsonArray("entry")?.map { parseSong(it.asJsonObject) } ?: emptyList()
            if (playlist.id != id || playlist.songCount != entries.size) return ApiResult.Failure(ApiError.InvalidResponse)
            ApiResult.Success(PlaylistDetailDto(playlist, entries))
        } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
    }

    suspend fun albumList(type: String, size: Int, offset: Int): ApiResult<List<AlbumDto>> =
        when (val result = request("getAlbumList2", listOf(
            "type" to type, "size" to size.toString(), "offset" to offset.toString()))) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> try {
                val container = result.value.obj("albumList2") ?: return ApiResult.Failure(ApiError.InvalidResponse)
                val albums = container.getAsJsonArray("album") ?: emptyList()
                ApiResult.Success(albums.map { parseAlbum(it.asJsonObject) })
            } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
        }

    suspend fun album(id: String): ApiResult<AlbumWithSongsDto> =
        when (val result = request("getAlbum", listOf("id" to id))) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> try {
                val albumJson = result.value.obj("album") ?: return ApiResult.Failure(ApiError.InvalidResponse)
                val songs = albumJson.getAsJsonArray("song")?.map { parseSong(it.asJsonObject) } ?: emptyList()
                val album = parseAlbum(albumJson)
                if (album.id != id || album.songCount != songs.size) return ApiResult.Failure(ApiError.InvalidResponse)
                ApiResult.Success(AlbumWithSongsDto(album, songs))
            } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
        }

    suspend fun genres(): ApiResult<List<GenreDto>> = when (val result = request("getGenres")) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            val container = result.value.obj("genres") ?: return ApiResult.Failure(ApiError.InvalidResponse)
            val genres = container.getAsJsonArray("genre") ?: emptyList()
            ApiResult.Success(genres.mapNotNull {
                val genre = it.asJsonObject
                val name = genre.optString("value") ?: return@mapNotNull null
                GenreDto(name, genre.optInt("songCount"), genre.optInt("albumCount"))
            })
        } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
    }

    /**
     * getSimilarSongs2 depends on server-side integrations (for example Last.fm): any failure
     * or empty answer means "continuation not available", never an error for the user.
     */
    suspend fun similarSongs(id: String, count: Int): ApiResult<List<SongDto>> =
        when (val result = request("getSimilarSongs2", listOf("id" to id, "count" to count.toString()))) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> try {
                ApiResult.Success(similarSongsFrom(result.value))
            } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
        }

    /** POST form (formPost extension): repeated songId keep their order; duplicates are preserved. */
    suspend fun createPlaylist(name: String, songIds: List<String>): ApiResult<PlaylistDetailDto> {
        if (name.isBlank()) return ApiResult.Failure(ApiError.InvalidRequest)
        val parameters = mutableListOf("name" to name)
        songIds.forEach { parameters.add("songId" to it) }
        return when (val result = requestPost("createPlaylist", parameters)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> try {
                val root = result.value.obj("playlist") ?: return ApiResult.Failure(ApiError.InvalidResponse)
                val playlist = parsePlaylist(root)
                val entries = root.getAsJsonArray("entry")?.map { parseSong(it.asJsonObject) } ?: emptyList()
                if (playlist.songCount != entries.size) return ApiResult.Failure(ApiError.InvalidResponse)
                ApiResult.Success(PlaylistDetailDto(playlist, entries))
            } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
        }
    }

    /** Rename/comment and/or edit songs. Removals by index are applied before appends. */
    suspend fun updatePlaylist(
        id: String,
        name: String? = null,
        songIdsToAdd: List<String> = emptyList(),
        songIndexesToRemove: List<Int> = emptyList(),
    ): ApiResult<Unit> {
        val parameters = mutableListOf("playlistId" to id)
        name?.let { parameters.add("name" to it) }
        songIndexesToRemove.sorted().forEach { parameters.add("songIndexToRemove" to it.toString()) }
        songIdsToAdd.forEach { parameters.add("songIdToAdd" to it) }
        return when (val result = requestPost("updatePlaylist", parameters)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }
    }

    suspend fun deletePlaylist(id: String): ApiResult<Unit> =
        when (val result = request("deletePlaylist", listOf("id" to id))) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }

    suspend fun star(
        songIds: List<String> = emptyList(),
        albumIds: List<String> = emptyList(),
        artistIds: List<String> = emptyList(),
    ): ApiResult<Unit> = changeStar("star", songIds, albumIds, artistIds)

    suspend fun unstar(
        songIds: List<String> = emptyList(),
        albumIds: List<String> = emptyList(),
        artistIds: List<String> = emptyList(),
    ): ApiResult<Unit> = changeStar("unstar", songIds, albumIds, artistIds)

    private suspend fun changeStar(
        endpoint: String,
        songIds: List<String>,
        albumIds: List<String>,
        artistIds: List<String>,
    ): ApiResult<Unit> {
        if (songIds.isEmpty() && albumIds.isEmpty() && artistIds.isEmpty()) {
            return ApiResult.Failure(ApiError.InvalidRequest)
        }
        val parameters = mutableListOf<Pair<String, String>>()
        songIds.forEach { parameters.add("id" to it) }
        albumIds.forEach { parameters.add("albumId" to it) }
        artistIds.forEach { parameters.add("artistId" to it) }
        return when (val result = request(endpoint, parameters)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }
    }

    /** OpenSubsonic structured lyrics; an empty list means the server has no lyrics for the song. */
    suspend fun lyrics(songId: String): ApiResult<List<StructuredLyricsDto>> =
        when (val result = request("getLyricsBySongId", listOf("id" to songId))) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> try {
                val list = result.value.obj("lyricsList") ?: return ApiResult.Failure(ApiError.InvalidResponse)
                val structured = list.getAsJsonArray("structuredLyrics") ?: emptyList()
                ApiResult.Success(structured.map { parseStructuredLyrics(it.asJsonObject) })
            } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
        }

    /** Playback notification; the server does NOT deduplicate identical id+time resends (verified). */
    suspend fun scrobble(entries: List<Pair<String, Long>>, submission: Boolean = true): ApiResult<Unit> {
        if (entries.isEmpty()) return ApiResult.Failure(ApiError.InvalidRequest)
        val parameters = entries.flatMap { (id, time) -> listOf("id" to id, "time" to time.toString()) }
            .toMutableList()
            .apply { add("submission" to submission.toString()) }
        return when (val result = requestPost("scrobble", parameters)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }
    }


    /** Binary cover art; a JSON error envelope on HTTP 200 is treated as a protocol failure. */
    suspend fun coverArt(coverArtId: String, sizePx: Int?): ApiResult<ByteArray> {
        if (!networkAllowed()) return ApiResult.Failure(ApiError.Unreachable)
        if (coverArtId.isEmpty()) return ApiResult.Failure(ApiError.InvalidRequest)
        val saltValue = salt()
        val url = address.url.newBuilder().addPathSegment("rest").addPathSegment("getCoverArt.view")
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", credentials.token(saltValue))
            .addQueryParameter("s", saltValue)
            .addQueryParameter("v", "1.16.1").addQueryParameter("c", "WalkTape")
            .addQueryParameter("f", "json")
            .addQueryParameter("id", coverArtId)
        sizePx?.let { url.addQueryParameter("size", it.toString()) }
        val call = http.newCall(Request.Builder().url(url.build()).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(ApiResult.Failure(networkError(e)))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        when {
                            !it.isSuccessful -> ApiResult.Failure(ApiError.Http(it.code))
                            else -> try {
                                val contentType = (it.header("Content-Type") ?: "").substringBefore(';').trim()
                                val body = it.body
                                if (body == null || body.contentLength() > MAX_BODY) {
                                    ApiResult.Failure(ApiError.InvalidResponse)
                                } else {
                                    val source = body.source()
                                    source.request(MAX_BODY + 1)
                                    if (source.buffer.size > MAX_BODY || source.buffer.size == 0L) {
                                        ApiResult.Failure(ApiError.InvalidResponse)
                                    } else if (contentType.contains("json")) {
                                        when (val envelope = decode(source.readUtf8())) {
                                            is ApiResult.Failure -> envelope
                                            else -> ApiResult.Failure(ApiError.InvalidResponse)
                                        }
                                    } else if (contentType.startsWith("image/")) {
                                        ApiResult.Success(source.readByteArray())
                                    } else ApiResult.Failure(ApiError.InvalidResponse)
                                }
                            } catch (e: IOException) { ApiResult.Failure(networkError(e)) }
                        }
                    }
                    continuation.resume(result)
                }
            })
        }
    }

    /**
     * Authenticated stream URL for the playback DataSource. The caller must supply a fresh
     * salt for every request (D17). Default format "raw" bypasses server transcoding so the
     * original file is streamed; quality selection belongs to W09-T01.
     */
    fun streamUrl(songId: String, salt: String, format: String? = "raw", maxBitRate: Int? = null): HttpUrl {
        val url = address.url.newBuilder().addPathSegment("rest").addPathSegment("stream.view")
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", credentials.token(salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "WalkTape")
            .addQueryParameter("id", songId)
        format?.let { url.addQueryParameter("format", it) }
        maxBitRate?.let { url.addQueryParameter("maxBitRate", it.toString()) }
        return url.build()
    }

    /**
     * Authenticated original-file URL for downloads (D17: `download` always returns the
     * original and honors Range, verified on the real server). No format parameter.
     */
    fun downloadUrl(songId: String, salt: String): HttpUrl {
        return address.url.newBuilder().addPathSegment("rest").addPathSegment("download.view")
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", credentials.token(salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "WalkTape")
            .addQueryParameter("id", songId)
            .build()
    }

    /** Repeated parameters are preserved for playlist endpoints in later waves. */
    internal suspend fun request(
        endpoint: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ): ApiResult<JsonObject> = execute(endpoint, parameters, asPost = false)

    /**
     * Same contract as [request] but sends parameters as a form-encoded POST body (OpenSubsonic
     * formPost): repeated songId lists of any length stay out of the request URL.
     */
    internal suspend fun requestPost(
        endpoint: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ): ApiResult<JsonObject> = execute(endpoint, parameters, asPost = true)

    private suspend fun execute(
        endpoint: String,
        parameters: List<Pair<String, String>>,
        asPost: Boolean,
    ): ApiResult<JsonObject> {
        if (!networkAllowed()) return ApiResult.Failure(ApiError.Unreachable)
        if (!endpoint.matches(Regex("[a-zA-Z][a-zA-Z0-9]*")) ||
            parameters.any { it.first in RESERVED }) return ApiResult.Failure(ApiError.InvalidRequest)
        val requestSalt = salt()
        if (requestSalt.length < 6) return ApiResult.Failure(ApiError.InvalidRequest)
        val url = address.url.newBuilder().addPathSegment("rest").addPathSegment("$endpoint.view")
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", credentials.token(requestSalt))
            .addQueryParameter("s", requestSalt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "WalkTape")
            .addQueryParameter("f", "json")
        if (!asPost) parameters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        val form = FormBody.Builder()
        parameters.forEach { (key, value) -> form.add(key, value) }
        val request = Request.Builder().url(url.build()).header("Accept", "application/json")
        if (asPost) request.post(form.build())
        val call = http.newCall(request.build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(ApiResult.Failure(networkError(e)))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        if (!it.isSuccessful) ApiResult.Failure(ApiError.Http(it.code))
                        else try {
                            val body = it.body
                            if (body == null || body.contentLength() > MAX_BODY) {
                                ApiResult.Failure(ApiError.InvalidResponse)
                            } else {
                                val source = body.source()
                                source.request(MAX_BODY + 1)
                                if (source.buffer.size > MAX_BODY) ApiResult.Failure(ApiError.InvalidResponse)
                                else decode(source.readUtf8())
                            }
                        } catch (e: IOException) { ApiResult.Failure(networkError(e)) }
                    }
                    continuation.resume(result)
                }
            })
        }
    }

    companion object {
        private const val MAX_BODY = 8L * 1024 * 1024
        private val RESERVED = setOf("u", "p", "t", "s", "v", "c", "f", "apiKey", "callback")

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .eventListener(NetworkTraffic.listener)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            // Redirects can transfer authentication to another host or downgrade TLS.
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        /**
         * Streaming client: no total call timeout, because a track may be read for minutes and
         * the socket can stay idle while playback is paused; ExoPlayer resumes failed range
         * requests on the original (pass-through) stream.
         */
        fun streamingHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .eventListener(NetworkTraffic.listener)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        private fun networkError(e: IOException): ApiError = when (e) {
            is SSLException -> ApiError.Tls
            is SocketTimeoutException -> ApiError.Timeout
            is UnknownHostException, is ConnectException -> ApiError.Unreachable
            else -> if (e is java.io.InterruptedIOException) ApiError.Timeout else ApiError.Network
        }

        /** Pure parsing of a getSimilarSongs2 response body for unit tests; throws on a missing container. */
        internal fun similarSongsFrom(root: JsonObject): List<SongDto> {
            val container = root.obj("similarSongs2") ?: throw IllegalArgumentException()
            return container.getAsJsonArray("song")?.map { parseSong(it.asJsonObject) } ?: emptyList()
        }

        internal fun decode(body: String): ApiResult<JsonObject> = try {
            val reader = JsonReader(StringReader(body)).apply { strictness = Strictness.STRICT }
            val document = JsonParser.parseReader(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) throw IllegalArgumentException()
            val root = document.asJsonObject.getAsJsonObject("subsonic-response")
                ?: throw IllegalArgumentException()
            if (!root.get("version").asString.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
                throw IllegalArgumentException()
            }
            when (root.get("status").asString) {
                "ok" -> if (root.has("error")) ApiResult.Failure(ApiError.InvalidResponse)
                    else ApiResult.Success(root)
                "failed" -> {
                    val code = root.getAsJsonObject("error").get("code").asString.toInt()
                    ApiResult.Failure(ApiError.Protocol(code, ProtocolErrorKind.fromCode(code)))
                }
                else -> ApiResult.Failure(ApiError.InvalidResponse)
            }
        } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
          catch (_: IOException) { ApiResult.Failure(ApiError.InvalidResponse) }
    }
}
