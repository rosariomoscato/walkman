package com.walktape.app.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.walktape.app.R
import com.walktape.app.playback.CassetteLabel
import com.walktape.app.playback.PlaybackSnapshot
import com.walktape.app.playback.cassetteLabel
import com.walktape.app.ui.components.playbackStatus
import com.walktape.app.ui.components.timeLabel
import com.walktape.app.ui.screens.library.CoverArtById
import com.walktape.app.ui.theme.CassetteShellColor
import com.walktape.app.ui.theme.CassetteWindowColor
import com.walktape.app.ui.theme.ReelHubColor
import com.walktape.app.ui.theme.TapeColor
import com.walktape.app.ui.theme.WtCream
import com.walktape.app.ui.theme.WtInk
import com.walktape.app.ui.theme.WtInkSoft
import com.walktape.app.ui.theme.WtOrange
import com.walktape.app.ui.theme.WtOrangeDim
import com.walktape.app.ui.theme.WtSteel
import com.walktape.app.ui.theme.WtSteelDark
import com.walktape.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * The cassette window of the TPS-L2 player: dynamic label (album / playlist / Mixtape),
 * real cover thumbnail, reels that turn only during actual playback and a tape counter.
 */
@Composable
internal fun CassetteWindow(
    state: PlaybackSnapshot,
    profileId: Long?,
    onOpenCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = cassetteLabel(state.queue, state.queueOriginName, state.mixtapeName)
    Column(
        modifier
            .fillMaxWidth()
            .background(CassetteWindowColor, RoundedCornerShape(12.dp))
            .border(1.dp, WtSteelDark.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        CassetteBody(state, profileId, label, onOpenCover, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        CounterRow(state)
    }
}

@Composable
private fun CassetteBody(
    state: PlaybackSnapshot,
    profileId: Long?,
    label: CassetteLabel?,
    onOpenCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = state.title.ifBlank { "" }
    val cassetteText = if (state.hasMedia) {
        stringResource(R.string.cassette_description, title.ifBlank {
            stringResource(R.string.playback_unknown_title)
        }, state.artist)
    } else stringResource(R.string.cassette_empty)
    val labelText = when (label?.kind) {
        CassetteLabel.Kind.MIXTAPE ->
            label.text ?: stringResource(R.string.cassette_label_mixtape)
        CassetteLabel.Kind.ALBUM, CassetteLabel.Kind.PLAYLIST -> label.text.orEmpty()
        null -> ""
    }
    Box(
        modifier
            .background(CassetteShellColor, RoundedCornerShape(6.dp)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverThumb(state, profileId, onOpenCover)
                Column(
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { contentDescription = cassetteText }
                ) {
                    if (labelText.isNotEmpty()) {
                        Text(
                            labelText.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = WtOrangeDim,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.hasMedia) {
                        Text(title.ifBlank { stringResource(R.string.playback_unknown_title) },
                            style = MaterialTheme.typography.titleMedium, color = WtInk,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.artist.isNotBlank()) Text(state.artist,
                            style = MaterialTheme.typography.bodySmall, color = WtInkSoft,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Reels(state, Modifier.height(100.dp).fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun CoverThumb(
    state: PlaybackSnapshot,
    profileId: Long?,
    onOpenCover: () -> Unit,
) {
    val coverLabel = stringResource(R.string.cover_enlarge)
    val hasCover = state.coverArtId != null
    Box(
        Modifier
            .size(56.dp)
            .background(WtCream, RoundedCornerShape(4.dp))
            .border(1.dp, WtSteelDark, RoundedCornerShape(4.dp))
            .semantics {
                contentDescription = coverLabel
                role = androidx.compose.ui.semantics.Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = hasCover,
                onClickLabel = coverLabel,
                onClick = onOpenCover
            )
    ) {
        CoverArtById(profileId, state.coverArtId, Modifier.size(56.dp))
    }
}

/** Reels turn only while the session really plays; they freeze on pause and reduced motion. */
@Composable
private fun Reels(state: PlaybackSnapshot, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isPlaying, reducedMotion) {
        if (state.isPlaying && !reducedMotion) {
            // The composition context provides the frame clock for LaunchedEffect coroutines.
            val clock = coroutineContext[MonotonicFrameClock] ?: return@LaunchedEffect
            var last = 0L
            while (isActive) {
                clock.withFrameNanos { now ->
                    if (last != 0L) {
                        val elapsedMs = (now - last) / 1_000_000f
                        angle = (angle + elapsedMs * DEGREES_PER_MS) % 360f
                    }
                    last = now
                }
            }
        }
    }
    val fraction = state.durationMs?.takeIf { it > 0 }
        ?.let { (state.positionMs.toFloat() / it).coerceIn(0.03f, 0.97f) } ?: 0.5f
    Canvas(modifier) {
        val centerY = size.height / 2f
        val maxR = size.height * 0.42f
        val minR = maxR * 0.40f
        val hubR = maxR * 0.30f
        val leftX = size.width * 0.27f
        val rightX = size.width * 0.73f
        val leftR = minR + (maxR - minR) * (1f - fraction)
        val rightR = minR + (maxR - minR) * fraction
        drawLine(
            TapeColor,
            Offset(leftX + leftR, centerY),
            Offset(rightX - rightR, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawReel(leftX, centerY, leftR, hubR, angle)
        drawReel(rightX, centerY, rightR, hubR, angle)
    }
}

private fun DrawScope.drawReel(centerX: Float, centerY: Float, tapeRadius: Float, hubRadius: Float, angleDeg: Float) {
    drawCircle(TapeColor, radius = tapeRadius, center = Offset(centerX, centerY))
    drawCircle(ReelHubColor, radius = hubRadius, center = Offset(centerX, centerY))
    rotate(angleDeg, Offset(centerX, centerY)) {
        repeat(6) { tooth ->
            val rad = Math.toRadians(tooth * 60.0)
            val inner = hubRadius * 0.30f
            val outer = hubRadius * 0.85f
            drawLine(
                WtSteelDark,
                Offset(centerX + (inner * cos(rad)).toFloat(), centerY + (inner * sin(rad)).toFloat()),
                Offset(centerX + (outer * cos(rad)).toFloat(), centerY + (outer * sin(rad)).toFloat()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CounterRow(state: PlaybackSnapshot) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (state.hasMedia) timeLabel(state.positionMs) else "--:--",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = WtSteel
        )
        Text(
            playbackStatus(state),
            style = MaterialTheme.typography.labelSmall,
            color = if (state.failure != null) WtOrange else WtSteelDark,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            state.durationMs?.let(::timeLabel) ?: "--:--",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = WtSteel
        )
    }
    if (state.buffering) {
        LinearProgressIndicator(
            Modifier.fillMaxWidth().height(2.dp),
            color = WtOrange,
            trackColor = WtSteelDark.copy(alpha = 0.4f)
        )
    }
}

private const val DEGREES_PER_MS = 0.045f
