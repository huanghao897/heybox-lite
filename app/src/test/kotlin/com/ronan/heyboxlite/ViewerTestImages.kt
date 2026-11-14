package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import java.io.File

internal class ViewerTestImages : ViewerImageRequests {
    class Pending(val url: String, val targetPx: Int, val original: Boolean,
                  val callback: (Bitmap?) -> Unit) {
        var cancelled = false
        fun complete(bitmap: Bitmap?) = callback(bitmap)
    }
    class Region(val targetPx: Int, val original: Boolean, val callback: (LongImageSource?) -> Unit) {
        var cancelled = false
        fun complete(source: LongImageSource?) = callback(source)
    }
    val images = mutableListOf<Pending>()
    val regions = mutableListOf<Region>()
    val gifs = mutableListOf<String>()
    override fun gif(url: String, complete: (android.graphics.drawable.Drawable?) -> Unit): () -> Unit {
        gifs.add(url)
        return {}
    }
    override fun image(url: String, targetPx: Int, original: Boolean, complete: (Bitmap?) -> Unit): () -> Unit {
        val pending = Pending(url, targetPx, original, complete).also(images::add)
        return { pending.cancelled = true }
    }
    override fun longImage(url: String, targetPx: Int, original: Boolean,
                           complete: (LongImageSource?) -> Unit): () -> Unit {
        val pending = Region(targetPx, original, complete).also(regions::add)
        return { pending.cancelled = true }
    }
}

internal fun viewerBitmap(width: Int = 100, height: Int = 100): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

internal fun viewerLongSource(file: File): LongImageSource {
    val bitmap = viewerBitmap(64, 800)
    try {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally {
        bitmap.recycle()
    }
    val decoder = requireNotNull(BitmapRegionDecoder.newInstance(file.absolutePath, false))
    val lease = StorageFileLeases.acquire(file)
    try {
        // Keep the real decoder/file lifecycle without changing the production constructor.
        val constructor = LongImageSource::class.java.getDeclaredConstructor(
            BitmapRegionDecoder::class.java, File::class.java, StorageFileLeases.Lease::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(decoder, file, lease)
    } catch (error: ReflectiveOperationException) {
        decoder.recycle()
        lease.close()
        throw error
    }
}
