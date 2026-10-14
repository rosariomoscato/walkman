package com.walktape.app.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.walktape.app.Graph
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.SongDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optional continuation at the end of a queue: asks the profile's server for songs similar
 * to the last played one. A disabled setting, a server without the integration or an empty
 * answer simply leaves no suggestion. Items carry logical stream URIs only, never secrets.
 */
class ContinuationSuggestion {
    sealed interface State {
        data object Idle : State
        data object Unavailable : State
        data class Available(val items: List<MediaItem>, val sourceTitle: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow<State>(State.Idle)
    val state = mutable.asStateFlow()
    private var fetchJob: Job? = null

    fun evaluate(client: OpenSubsonicClient?, profileId: Long, lastSongId: String?, lastTitle: String?) {
        fetchJob?.cancel()
        if (client == null || lastSongId.isNullOrBlank()) {
            mutable.value = State.Idle
            return
        }
        fetchJob = scope.launch {
            if (!Graph.settingsRepository.settings.first().suggestionsEnabled) {
                mutable.value = State.Idle
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                client.similarSongs(lastSongId, SuggestionSelection.REQUEST_COUNT)
            }
            mutable.value = when (result) {
                is ApiResult.Failure -> State.Unavailable
                is ApiResult.Success -> {
                    val songs = SuggestionSelection.candidates(result.value, lastSongId)
                    if (songs.isEmpty()) State.Unavailable
                    else State.Available(songs.map { it.toSuggestionItem(profileId) }, lastTitle.orEmpty())
                }
            }
        }
    }

    fun consumed() {
        mutable.value = State.Idle
    }

    fun reset() {
        fetchJob?.cancel()
        fetchJob = null
        mutable.value = State.Idle
    }
}

internal fun SongDto.toSuggestionItem(profileId: Long): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(SubsonicStream.uri(profileId, id).toUri())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(displayArtist ?: artist)
            .setAlbumTitle(album)
            .setDurationMs(duration * 1000L)
            .build()
    )
    .build()
