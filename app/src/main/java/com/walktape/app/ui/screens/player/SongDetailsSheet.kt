package com.walktape.app.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.core.db.SongEntity
import com.walktape.app.core.library.LyricsRepository
import com.walktape.app.ui.screens.library.durationLabel
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongDetailsSheet(profileId: Long, songId: String, onDismiss: () -> Unit) {
    val playback by Graph.playback.state.collectAsStateWithLifecycle()
    var song by remember(profileId, songId) { mutableStateOf<SongEntity?>(null) }
    var lyrics by remember(profileId, songId) { mutableStateOf<LyricsRepository.Result?>(null) }
    var retry by remember(profileId, songId) { mutableIntStateOf(0) }
    LaunchedEffect(profileId, songId, retry) {
        song = Graph.database.libraryDao().songsByIds(profileId, listOf(songId)).firstOrNull()
        lyrics = null
        lyrics = try {
            Graph.lyricsRepository().lyrics(profileId, songId, refresh = retry > 0)
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { LyricsRepository.Result.Unavailable(com.walktape.app.core.net.ApiError.LocalStorage) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val listState = rememberLazyListState()
        val available = lyrics as? LyricsRepository.Result.Available
        val lines = available?.lyrics?.lines.orEmpty()
        val current = if (available?.lyrics?.synced == true && playback.songId == songId)
            lines.indexOfLast { it.startMs != null && it.startMs <= playback.positionMs } else -1
        LaunchedEffect(current) {
            if (current >= 0) listState.animateScrollToItem(current + 2)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp).navigationBarsPadding(),
            state = listState, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.song_details), style = MaterialTheme.typography.titleLarge)
                song?.let { s ->
                    Text(s.title, style = MaterialTheme.typography.titleMedium)
                    DetailValue(R.string.song_details_artist, s.displayArtist ?: s.artist)
                    DetailValue(R.string.song_details_album, s.album)
                    DetailValue(R.string.song_details_year, s.year?.toString())
                    DetailValue(R.string.song_details_genre, s.genre)
                    DetailValue(R.string.song_details_format, s.suffix?.uppercase() ?: s.contentType)
                    DetailValue(R.string.song_details_bitrate, s.bitRate.takeIf { it > 0 }?.let {
                        stringResource(R.string.song_details_bitrate_value, it)
                    })
                    DetailValue(R.string.song_details_duration, durationLabel(s.duration))
                } ?: Text(stringResource(R.string.song_details_unavailable))
            }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.lyrics_title), style = MaterialTheme.typography.titleLarge)
                when (lyrics) {
                    null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    LyricsRepository.Result.None -> Text(stringResource(R.string.lyrics_not_available))
                    is LyricsRepository.Result.Unavailable -> Text(stringResource(R.string.lyrics_error))
                    else -> Unit
                }
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.action_retry)) }
            }
            itemsIndexed(lines) { index, line ->
                val currentLabel = stringResource(R.string.lyrics_current_line)
                Text(line.value, modifier = Modifier.fillMaxWidth().semantics {
                    if (index == current) stateDescription = currentLabel
                }, style = if (index == current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun DetailValue(label: Int, value: String?) {
    if (!value.isNullOrBlank()) Text("${stringResource(label)}: $value")
}
