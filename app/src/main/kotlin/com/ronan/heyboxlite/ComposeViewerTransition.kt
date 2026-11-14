package com.ronan.heyboxlite

import android.graphics.RectF
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer

/** Uniform scaling plus clipping preserves the source crop without stretching it. */
internal fun Modifier.viewerTransition(
    transform: ComposeViewerTransform,
    origin: RectF?,
    progress: () -> Float,
): Modifier {
    fun frame(): ImageViewerTransition.Frame {
        val viewport = transform.viewport
        val fitted = transform.fitted
        val fraction = progress()
        val full = ImageViewerTransition.Frame(1f, 0f, 0f,
            RectF(0f, 0f, viewport.width, viewport.height))
        if (origin == null || origin.width() <= 1f || origin.height() <= 1f ||
            fitted == Size.Zero || viewport == Size.Zero) return full
        val display = RectF((viewport.width - fitted.width) / 2,
            (viewport.height - fitted.height) / 2,
            (viewport.width + fitted.width) / 2, (viewport.height + fitted.height) / 2)
        val from = ImageViewerTransition.Frame.crop(display, origin, 0f, 0f,
            viewport.width.toInt(), viewport.height.toInt())
        return ImageViewerTransition.Frame.between(from, full, fraction)
    }
    return graphicsLayer {
        val value = frame()
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = value.scale
        scaleY = value.scale
        translationX = value.x
        translationY = value.y
        if (origin == null) alpha = progress()
    }.drawWithContent {
        val rect = frame().clip
        val content = this
        if (transform.viewport == Size.Zero) content.drawContent()
        else clipRect(rect.left, rect.top, rect.right, rect.bottom) { content.drawContent() }
    }
}
