package com.ronan.heyboxlite

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Claim pinch/pan before the pager; leave unzoomed horizontal drags for paging. */
internal fun Modifier.viewerGestures(
    transform: ComposeViewerTransform,
    longImage: Boolean,
    onDismiss: () -> Unit,
    onInteraction: () -> Unit,
    canPull: (Float) -> Boolean = { !longImage },
    onLongPan: (Offset) -> Unit = {},
    motion: Int = MotionLevel.FULL,
    zoomEnabled: Boolean = true,
): Modifier = pointerInput(transform, longImage, motion, zoomEnabled) {
    coroutineScope {
        val scope = this
        var lastTap = -1L
        var lastPosition = Offset.Zero
        var dismissCommitted = false
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                transform.cancelZoomAnimation()
                onInteraction()
                var total = Offset.Zero
                var edgeDistance = 0f
                var transformed = false
                var pulling = false
                var moved = false
                var cancelled = false
                var previousCount = 1
                val slop = viewConfiguration.touchSlop
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.count { it.pressed }
                    if (pressed > 1) {
                        transformed = true
                        moved = true
                        pulling = false
                        lastTap = -1L
                        transform.resetPull()
                        if (previousCount > 1 && zoomEnabled) {
                            val movement = event.calculatePan()
                            transform.pinch(event.calculateZoom(), event.calculateCentroid(useCurrent = true),
                                if (longImage) Offset.Zero else movement)
                            if (longImage) onLongPan(movement)
                        }
                        event.changes.forEach { it.consume() }
                    } else if (pressed == 1) {
                        // Rebase after a finger is lifted; do not turn the centroid jump
                        // into a pan or a page swipe.
                        if (previousCount == 1) {
                            val change = event.changes.first { it.pressed }
                            val delta = change.positionChangeIgnoreConsumed()
                            total += delta
                            if (total.getDistance() > slop) moved = true
                            if (transform.zoomed) {
                                if (longImage) onLongPan(delta) else transform.move(delta)
                                change.consume()
                            } else if (!transformed && !change.isConsumed) {
                                edgeDistance = if (canPull(delta.y)) edgeDistance + delta.y else 0f
                                if (!pulling && abs(edgeDistance) > slop * 2 &&
                                    abs(total.y) > abs(total.x) * 1.2f) {
                                    pulling = true
                                }
                                if (pulling) {
                                    transform.pull(delta)
                                    change.consume()
                                }
                            }
                        } else event.changes.forEach { it.consume() }
                    } else {
                        cancelled = event.changes.any { it.isConsumed && it.previousPressed }
                    }
                    previousCount = pressed
                } while (event.changes.any { it.pressed })

                if (pulling) {
                    if (!cancelled && ImageDismissPolicy.shouldDismiss(transform.dismiss.y, size.height)) {
                        dismissCommitted = true
                        onDismiss()
                    } else transform.resetPull()
                    lastTap = -1L
                } else if (!moved && !transformed && !cancelled) {
                    if (!longImage && !transform.contains(down.position)) {
                        lastTap = -1L
                        onDismiss()
                        return@awaitEachGesture
                    }
                    val timeout = ImageZoomPolicy.doubleTapTimeout(size.width < 700)
                    if (zoomEnabled && lastTap >= 0 && down.uptimeMillis - lastTap <= timeout &&
                        (down.position - lastPosition).getDistance() < slop * 4) {
                        if (motion == MotionLevel.OFF) transform.doubleTap(down.position)
                        else scope.launch { transform.animateDoubleTap(down.position,
                            if (motion == MotionLevel.REDUCED) 160 else 240) }
                        lastTap = -1L
                    } else {
                        lastTap = down.uptimeMillis
                        lastPosition = down.position
                    }
                } else lastTap = -1L
            }
        } finally {
            transform.cancelZoomAnimation()
            if (!dismissCommitted) transform.resetPull()
        }
    }
}
