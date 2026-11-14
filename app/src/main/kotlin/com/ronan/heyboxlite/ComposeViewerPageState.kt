package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal interface ViewerImageRequests {
    fun image(url: String, targetPx: Int, original: Boolean, complete: (Bitmap?) -> Unit): () -> Unit
    fun longImage(url: String, targetPx: Int, original: Boolean,
                  complete: (LongImageSource?) -> Unit): () -> Unit
    fun gif(url: String, complete: (Drawable?) -> Unit): () -> Unit = {}
}

internal class ExistingViewerImageRequests(private val context: Context) : ViewerImageRequests {
    override fun image(url: String, targetPx: Int, original: Boolean,
                       complete: (Bitmap?) -> Unit): () -> Unit {
        val request = if (original) ImageLoader.loadOriginalCancellable(url, targetPx, complete)
            else ImageLoader.loadCancellable(url, targetPx, complete)
        return request::cancel
    }

    override fun longImage(url: String, targetPx: Int, original: Boolean,
                           complete: (LongImageSource?) -> Unit): () -> Unit =
        ViewerRegionLoader.open(context, url, targetPx, original, complete)::cancel

    override fun gif(url: String, complete: (Drawable?) -> Unit): () -> Unit =
        ImageLoader.requestGif(ImageLoader.originalUrl(url), complete)::cancel
}

/** One composed page owns its requests; a late result cannot revive a disposed page. */
@Stable
internal class ComposeViewerPageState(
    val url: String,
    private val targetPx: Int,
    preview: Bitmap? = null,
    private val requests: ViewerImageRequests,
) {
    var bitmap by mutableStateOf(preview?.takeUnless { it.isRecycled })
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var original by mutableStateOf(false)
        private set
    var longSource by mutableStateOf<LongImageSource?>(null)
        private set
    var retainedLongSource by mutableStateOf<LongImageSource?>(null)
        private set
    var longFailed by mutableStateOf(false)
        private set
    var animation by mutableStateOf<Drawable?>(null)
        private set
    private var generation = 0
    private var regionGeneration = 0
    private var closed = false
    private var active = true
    private var entered = false
    private var upgraded = false
    private var imageRequest: (() -> Unit)? = null
    private var regionRequest: (() -> Unit)? = null
    private var regionRequestOriginal = false
    private var failedImageOriginal = false
    private var failedRegionOriginal = false
    private var gifRequest: (() -> Unit)? = null
    private var gifAttempted = false
    private var immediateOriginal = false
    private var originalAttempted = false
    private var playGif = true
    private var longOriginal = false
    private var retainedLongOriginal = false

    fun start() {
        if (!closed && active && bitmap == null && !loading) load(false)
    }

    fun enterCompleted(immediateOriginal: Boolean, playGif: Boolean = true) {
        if (closed) return
        entered = true
        this.immediateOriginal = immediateOriginal
        this.playGif = playGif
        if (!active) return
        if (immediateOriginal && !original && !originalAttempted) load(true)
        else if (!upgraded && !loading) load(false)
        openLongImage()
        openGif()
    }

    fun loadOriginal() {
        if (!closed && active && !original && !loading) {
            longFailed = false
            load(true)
        }
    }

    fun retry() {
        if (closed || !active || loading) return
        if (longFailed && !failed) retryLongImage()
        else load(if (failed) failedImageOriginal else original)
    }

    fun regionFailed(source: LongImageSource) {
        if (closed || longSource !== source) return
        failedRegionOriginal = longOriginal
        longSource = retainedLongSource
        longOriginal = retainedLongSource != null && retainedLongOriginal
        retainedLongSource = null
        longFailed = true
        source.close()
    }

    fun regionReady(source: LongImageSource) {
        if (closed || longSource !== source) return
        val retained = retainedLongSource
        retainedLongSource = null
        retained?.close()
    }

    fun retryLongImage() {
        if (closed || !active || !longFailed) return
        longFailed = false
        openLongImage(original || failedRegionOriginal)
    }

    private fun load(full: Boolean) {
        val serial = ++generation
        imageRequest?.invoke()
        imageRequest = null
        gifRequest?.invoke()
        gifRequest = null
        if (animation == null) gifAttempted = false
        if (full) originalAttempted = true
        loading = true
        failed = false
        val cancel = requests.image(url, if (full) 2400 else targetPx, full) { result ->
            if (closed || !active || serial != generation) return@image
            imageRequest = null
            loading = false
            if (result == null || result.isRecycled) {
                failedImageOriginal = full
                failed = true
            } else {
                bitmap = result
                upgraded = true
                original = full
                if (longFailed && failedRegionOriginal != full) longFailed = false
                if (entered) { openLongImage(); openGif() }
            }
        }
        if (serial == generation && loading && !closed) imageRequest = cancel else cancel()
    }

    private fun openLongImage(full: Boolean = original) {
        val image = bitmap ?: return
        if (!entered || closed || !active || (longSource != null && (!full || longOriginal)) ||
            longFailed || GifSupport.isGifUrl(url) || image.height <= image.width * 3L) return
        if (regionRequest != null && (!full || regionRequestOriginal)) return
        cancelRegionRequest()
        val serial = regionGeneration
        regionRequestOriginal = full
        // A non-null sentinel also handles loaders that complete synchronously.
        regionRequest = {}
        val cancel = requests.longImage(url, targetPx, full) { source ->
            if (closed || !active || serial != regionGeneration || regionRequest == null) {
                source?.close()
                return@longImage
            }
            regionRequest = null
            longFailed = source == null
            if (source == null) failedRegionOriginal = full
            if (source != null) {
                val previous = longSource
                if (previous !== source) {
                    retainedLongSource?.close()
                    retainedLongSource = previous
                    retainedLongOriginal = longOriginal
                }
                longSource = source
                longOriginal = full
            }
        }
        if (serial == regionGeneration && regionRequest != null && !closed) regionRequest = cancel
        else cancel()
    }

    private fun cancelRegionRequest() {
        regionGeneration++
        val cancel = regionRequest
        regionRequest = null
        cancel?.invoke()
    }

    private fun openGif() {
        if (closed || !active || gifAttempted || !entered || !playGif || !GifSupport.isGifUrl(url)) return
        gifAttempted = true
        val serial = generation
        gifRequest = requests.gif(url) { drawable ->
            if (closed || !active || serial != generation) {
                GifSupport.setRunning(drawable, false)
            } else {
                gifRequest = null
                animation = drawable
            }
        }
    }

    fun setActive(value: Boolean) {
        if (closed || active == value) return
        active = value
        if (!value) {
            generation++
            imageRequest?.invoke()
            cancelRegionRequest()
            gifRequest?.invoke()
            GifSupport.setRunning(animation, false)
            if (animation == null) gifAttempted = false
            if (!original) originalAttempted = false
            imageRequest = null
            regionRequest = null
            gifRequest = null
            loading = false
        } else if (bitmap == null) start()
        else if (entered) {
            if (immediateOriginal && !original && !originalAttempted) load(true)
            else if (!upgraded) load(false)
            openLongImage()
            openGif()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        generation++
        imageRequest?.invoke()
        cancelRegionRequest()
        gifRequest?.invoke()
        GifSupport.setRunning(animation, false)
        animation = null
        imageRequest = null
        regionRequest = null
        longSource?.close()
        longSource = null
        retainedLongSource?.close()
        retainedLongSource = null
        bitmap = null
        loading = false
    }
}
