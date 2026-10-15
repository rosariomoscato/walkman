package com.walktape.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.playback.PlaybackSnapshot
import com.walktape.app.ui.components.EmptyState
import com.walktape.app.ui.components.KeyFeedback
import com.walktape.app.ui.components.MechanicalKey
import com.walktape.app.ui.components.NowPlayingText
import com.walktape.app.ui.components.QueueListContent
import com.walktape.app.ui.components.QueueSheet
import com.walktape.app.ui.components.SeekControl
import com.walktape.app.ui.components.SuggestionSection
import com.walktape.app.ui.components.TimerSection
import com.walktape.app.ui.components.VolumeSection
import com.walktape.app.ui.components.rememberKeyFeedback
import com.walktape.app.ui.screens.library.CoverArtById
import com.walktape.app.ui.theme.ChassisBodyBrush
import com.walktape.app.ui.theme.ChassisSteelBrush
import com.walktape.app.ui.theme.WtBlueNight
import com.walktape.app.ui.theme.WtInk
import com.walktape.app.ui.theme.WtOrange
import com.walktape.app.ui.theme.WtSteel
import com.walktape.app.ui.theme.WtSteelDark

/**
 * TPS-L2 player: chassis with cassette and mechanical keys on the real W05 engine, plus the
 * functional sections (seek, volume, timer, suggestions) and the queue. Compact/medium widths
 * scroll in one column and open the queue as a sheet; expanded widths show the queue pane.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val state by Graph.playback.state.collectAsStateWithLifecycle()
    val feedback = rememberKeyFeedback()
    val profiles by Graph.profiles.profiles.collectAsStateWithLifecycle(emptyList())
    val profileId = profiles.firstOrNull { it.isActive }?.id
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var coverOpen by remember { mutableStateOf(false) }

    // Long-press scrub: a cumulative target anchored to the item key captured at hold start.
    // If the track/profile changes under the gesture, the bridge rejects the stale key.
    val scrub = remember { mutableStateOf<ScrubSession?>(null) }
    val rewindTick: (Int) -> Unit = { step -> scrubTick(scrub, step, -1) }
    val forwardTick: (Int) -> Unit = { step -> scrubTick(scrub, step, +1) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    PlayerHeader(onBack)
                    Spacer(Modifier.height(12.dp))
                    Chassis(state, profileId, feedback, { coverOpen = true }, onQueue = {}, showQueueKey = false,
                        rewindTick, forwardTick)
                    Spacer(Modifier.height(16.dp))
                    PlayerSections(state)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .padding(16.dp)
                ) {
                    QueueListContent(Modifier.weight(1f), maxListHeight = null)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                PlayerHeader(onBack)
                Spacer(Modifier.height(12.dp))
                Chassis(state, profileId, feedback, { coverOpen = true }, onQueue = { queueOpen = true },
                    showQueueKey = true, rewindTick, forwardTick)
                Spacer(Modifier.height(16.dp))
                PlayerSections(state)
                if (!state.hasMedia) {
                    EmptyState(
                        icon = Icons.Filled.MusicNote,
                        title = stringResource(R.string.player_empty_title),
                        body = stringResource(R.string.player_empty_body),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    val openCoverArtId = state.coverArtId
    if (queueOpen && !state.queue.isEmpty()) QueueSheet(onDismiss = { queueOpen = false })
    LaunchedEffect(state.hasMedia) { if (!state.hasMedia) queueOpen = false }
    if (coverOpen && openCoverArtId != null) {
        CoverDialog(coverArtId = openCoverArtId, profileId = profileId, onDismiss = { coverOpen = false })
    }
}

internal class ScrubSession(val key: String?, val duration: Long?, var target: Long)

/** Escalating steps: 5s for precision, growing for long traversals. */
private fun stepMs(step: Int): Long = when {
    step < 3 -> 5_000L
    step < 6 -> 10_000L
    else -> 15_000L
}

private fun scrubTick(scrub: MutableState<ScrubSession?>, step: Int, direction: Int) {
    val current = Graph.playback.state.value
    var session = scrub.value
    if (step == 0) {
        if (current.itemKey == null || !current.canSeek) return
        session = ScrubSession(current.itemKey, current.durationMs, current.positionMs)
        scrub.value = session
    }
    if (session == null) return
    // A gesture in progress never applies to a track or profile that changed underneath.
    if (session.key != current.itemKey || !current.canSeek) {
        return
    }
    session.target = (session.target + direction * stepMs(step))
        .coerceIn(0, session.duration ?: Long.MAX_VALUE)
    Graph.playback.seekTo(session.target, session.key)
}

@Composable
private fun PlayerHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
        }
        Text(
            stringResource(R.string.player_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun Chassis(
    state: PlaybackSnapshot,
    profileId: Long?,
    feedback: KeyFeedback,
    onOpenCover: () -> Unit,
    onQueue: () -> Unit,
    showQueueKey: Boolean,
    rewindTick: (Int) -> Unit,
    forwardTick: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .background(ChassisBodyBrush, RoundedCornerShape(20.dp))
            .border(1.5.dp, WtSteelDark.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
    ) {
        SteelHeader()
        CassetteWindow(state, profileId, onOpenCover, Modifier.padding(16.dp))
        key(state.itemKey) {
            KeysPanel(state, feedback, onQueue, showQueueKey, rewindTick, forwardTick,
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun SteelHeader() {
    Row(
        Modifier.fillMaxWidth().height(46.dp).background(ChassisSteelBrush).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "WalkTape",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic
            ),
            color = WtInk
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(10.dp).background(WtOrange, RoundedCornerShape(2.dp)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeysPanel(
    state: PlaybackSnapshot,
    feedback: KeyFeedback,
    onQueue: () -> Unit,
    showQueueKey: Boolean,
    rewindTick: (Int) -> Unit,
    forwardTick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrubBackLabel = stringResource(R.string.player_scrub_back)
    val scrubForwardLabel = stringResource(R.string.player_scrub_forward)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MechanicalKey(
                label = stringResource(R.string.playback_previous),
                icon = Icons.Filled.FastRewind,
                onClick = Graph.playback::previous,
                holdTick = rewindTick,
                holdEnabled = state.canSeek,
                enabled = state.canPrevious,
                width = 64.dp,
                feedback = feedback,
                accessibilityActions = listOf(CustomAccessibilityAction(scrubBackLabel) {
                    scrubTickAnchorless(-10_000L); true
                })
            )
            MechanicalKey(
                label = stringResource(if (state.playWhenReady && !state.ended) R.string.playback_pause else R.string.playback_play),
                icon = if (state.playWhenReady && !state.ended) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onClick = Graph.playback::togglePlayPause,
                emphasized = true,
                enabled = state.canPlayPause,
                width = 84.dp,
                height = 60.dp,
                feedback = feedback
            )
            MechanicalKey(
                label = stringResource(R.string.playback_next),
                icon = Icons.Filled.FastForward,
                onClick = Graph.playback::next,
                holdTick = forwardTick,
                holdEnabled = state.canSeek,
                enabled = state.canNext,
                width = 64.dp,
                feedback = feedback,
                accessibilityActions = listOf(CustomAccessibilityAction(scrubForwardLabel) {
                    scrubTickAnchorless(+10_000L); true
                })
            )
            MechanicalKey(
                label = stringResource(R.string.player_stop),
                icon = Icons.Filled.Stop,
                onClick = { if (state.playWhenReady) Graph.playback.togglePlayPause() },
                enabled = state.playWhenReady && state.canPlayPause,
                width = 64.dp,
                feedback = feedback
            )
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val shuffleLabel = stringResource(if (state.shuffleOn) R.string.queue_shuffle_off else R.string.queue_shuffle_on)
            MechanicalKey(
                label = shuffleLabel,
                icon = Icons.Filled.Shuffle,
                onClick = Graph.playback::toggleShuffle,
                engaged = state.shuffleOn,
                enabled = state.canShuffle,
                feedback = feedback
            )
            val repeatLabel = stringResource(when (state.repeatMode) {
                1 -> R.string.playback_repeat_one
                2 -> R.string.playback_repeat_all
                else -> R.string.playback_repeat_off
            })
            MechanicalKey(
                label = repeatLabel,
                icon = if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                onClick = Graph.playback::cycleRepeat,
                engaged = state.repeatMode != 0,
                enabled = state.canRepeat,
                feedback = feedback
            )
            if (showQueueKey) {
                MechanicalKey(
                    label = stringResource(R.string.queue_open, state.queue.size),
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = onQueue,
                    enabled = state.queue.isNotEmpty(),
                    feedback = feedback
                )
            }
            MechanicalKey(
                label = stringResource(R.string.player_volume_down),
                icon = Icons.Filled.VolumeDown,
                onClick = { Graph.playback.setSystemVolume((state.volume - 1).coerceAtLeast(0)) },
                enabled = !state.volumeFixed,
                feedback = feedback
            )
            MechanicalKey(
                label = stringResource(R.string.player_volume_up),
                icon = Icons.Filled.VolumeUp,
                onClick = { Graph.playback.setSystemVolume((state.volume + 1).coerceAtMost(state.maxVolume)) },
                enabled = !state.volumeFixed,
                feedback = feedback
            )
        }
    }
}

/** Accessibility shortcut: a single precise ±10s nudge on the current track. */
private fun scrubTickAnchorless(delta: Long) {
    val current = Graph.playback.state.value
    if (current.itemKey == null || !current.canSeek) return
    val duration = current.durationMs ?: return
    Graph.playback.seekTo((current.positionMs + delta).coerceIn(0, duration), current.itemKey)
}

@Composable
private fun PlayerSections(state: PlaybackSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.hasMedia) {
            NowPlayingText(state)
            SeekControl(state)
            VolumeSection(state)
            HorizontalDivider()
            TimerSection(state)
            if (state.ended) SuggestionSection()
        }
    }
}

@Composable
private fun CoverDialog(coverArtId: String, profileId: Long?, onDismiss: () -> Unit) {
    val closeLabel = stringResource(R.string.cover_dialog_close)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC101A30))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                    onClickLabel = closeLabel
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WtBlueNight,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                CoverArtById(
                    profileId, coverArtId,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    ContentScale.Fit
                )
            }
            Text(
                closeLabel,
                color = WtSteel,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            )
        }
    }
}
