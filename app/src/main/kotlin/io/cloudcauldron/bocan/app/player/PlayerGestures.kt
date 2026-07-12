@file:Suppress("TooManyFunctions")

package io.cloudcauldron.bocan.app.player

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * The gesture surface for Now Playing. Horizontal swipes change tracks with the pager
 * convention (a leftward swipe advances to the next track, rightward goes back), an upward
 * swipe opens the song details sheet, and a downward swipe dismisses the player. One axis
 * is locked on the initial drag so diagonal drags never fire both machines.
 *
 * The anchor decisions (offset plus velocity to a target, end-of-queue clamping, and the
 * rubber-band beyond a missing neighbor) are pure functions below, unit tested off any
 * Compose or device dependency. The modifier is the thin glue that drives them from the
 * finger and animates the settle (a snap under reduced motion).
 */

/** The three horizontal anchors: the previous track, the settled centre, the next track. */
enum class HorizontalAnchor { Previous, Settled, Next }

/** The three vertical anchors: reveal details (up), the settled centre, dismiss (down). */
enum class VerticalAnchor { Details, Settled, Dismiss }

/** The axis a gesture locks to once it crosses touch slop. */
enum class GestureAxis { Horizontal, Vertical }

/** Commit thresholds and resistances shared by the pure logic and the modifier. */
object PlayerGestureThresholds {
    /** Fraction of the card width past which a horizontal release commits to a track change. */
    const val COMMIT_FRACTION = 0.4f

    /**
     * Absolute distance (dp) past which a vertical release commits. Fixed rather than a
     * fraction of the surface: the gesture surface is the tall artwork block, so a fraction
     * of its height would be an unreachable swipe.
     */
    const val VERTICAL_COMMIT_DP = 96f

    /** Velocity (pixels per second) past which a fling commits regardless of distance. */
    const val FLING_VELOCITY = 1_000f

    /** How far a drag toward a missing neighbor can stretch, as a fraction of the card width. */
    const val RUBBER_BAND_RESISTANCE = 0.3f
}

/**
 * Which axis a drag belongs to, from its first over-slop delta. Ties break to horizontal:
 * track changes are the higher-traffic gesture and horizontal muscle memory should win.
 */
fun dominantAxis(dx: Float, dy: Float): GestureAxis = if (abs(dx) >= abs(dy)) GestureAxis.Horizontal else GestureAxis.Vertical

/**
 * The horizontal target for a release, using the pager convention: a leftward drag or fling
 * (finger moving right to left, a negative offset) advances to the next track; a rightward
 * drag goes to the previous. A release short of the distance threshold and below the fling
 * velocity settles back. When the intended neighbor is null (an end of the queue) the target
 * clamps to settled, which is the caller's cue to rubber-band.
 */
fun resolveHorizontalTarget(offsetPx: Float, widthPx: Float, velocityPx: Float, hasPrevious: Boolean, hasNext: Boolean): HorizontalAnchor {
    if (!isCommitted(offsetPx, widthPx * PlayerGestureThresholds.COMMIT_FRACTION, velocityPx)) return HorizontalAnchor.Settled
    return if (commitDirection(offsetPx, velocityPx) < 0f) {
        if (hasNext) HorizontalAnchor.Next else HorizontalAnchor.Settled
    } else {
        if (hasPrevious) HorizontalAnchor.Previous else HorizontalAnchor.Settled
    }
}

/**
 * The vertical target for a release. A downward drag or fling (positive offset) dismisses;
 * upward (negative) opens details. [commitDistancePx] is an absolute distance, not a fraction
 * of the tall surface. A release short of it and below the fling velocity settles back.
 */
fun resolveVerticalTarget(offsetPx: Float, commitDistancePx: Float, velocityPx: Float): VerticalAnchor {
    if (!isCommitted(offsetPx, commitDistancePx, velocityPx)) return VerticalAnchor.Settled
    return if (commitDirection(offsetPx, velocityPx) > 0f) VerticalAnchor.Dismiss else VerticalAnchor.Details
}

/**
 * Clamp a raw drag offset for display. A drag toward an existing neighbor is allowed up to
 * one full card ([extentPx]); a drag toward a missing neighbor (rubber-band side) stretches
 * with diminishing resistance and never reaches a commit.
 */
fun clampWithRubberBand(
    rawOffsetPx: Float,
    extentPx: Float,
    allowPositive: Boolean,
    allowNegative: Boolean,
    resistance: Float = PlayerGestureThresholds.RUBBER_BAND_RESISTANCE
): Float = when {
    extentPx <= 0f -> 0f
    rawOffsetPx > 0f -> if (allowPositive) rawOffsetPx.coerceAtMost(extentPx) else rubberBand(rawOffsetPx, extentPx, resistance)
    rawOffsetPx < 0f -> if (allowNegative) rawOffsetPx.coerceAtLeast(-extentPx) else -rubberBand(-rawOffsetPx, extentPx, resistance)
    else -> 0f
}

private fun rubberBand(magnitude: Float, extentPx: Float, resistance: Float): Float =
    extentPx * resistance * (magnitude / (magnitude + extentPx))

private fun isCommitted(offsetPx: Float, commitDistancePx: Float, velocityPx: Float): Boolean =
    abs(offsetPx) >= commitDistancePx || abs(velocityPx) >= PlayerGestureThresholds.FLING_VELOCITY

private fun commitDirection(offsetPx: Float, velocityPx: Float): Float =
    if (abs(velocityPx) >= PlayerGestureThresholds.FLING_VELOCITY) velocityPx else offsetPx

/**
 * The finger-tracked offsets and neighbor availability, hoisted so [NowPlayingScreen] can
 * render the two-card strip (horizontal) and translate the whole screen (vertical) from
 * the same source the modifier drives. Offsets are in pixels; the screen applies them.
 */
@Stable
class PlayerGestureState {
    var widthPx by mutableFloatStateOf(0f)
    var heightPx by mutableFloatStateOf(0f)
    var hasPrevious by mutableStateOf(false)
    var hasNext by mutableStateOf(false)

    /** The displayed horizontal offset of the strip (rubber-banded at the ends). */
    var horizontalOffset by mutableFloatStateOf(0f)
        private set

    /** The displayed whole-screen vertical offset for a dismiss drag. */
    var verticalOffset by mutableFloatStateOf(0f)
        private set

    private var rawHorizontal = 0f
    private var rawVertical = 0f

    fun applyHorizontalDelta(dx: Float) {
        rawHorizontal += dx
        // Rightward (positive) reveals the previous card, leftward (negative) the next.
        horizontalOffset = clampWithRubberBand(rawHorizontal, widthPx, allowPositive = hasPrevious, allowNegative = hasNext)
    }

    fun applyVerticalDelta(dy: Float) {
        rawVertical += dy
        // Only a downward drag translates the screen (the dismiss); an upward reveal does not move it.
        verticalOffset = rawVertical.coerceAtLeast(0f)
    }

    /** The raw (unclamped) horizontal travel, for the anchor decision. */
    val rawHorizontalOffset: Float get() = rawHorizontal

    /** The raw (unclamped) vertical travel, for the anchor decision. */
    val rawVerticalOffset: Float get() = rawVertical

    fun displayHorizontal(value: Float) {
        horizontalOffset = value
    }

    fun displayVertical(value: Float) {
        verticalOffset = value
    }

    fun resetHorizontal() {
        rawHorizontal = 0f
        horizontalOffset = 0f
    }

    fun resetVertical() {
        rawVertical = 0f
        verticalOffset = 0f
    }
}

@Composable
fun rememberPlayerGestureState(): PlayerGestureState = remember { PlayerGestureState() }

/** The transport-and-navigation callbacks a committed gesture fires. */
data class PlayerGestureActions(
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onOpenDetails: () -> Unit,
    val onDismiss: () -> Unit
)

/** The accessibility action labels for the four gestures, all string resources upstream. */
data class PlayerGestureLabels(val next: String, val previous: String, val details: String, val close: String)

/**
 * Drive [state] from the finger: lock the axis at slop, follow the drag, and settle on
 * release through the pure resolvers. The four gestures are also exposed as custom
 * accessibility actions so none is reachable only by touch. Under [reducedMotion] the
 * settle is an instant snap with no translation, honouring the phase 11 audit promise.
 */
@Composable
fun Modifier.playerGestures(
    state: PlayerGestureState,
    reducedMotion: Boolean,
    actions: PlayerGestureActions,
    labels: PlayerGestureLabels
): Modifier {
    val scope = rememberCoroutineScope()
    val latestActions by rememberUpdatedState(actions)
    return this
        .onSizeChanged {
            state.widthPx = it.width.toFloat()
            state.heightPx = it.height.toFloat()
        }
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(labels.next) {
                    latestActions.onNext()
                    true
                },
                CustomAccessibilityAction(labels.previous) {
                    latestActions.onPrevious()
                    true
                },
                CustomAccessibilityAction(labels.details) {
                    latestActions.onOpenDetails()
                    true
                },
                CustomAccessibilityAction(labels.close) {
                    latestActions.onDismiss()
                    true
                }
            )
        }
        .pointerInput(state, reducedMotion) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val velocity = VelocityTracker()
                var axis: GestureAxis? = null
                val crossed = awaitTouchSlopOrCancellation(down.id) { change, over ->
                    axis = dominantAxis(over.x, over.y)
                    applyDelta(state, axis, over.x, over.y)
                    change.consume()
                }
                val locked = axis
                if (crossed == null || locked == null) return@awaitEachGesture
                velocity.addPosition(crossed.uptimeMillis, crossed.position)
                drag(down.id) { change ->
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val delta = change.positionChange()
                    applyDelta(state, locked, delta.x, delta.y)
                    change.consume()
                }
                val computed = velocity.calculateVelocity()
                val verticalCommitPx = PlayerGestureThresholds.VERTICAL_COMMIT_DP.dp.toPx()
                scope.launch {
                    when (locked) {
                        GestureAxis.Horizontal -> settleHorizontal(state, computed.x, reducedMotion, latestActions)
                        GestureAxis.Vertical -> settleVertical(state, computed.y, verticalCommitPx, reducedMotion, latestActions)
                    }
                }
            }
        }
}

private fun applyDelta(state: PlayerGestureState, axis: GestureAxis?, dx: Float, dy: Float) {
    when (axis) {
        GestureAxis.Horizontal -> state.applyHorizontalDelta(dx)
        GestureAxis.Vertical -> state.applyVerticalDelta(dy)
        null -> Unit
    }
}

private suspend fun settleHorizontal(state: PlayerGestureState, velocityX: Float, reducedMotion: Boolean, actions: PlayerGestureActions) {
    val target = resolveHorizontalTarget(state.rawHorizontalOffset, state.widthPx, velocityX, state.hasPrevious, state.hasNext)
    // Next commits leftward (negative), previous rightward (positive).
    val targetOffset = when (target) {
        HorizontalAnchor.Next -> -state.widthPx
        HorizontalAnchor.Previous -> state.widthPx
        HorizontalAnchor.Settled -> 0f
    }
    if (!reducedMotion) {
        animateTo(state.horizontalOffset, targetOffset) { state.displayHorizontal(it) }
    }
    when (target) {
        HorizontalAnchor.Next -> actions.onNext()
        HorizontalAnchor.Previous -> actions.onPrevious()
        HorizontalAnchor.Settled -> Unit
    }
    state.resetHorizontal()
}

private suspend fun settleVertical(
    state: PlayerGestureState,
    velocityY: Float,
    commitDistancePx: Float,
    reducedMotion: Boolean,
    actions: PlayerGestureActions
) {
    when (resolveVerticalTarget(state.rawVerticalOffset, commitDistancePx, velocityY)) {
        VerticalAnchor.Dismiss -> {
            if (!reducedMotion) animateTo(state.verticalOffset, state.heightPx) { state.displayVertical(it) }
            actions.onDismiss()
        }
        VerticalAnchor.Details -> {
            actions.onOpenDetails()
            if (!reducedMotion) animateTo(state.verticalOffset, 0f) { state.displayVertical(it) }
        }
        VerticalAnchor.Settled -> if (!reducedMotion) animateTo(state.verticalOffset, 0f) { state.displayVertical(it) }
    }
    state.resetVertical()
}

private suspend fun animateTo(from: Float, to: Float, onValue: (Float) -> Unit) {
    animate(initialValue = from, targetValue = to, animationSpec = spring()) { value, _ -> onValue(value) }
}
