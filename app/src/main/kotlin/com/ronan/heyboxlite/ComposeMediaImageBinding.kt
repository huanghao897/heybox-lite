package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView

internal interface ComposeMediaImageRequests {
    fun load(view: ImageView, url: String, targetPx: Int, complete: (Boolean, Bitmap?) -> Unit)
    fun animate(view: ImageView, url: String)
    fun cancel(view: ImageView)
}

internal object ExistingComposeMediaImageRequests : ComposeMediaImageRequests {
    override fun load(view: ImageView, url: String, targetPx: Int, complete: (Boolean, Bitmap?) -> Unit) {
        ImageLoader.intoMeasuredStable(view, url, targetPx, complete)
    }

    override fun animate(view: ImageView, url: String) = ImageLoader.intoGif(view, url)
    override fun cancel(view: ImageView) = ImageLoader.cancel(view)
}

internal data class ComposeMediaImageRequest(
    val url: String,
    val targetPx: Int,
    val animatedUrl: String = "",
    val retry: Int = 0,
)

/** One binding per recycled View; old loads must not install another item's GIF or error. */
internal class ComposeMediaImageBinding(
    private val view: ImageView,
    val requests: ComposeMediaImageRequests,
) {
    private var current: ComposeMediaImageRequest? = null
    private var generation = 0

    fun bind(next: ComposeMediaImageRequest, complete: (Boolean) -> Unit) {
        if (next == current) return
        reset()
        current = next
        val serial = generation
        requests.load(view, next.url, next.targetPx) { success, _ ->
            if (serial != generation || next != current) return@load
            complete(success)
            if (success && next.animatedUrl.isNotBlank()) requests.animate(view, next.animatedUrl)
        }
    }

    fun reset() {
        generation++
        current = null
        requests.cancel(view)
        view.setImageDrawable(null)
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }
}

private const val MEDIA_BINDING_TAG = 0x7f0b0d01

internal fun bindComposeMediaImage(
    view: ImageView,
    request: ComposeMediaImageRequest,
    requests: ComposeMediaImageRequests,
    complete: (Boolean) -> Unit,
) {
    val previous = view.getTag(MEDIA_BINDING_TAG) as? ComposeMediaImageBinding
    val binding = if (previous?.requests === requests) previous else {
        previous?.reset()
        ComposeMediaImageBinding(view, requests).also { view.setTag(MEDIA_BINDING_TAG, it) }
    }
    binding.bind(request, complete)
}

internal fun resetComposeMediaImage(view: ImageView) {
    (view.getTag(MEDIA_BINDING_TAG) as? ComposeMediaImageBinding)?.reset()
}
