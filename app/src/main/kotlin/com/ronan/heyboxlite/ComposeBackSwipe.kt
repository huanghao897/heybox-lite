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
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
): Modifier = pointerInput(route, enabled, edgePx, thresholdPx, touchSlopPx) {
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
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) break

            // Main pass runs after child scrolling and controls. A consumed
            // change belongs to that child and must never become back.
            if (!decided && change.isConsumed) break
            val delta = change.positionChangeIgnoreConsumed()
            totalX += delta.x
            totalY += delta.y

            if (!decided && maxOf(abs(totalX), abs(totalY)) >= touchSlopPx) {
                decided = true
                accepted = totalX > 0f && abs(totalX) > abs(totalY) * 1.12f
                if (!accepted) break
            }
            if (accepted) {
                change.consume()
                onProgress(totalX.coerceIn(0f, thresholdPx * 2.5f))
            }
        }

        if (accepted && totalX >= thresholdPx) {
            finished = true
            onProgress(0f)
            onComplete()
        }
        if (!finished) onCancel()
    }
}
