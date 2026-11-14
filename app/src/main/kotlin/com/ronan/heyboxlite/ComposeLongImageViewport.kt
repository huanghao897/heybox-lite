package com.ronan.heyboxlite

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/** Scroll coordinates only. Layout remains viewport-sized even for very long images. */
@Stable
internal class ComposeLongImageViewport {
    var position by mutableStateOf(Offset.Zero)
        private set
    var imageWidth by mutableFloatStateOf(1f)
        private set
    var inset by mutableFloatStateOf(0f)
        private set
    private var imageHeight = 1f
    private var viewport = Size.Zero
    private var zoom = 1f
    private var extra = 0f
    private val maximumX get() = max(0f, imageWidth + inset * 2 - viewport.width)
    private val maximumY get() = max(0f, imageHeight + inset * 2 - viewport.height)

    fun layout(size: Size, sourceWidth: Int, sourceHeight: Int, round: Boolean,
               scale: Float, focus: Offset) {
        if (size == Size.Zero) return
        val ratio = scale / zoom
        viewport = size
        inset = if (round) min(size.width, size.height) * .1f else 0f
        imageWidth = (size.width - inset * 2).coerceAtLeast(1f) * scale
        imageHeight = (imageWidth.toDouble() * sourceHeight / sourceWidth.coerceAtLeast(1))
            .coerceAtMost(Int.MAX_VALUE - size.height.toDouble()).toFloat()
        extra = if (round && scale > 1.01f) size.width * .3f else 0f
        if (scale != zoom) {
            position = Offset((position.x + focus.x - inset) * ratio + inset - focus.x,
                (position.y + focus.y - inset) * ratio + inset - focus.y)
        }
        zoom = scale
        position = bounded(if (scale == 1f) Offset(0f, position.y) else position)
    }

    fun pan(delta: Offset): Offset {
        val previous = position
        position = bounded(position - delta)
        return previous - position
    }

    fun canPull(deltaY: Float): Boolean = if (deltaY > 0f) position.y <= 0f
        else position.y >= maximumY

    private fun bounded(value: Offset): Offset {
        val minimum = if (extra == 0f) 0f else -extra
        return Offset(value.x.coerceIn(minimum, maximumX + extra),
            value.y.coerceIn(minimum, maximumY + extra))
    }
}
