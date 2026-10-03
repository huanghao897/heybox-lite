package com.ronan.heyboxlite

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * A parent-level back gesture that leaves vertical scrolling and controls to
 * their child. It only consumes a decided rightward horizontal gesture.
 */
internal fun Modifier.composeBackSwipe(
    route: String,
    enabled: Boolean,
    edgePx: Float,
    thresholdPx: Float,
    touchSlopPx: Float,
    maxDragPx: Float,
    canStart: (direction: Int) -> Boolean,
    onStart: (direction: Int) -> Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
): Modifier = pointerInput(route, enabled, edgePx, thresholdPx, touchSlopPx, maxDragPx) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!ComposeSwipePolicy.canArm(route, down.position.x, edgePx)) {
            return@awaitEachGesture
        }

        var totalX = 0f
        var totalY = 0f
        var decided = false
        var accepted = false
        var finished = false

        while (true) {
            // Initial pass lets the shell decide before a LazyColumn starts a
            // horizontal-looking drag, but we do not consume anything until
            // the axis and direction are unambiguous. Vertical scrolling and
            // sliders therefore keep their normal ownership.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size != 1) {
                // A second pointer belongs to pinch/zoom or another
                // multi-touch control. It must never finish a partially
                // accumulated back gesture.
                accepted = false
                break
            }
            val change = event.changes.firstOrNull() ?: break
            val delta = change.positionChangeIgnoreConsumed()
            totalX += delta.x
            totalY += delta.y

            if (!decided && maxOf(abs(totalX), abs(totalY)) >= touchSlopPx) {
                decided = true
                val direction = if (totalX > 0f) 1 else -1
                accepted = abs(totalX) > abs(totalY) * 1.18f
                        && canStart(direction)
                        && onStart(direction)
                if (!accepted) break
            }
            if (accepted) {
                change.consume()
                val limit = maxDragPx.takeIf { it > 0f }
                onProgress(if (limit == null) totalX else totalX.coerceIn(-limit, limit))
            }
            if (!change.pressed) break
        }

        if (accepted && abs(totalX) >= thresholdPx) {
            finished = true
            onComplete()
        }
        if (!finished) onCancel()
    }
}
