package com.walktape.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import com.walktape.app.ui.theme.KeyOrangeBrush
import com.walktape.app.ui.theme.KeySteelBrush
import com.walktape.app.ui.theme.WtBlueNight
import com.walktape.app.ui.theme.WtInk
import com.walktape.app.ui.theme.WtOrange
import com.walktape.app.ui.theme.WtSteelDark
import com.walktape.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Mechanical key of the TPS-L2 chassis: 56dp+ target, gradient keycap, no ripple (the feedback
 * is haptic/audible and optional). [holdTick] fires repeatedly while the key stays pressed after
 * the long-press threshold, with an escalating [step] index; a hold never triggers [onClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MechanicalKey(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    holdTick: ((step: Int) -> Unit)? = null,
    holdEnabled: Boolean = false,
    emphasized: Boolean = false,
    engaged: Boolean = false,
    enabled: Boolean = true,
    width: Dp = 56.dp,
    height: Dp = 56.dp,
    feedback: KeyFeedback? = null,
    stateDescriptionText: String? = null,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val animatedScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        label = "keyScale"
    )
    val scale = if (reducedMotion) {
        if (pressed && enabled) 0.96f else 1f
    } else animatedScale

    val currentHoldTick by rememberUpdatedState(holdTick)
    val currentFeedback by rememberUpdatedState(feedback)

    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release || interaction is PressInteraction.Cancel) holding = false
        }
    }
    LaunchedEffect(holding, enabled, holdEnabled, lifecycleState) {
        if (holding && enabled && holdEnabled && lifecycleState == Lifecycle.State.RESUMED && currentHoldTick != null) {
            currentFeedback?.holdStart()
            var step = 0
            while (isActive) {
                currentFeedback?.tick()
                currentHoldTick?.invoke(step++)
                delay(HOLD_TICK_MS)
            }
        }
    }

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .scale(scale)
            .background(if (emphasized) KeyOrangeBrush else KeySteelBrush, shape)
            .border(BorderStroke(1.5.dp, if (engaged) WtOrange else WtSteelDark), shape)
            .semantics {
                role = Role.Button
                stateDescriptionText?.let { stateDescription = it }
                if (accessibilityActions.isNotEmpty()) customActions = accessibilityActions
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    feedback?.press()
                    onClick()
                },
                onLongClick = if (holdTick != null || onLongClick != null) ({
                    if (holdTick == null || holdEnabled) {
                        holding = true
                        feedback?.press()
                        onLongClick?.invoke()
                    }
                }) else null
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (emphasized) WtBlueNight else WtInk
        )
        if (engaged) {
            // Engaged indicator: orange dot on a dark well, readable on the steel keycap.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .background(WtInk, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(5.dp).background(WtOrange, RoundedCornerShape(50)))
            }
        }
    }
}

private const val HOLD_TICK_MS = 350L

/** Text keycap for in-app actions that reuse the chassis material outside the player. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MechanicalTextKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    feedback: KeyFeedback? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val animatedScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        label = "textKeyScale"
    )
    val scale = if (reducedMotion) {
        if (pressed && enabled) 0.96f else 1f
    } else animatedScale
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (emphasized) WtBlueNight else WtInk,
        modifier = modifier
            .scale(scale)
            .background(if (emphasized) KeyOrangeBrush else KeySteelBrush, shape)
            .border(BorderStroke(1.5.dp, WtSteelDark), shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    feedback?.press()
                    onClick()
                }
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}
