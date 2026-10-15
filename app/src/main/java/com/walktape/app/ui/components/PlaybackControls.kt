package com.walktape.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.playback.PlaybackFailure
import com.walktape.app.playback.PlaybackSnapshot

/**
 * Mini transport bar above the navigation: the expand affordance opens the full TPS-L2 player
 * route, which hosts seek, volume, timer, suggestions and the queue.
 */
@Composable
fun PlaybackControls(onOpenPlayer: () -> Unit) {
    val state by Graph.playback.state.collectAsStateWithLifecycle()
    val failedProfiles by Graph.queues.failedProfiles.collectAsStateWithLifecycle()
    val profiles by Graph.profiles.profiles.collectAsStateWithLifecycle(emptyList())
    if (profiles.firstOrNull { it.isActive }?.id in failedProfiles) {
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Text(stringResource(R.string.queue_storage_error), Modifier.fillMaxWidth().padding(12.dp))
        }
    }
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
    Surface(modifier = Modifier.fillMaxWidth().windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column {
            HorizontalDivider()
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
                if (stacked) Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Metadata(state, Modifier.weight(1f))
                        ExpandButton(onOpenPlayer)
                    }
                    Transport(state, Modifier.align(Alignment.CenterHorizontally))
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Metadata(state, Modifier.weight(1f))
                    Transport(state)
                    ExpandButton(onOpenPlayer)
                }
            }
            if (state.buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
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
        Icon(Icons.Default.ExpandMore, stringResource(R.string.player_open))
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
