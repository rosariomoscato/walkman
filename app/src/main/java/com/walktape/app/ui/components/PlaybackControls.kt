package com.walktape.app.ui.components

import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.playback.PlaybackFailure
import com.walktape.app.playback.PlaybackSnapshot
import kotlin.math.roundToInt
import java.util.Locale

/** Functional controls shared by the shell; the TPS-L2 player is built in wave 06. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackControls() {
    val state by Graph.playback.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.hasMedia) { if (!state.hasMedia) expanded = false }
    if (!state.hasMedia) {
        if (state.failure != null) Surface {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.playback_error), Modifier.weight(1f))
                if (state.failure == PlaybackFailure.CONNECTION) TextButton(onClick = Graph.playback::connect) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
        return
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column {
            HorizontalDivider()
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
                if (stacked) Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Metadata(state, Modifier.weight(1f))
                        ExpandButton { expanded = true }
                    }
                    Transport(state, Modifier.align(Alignment.CenterHorizontally))
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Metadata(state, Modifier.weight(1f))
                    Transport(state)
                    ExpandButton { expanded = true }
                }
            }
            if (state.buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (expanded) ModalBottomSheet(
        onDismissRequest = { expanded = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val previous = window?.volumeControlStream
            window?.volumeControlStream = AudioManager.STREAM_MUSIC
            onDispose { if (previous != null) window.volumeControlStream = previous }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.playback_controls), style = MaterialTheme.typography.titleLarge)
            Text(state.title.ifBlank { stringResource(R.string.playback_unknown_title) },
                style = MaterialTheme.typography.titleMedium)
            if (state.artist.isNotBlank()) Text(state.artist)
            if (state.album.isNotBlank()) Text(state.album, style = MaterialTheme.typography.bodySmall)
            Text(status(state), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SeekControl(state)
            Transport(state, Modifier.align(Alignment.CenterHorizontally))
            val repeatLabel = stringResource(when (state.repeatMode) {
                1 -> R.string.playback_repeat_one
                2 -> R.string.playback_repeat_all
                else -> R.string.playback_repeat_off
            })
            TextButton(onClick = Graph.playback::cycleRepeat, enabled = state.canRepeat,
                modifier = Modifier.fillMaxWidth()) {
                Icon(if (state.repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = null)
                Text(repeatLabel, Modifier.padding(start = 8.dp))
            }
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
    }
}

@Composable
private fun Metadata(state: PlaybackSnapshot, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(state.title.ifBlank { stringResource(R.string.playback_unknown_title) },
            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (state.failure != null) stringResource(R.string.playback_error) else state.artist,
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExpandButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(Icons.Default.ExpandMore, stringResource(R.string.playback_open_controls))
    }
}

@Composable
private fun Transport(state: PlaybackSnapshot, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = Graph.playback::previous, enabled = state.canPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, stringResource(R.string.playback_previous))
        }
        val pause = state.playWhenReady && !state.ended && state.failure != PlaybackFailure.PLAYBACK
        FilledIconButton(onClick = Graph.playback::togglePlayPause, enabled = state.canPlayPause,
            modifier = Modifier.size(56.dp)) {
            Icon(if (pause) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (pause) R.string.playback_pause else R.string.playback_play))
        }
        IconButton(onClick = Graph.playback::next, enabled = state.canNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, stringResource(R.string.playback_next))
        }
    }
}

@Composable
private fun SeekControl(state: PlaybackSnapshot) {
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
private fun status(state: PlaybackSnapshot): String = stringResource(when {
    state.failure != null -> R.string.playback_error
    state.buffering -> R.string.playback_buffering
    state.ended -> R.string.playback_ended
    state.isPlaying -> R.string.playback_playing
    state.playWhenReady -> R.string.playback_waiting
    else -> R.string.playback_paused
})

private fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
