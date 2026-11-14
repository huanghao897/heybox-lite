package com.ronan.heyboxlite

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** The OS drawable decoder stays a leaf, while Compose owns its size and lifecycle. */
@Composable
internal fun ComposeViewerGif(drawable: Drawable, active: Boolean, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> GifImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        onRelease = { ImageLoader.cancel(it); it.setImageDrawable(null) },
        update = { view ->
            if (view.drawable !== drawable) GifSupport.apply(view, drawable)
            GifSupport.setRunning(drawable, active)
        },
    )
}
