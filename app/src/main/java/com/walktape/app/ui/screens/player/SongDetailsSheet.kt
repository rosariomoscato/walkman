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
    val quality by Graph.playbackQuality.playbackQuality.collectAsStateWithLifecycle()
    val replayGain by Graph.replayGainInfo.collectAsStateWithLifecycle()
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
                    if (playback.songId == songId) {
                        DetailValue(R.string.quality_now_title, qualityLabel(quality?.takeIf { it.songId == songId }))
                        DetailValue(R.string.audio_replaygain, when {
                            replayGain.mode == com.walktape.app.core.settings.ReplayGainMode.OFF ->
                                stringResource(R.string.audio_replaygain_off)
                            !replayGain.hasTags -> stringResource(R.string.audio_replaygain_no_tags)
                            else -> stringResource(R.string.audio_replaygain_applied, replayGain.gainDb)
                        })
                    }
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

/** Honest description of the bytes actually flowing for the current song (W09-T01). */
@Composable
private fun qualityLabel(quality: com.walktape.app.playback.PlaybackQuality?): String {
    if (quality == null) return stringResource(R.string.quality_now_unknown)
    val codec = when (quality.actualMime) {
        "audio/ogg", "audio/opus" -> stringResource(R.string.quality_codec_opus)
        "audio/mpeg" -> stringResource(R.string.quality_codec_mp3)
        "audio/flac" -> stringResource(R.string.quality_codec_flac)
        "audio/mp4" -> stringResource(R.string.quality_codec_aac)
        null -> null
        else -> stringResource(R.string.quality_codec_unknown, quality.actualMime)
    }
    val base = when {
        quality.local -> stringResource(if (quality.localReduced == true)
            R.string.quality_now_local_reduced else R.string.quality_now_local_original)
        quality.requestedReduced && quality.transcodingUnavailable != true -> stringResource(R.string.quality_now_stream_reduced,
            quality.requestedKbps ?: 0)
        else -> stringResource(R.string.quality_now_stream_original)
    }
    val withCodec = if (codec != null && quality.actualBitrate != null)
        stringResource(R.string.quality_codec_label, base, "$codec ${quality.actualBitrate / 1000} kbps")
    else if (codec != null) stringResource(R.string.quality_codec_label, base, codec)
    else base
    return if (quality.transcodingUnavailable == true)
        stringResource(R.string.quality_now_transcoding_unavailable) + " · $withCodec" else withCodec
}
