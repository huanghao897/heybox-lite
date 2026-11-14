package com.ronan.heyboxlite

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

/** Compose owns scrolling and gestures; the interoperability leaf draws bounded region tiles. */
@Composable
internal fun ComposeViewerLongImage(
    source: LongImageSource,
    active: Boolean,
    round: Boolean,
    transform: ComposeViewerTransform,
    onReady: () -> Unit,
    onFailed: () -> Unit,
    onDismiss: () -> Unit,
    viewport: ComposeLongImageViewport = remember { ComposeLongImageViewport() },
    interactive: Boolean = true,
) {
    var size by remember { mutableStateOf(Size.Zero) }
    var ready by remember(source) { mutableStateOf(false) }
    SideEffect {
        viewport.layout(size, source.width, source.height, round, transform.zoom, transform.zoomFocus)
    }
    val scroll = rememberScrollableState { delta -> viewport.pan(Offset(0f, delta)).y }
    Box(Modifier.fillMaxSize().onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }
        .alpha(if (ready) 1f else 0f)
        .then(if (active && interactive) Modifier.viewerGestures(transform, true, onDismiss, {},
            canPull = viewport::canPull, onLongPan = { viewport.pan(it) }) else Modifier)
        .scrollable(scroll, Orientation.Vertical, enabled = active && interactive && !transform.zoomed)
        .testTag("viewer-long-image")) {
        key(source) { AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LongImageTileView(context, source, 1, { ready = true; onReady() }, onFailed)
                    .apply { setWindowed() }
            },
            onRelease = { it.release() },
            update = { view ->
                view.setActive(active)
                if (size != Size.Zero) {
                    view.windowViewport(viewport.imageWidth.toInt().coerceAtLeast(1),
                        viewport.position.x - viewport.inset, viewport.position.y - viewport.inset,
                        size.height.toInt())
                }
            },
        ) }
    }
}
