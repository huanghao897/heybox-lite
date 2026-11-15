package com.ronan.heyboxlite

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlin.math.max
import kotlin.math.min

/** All positions are physical pixels. Zoom is relative to the fitted image. */
@Stable
internal class ComposeViewerTransform {
    var zoom by mutableFloatStateOf(1f)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set
    var dismiss by mutableStateOf(Offset.Zero)
        private set
    var tapLevel by mutableIntStateOf(0)
        private set
    var viewport by mutableStateOf(Size.Zero)
        private set
    var fitted by mutableStateOf(Size.Zero)
        private set
    var zoomFocus by mutableStateOf(Offset.Zero)
        private set
    private var round = false
    private var zoomAnimation: Job? = null
    val zoomed: Boolean get() = zoom > 1.01f

    fun layout(viewport: Size, image: Size, round: Boolean) {
        if (viewport == this.viewport && image == imageSize && round == this.round) return
        this.viewport = viewport
        this.imageSize = image
        this.round = round
        val inset = if (round) min(viewport.width, viewport.height) * .10f else 0f
        val fit = min((viewport.width - inset * 2) / max(1f, image.width),
            (viewport.height - inset * 2) / max(1f, image.height)).coerceAtLeast(0f)
        fitted = Size(image.width * fit, image.height * fit)
        pan = bounded(pan)
    }

    private var imageSize = Size.Zero

    fun pinch(factor: Float, centroid: Offset, movement: Offset) {
        cancelZoomAnimation()
        zoomFocus = centroid
        val next = (zoom * ImageZoomPolicy.pinchFactor(factor, round))
            .coerceIn(1f, ImageZoomPolicy.MAX_ZOOM)
        val relative = centroid - Offset(viewport.width / 2, viewport.height / 2)
        val ratio = next / zoom
        pan = (pan - relative) * ratio + relative + movement
        zoom = next
        tapLevel = 0
        dismiss = Offset.Zero
        pan = bounded(pan)
    }

    fun move(delta: Offset) {
        cancelZoomAnimation()
        pan = bounded(pan + delta)
    }

    fun pull(delta: Offset) {
        cancelZoomAnimation()
        dismiss += delta
    }
    fun resetPull() { dismiss = Offset.Zero }

    fun doubleTap(position: Offset) {
        cancelZoomAnimation()
        applyZoom(nextTapZoom(position), zoom, pan, position)
    }

    suspend fun animateDoubleTap(position: Offset, durationMillis: Int) {
        cancelZoomAnimation()
        val job = currentCoroutineContext().job
        zoomAnimation = job
        val initialZoom = zoom
        val initialPan = pan
        val target = nextTapZoom(position)
        try {
            animate(initialZoom, target, animationSpec = tween(durationMillis)) { value, _ ->
                applyZoom(value, initialZoom, initialPan, position)
            }
        } finally {
            if (zoomAnimation === job) zoomAnimation = null
        }
    }

    fun cancelZoomAnimation() {
        zoomAnimation?.cancel()
        zoomAnimation = null
    }

    private fun nextTapZoom(position: Offset): Float {
        zoomFocus = position
        tapLevel = ImageZoomPolicy.nextLevel(tapLevel)
        val widthZoom = (viewport.width / max(1f, fitted.width))
        dismiss = Offset.Zero
        return ImageZoomPolicy.targetScale(tapLevel, widthZoom)
    }

    private fun applyZoom(next: Float, initialZoom: Float, initialPan: Offset, position: Offset) {
        val relative = position - Offset(viewport.width / 2, viewport.height / 2)
        zoom = next
        pan = if (next == 1f) Offset.Zero else bounded((initialPan - relative) * (next / initialZoom) + relative)
    }

    fun reset() {
        cancelZoomAnimation()
        zoom = 1f
        pan = Offset.Zero
        dismiss = Offset.Zero
        tapLevel = 0
    }

    fun contains(position: Offset): Boolean {
        val center = Offset(viewport.width / 2, viewport.height / 2) + pan
        return kotlin.math.abs(position.x - center.x) <= fitted.width * zoom / 2 &&
            kotlin.math.abs(position.y - center.y) <= fitted.height * zoom / 2
    }

    private fun bounded(value: Offset): Offset {
        // On a circle an image narrower than the diameter still needs to reach
        // the central chord, otherwise the corners remain behind the screen mask.
        val width = fitted.width * zoom
        val height = fitted.height * zoom
        val reachX = if (round && zoomed) min(width / 2, viewport.width * .4f) else 0f
        val reachY = if (round && zoomed) min(height / 2, viewport.height * .4f) else 0f
        val x = max(max(0f, (width - viewport.width) / 2), reachX)
        val y = max(max(0f, (height - viewport.height) / 2), reachY)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
}
