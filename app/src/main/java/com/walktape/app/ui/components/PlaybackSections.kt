package com.walktape.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.playback.ContinuationSuggestion
import com.walktape.app.playback.PlaybackFailure
import com.walktape.app.playback.PlaybackSnapshot
import com.walktape.app.playback.SleepTimer
import java.util.Locale
import kotlin.math.roundToInt

/** Functional sections shared by the player screen; all wired to the real session bridge. */

@Composable
internal fun playbackStatus(state: PlaybackSnapshot): String =
    stringResource(when {
        state.failure != null -> R.string.playback_error
        state.buffering -> R.string.playback_buffering
        state.ended -> R.string.playback_ended
        state.isPlaying -> R.string.playback_playing
        state.playWhenReady -> R.string.playback_waiting
        else -> R.string.playback_paused
    })

@Composable
internal fun SeekControl(state: PlaybackSnapshot) {
    var dragged by remember(state.itemKey) { mutableStateOf<Float?>(null) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    val duration = state.durationMs ?: 0L
    val end = duration.toFloat().coerceAtLeast(1f)
    val value = (dragged ?: state.positionMs.toFloat()).coerceIn(0f, end)
    val label = stringResource(R.string.playback_seek)
    val elapsed = timeLabel(if (dragged != null) value.toLong() else state.positionMs)
    val total = state.durationMs?.let(::timeLabel) ?: "--:--"
    val positionLabel = stringResource(R.string.playback_seek_position, elapsed, total)
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = label
            stateDescription = positionLabel
            progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..end)
            if (!state.canSeek) disabled()
            if (state.canSeek) setProgress { requested ->
                dragged = null
                Graph.playback.seekTo(requested.coerceIn(0f, end).toLong(), state.itemKey)
                true
            }
        }) {
        Slider(value = value, onValueChange = { dragged = it; dragKey = state.itemKey },
            onValueChangeFinished = {
                dragged?.let { Graph.playback.seekTo(it.toLong(), dragKey) }
                dragged = null
            }, valueRange = 0f..end, enabled = state.canSeek, modifier = Modifier.fillMaxWidth())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(elapsed, style = MaterialTheme.typography.bodySmall)
        Text(total, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun VolumeSection(state: PlaybackSnapshot) {
    Text(stringResource(R.string.playback_volume, state.volume, state.maxVolume))
    val volumeLabel = stringResource(R.string.playback_volume_description)
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = volumeLabel
            progressBarRangeInfo = ProgressBarRangeInfo(state.volume.toFloat(), 0f..state.maxVolume.toFloat(),
                (state.maxVolume - 1).coerceAtLeast(0))
            if (state.volumeFixed) disabled()
            if (!state.volumeFixed) setProgress { requested ->
                Graph.playback.setSystemVolume(requested.roundToInt())
                true
            }
        }) {
        Slider(value = state.volume.toFloat().coerceIn(0f, state.maxVolume.toFloat()),
            onValueChange = { Graph.playback.setSystemVolume(it.roundToInt()) },
            valueRange = 0f..state.maxVolume.toFloat(), steps = (state.maxVolume - 1).coerceAtLeast(0),
            enabled = !state.volumeFixed, modifier = Modifier.fillMaxWidth())
    }
    if (state.volumeFixed) Text(stringResource(R.string.playback_volume_fixed))
}

@Composable
internal fun TimerSection(playback: PlaybackSnapshot) {
    val timer by Graph.playbackTimer.state.collectAsStateWithLifecycle()
    Text(stringResource(R.string.playback_timer_title), style = MaterialTheme.typography.titleSmall)
    if (timer.armed) {
        val active = when (timer.mode) {
            SleepTimer.Mode.DURATION -> stringResource(R.string.playback_timer_remaining,
                timeLabel(timer.remainingMs ?: 0))
            SleepTimer.Mode.TRACK -> stringResource(R.string.playback_timer_active_track)
            SleepTimer.Mode.ALBUM -> stringResource(R.string.playback_timer_active_album)
            SleepTimer.Mode.QUEUE -> stringResource(R.string.playback_timer_active_queue)
            null -> ""
        }
        Text(active, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = Graph.playbackTimer::cancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.playback_timer_off))
        }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 60, 90).forEach { minutes ->
            TextButton(onClick = { Graph.playbackTimer.arm(SleepTimer.Mode.DURATION, minutes * 60_000L) }) {
                Text(stringResource(R.string.playback_timer_minutes, minutes))
            }
        }
        TextButton(onClick = { Graph.playbackTimer.arm(SleepTimer.Mode.TRACK) }) {
            Text(stringResource(R.string.playback_timer_track))
        }
        val hasAlbum = !playback.albumId.isNullOrBlank()
        TextButton(onClick = { Graph.playbackTimer.arm(SleepTimer.Mode.ALBUM) }, enabled = hasAlbum) {
            Text(stringResource(R.string.playback_timer_album))
        }
        TextButton(onClick = { Graph.playbackTimer.arm(SleepTimer.Mode.QUEUE) }) {
            Text(stringResource(R.string.playback_timer_queue))
        }
    }
}

@Composable
internal fun SuggestionSection() {
    val suggestion by Graph.suggestion.state.collectAsStateWithLifecycle()
    val available = suggestion as? ContinuationSuggestion.State.Available ?: return
    TextButton(onClick = Graph.playback::applyContinuationSuggestion, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
        Text(stringResource(R.string.playback_suggestion_continue, available.items.size),
            Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun NowPlayingText(state: PlaybackSnapshot) {
    Column {
        Text(state.title.ifBlank { stringResource(R.string.playback_unknown_title) },
            style = MaterialTheme.typography.titleLarge)
        if (state.artist.isNotBlank()) Text(state.artist, style = MaterialTheme.typography.bodyMedium)
        if (state.album.isNotBlank()) Text(state.album,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(playbackStatus(state), style = MaterialTheme.typography.bodySmall,
            color = if (state.failure != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
