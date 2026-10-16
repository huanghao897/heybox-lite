package com.ronan.heyboxlite

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference

/** Ordinary thumbnails force JPEG; emoji must retain the original image's alpha channel. */
internal object ExistingComposeInlineEmojiImageLoader : ComposeImageLoader {
    override fun cached(sourceUrl: String, targetPx: Int): Bitmap? =
        ImageLoader.cachedExactOriginal(sourceUrl)

    override fun load(sourceUrl: String, targetPx: Int, callback: (Bitmap?) -> Unit) {
        loadCancellable(sourceUrl, targetPx, callback)
    }

    override fun loadCancellable(
        sourceUrl: String,
        targetPx: Int,
        callback: (Bitmap?) -> Unit,
    ): () -> Unit {
        val receiver = AtomicReference<((Bitmap?) -> Unit)?>(callback)
        val request = ImageLoader.loadExactOriginalCancellable(sourceUrl, targetPx) { bitmap ->
            receiver.getAndSet(null)?.invoke(bitmap)
        }
        return {
            receiver.set(null)
            request.cancel()
        }
    }
}
