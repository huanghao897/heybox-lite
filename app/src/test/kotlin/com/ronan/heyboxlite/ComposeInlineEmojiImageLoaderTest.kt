package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    shadows = [ComposeInlineEmojiImageShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeInlineEmojiImageLoaderTest {
    @Before fun resetRequests() {
        ComposeInlineEmojiImageShadow.requests.clear()
        ComposeInlineEmojiImageShadow.cached.clear()
    }

    @Test fun originalPathPreservesTransparentEdgesAndOpaqueWhiteArtwork() {
        val original = pngWithTransparentEdges()
        assertTrue(original.hasAlpha())
        assertEquals(0, Color.alpha(original.getPixel(0, 0)))
        assertEquals(Color.WHITE, original.getPixel(8, 8))
        var result: Bitmap? = null

        ExistingComposeInlineEmojiImageLoader.load(LIGHT_URL, 16) { result = it }
        val request = ComposeInlineEmojiImageShadow.requests.single()
        assertEquals(LIGHT_URL, request.url)
        assertEquals(16, request.targetPx)
        request.callback.onLoaded(original)

        assertSame(original, result)
        assertEquals(0, Color.alpha(result!!.getPixel(0, 0)))
        assertEquals("White artwork is not a background to remove", Color.WHITE, result!!.getPixel(8, 8))
    }

    @Test fun cancellationDropsTheConsumerAndLateCompletionCannotUpdateDisposedText() {
        var result: Bitmap? = null
        val cancel = ExistingComposeInlineEmojiImageLoader.loadCancellable(LIGHT_URL, 16) { result = it }

        cancel()
        ComposeInlineEmojiImageShadow.requests.single().callback.onLoaded(pngWithTransparentEdges())

        assertNull(result)
    }

    @Test fun darkVariantIsRequestedAsSelectedByTheCatalogWithoutThumbnailConversion() {
        val catalog = ComposeInlineTextTestCatalog().apply { register("cube_alpha", LIGHT_URL, DARK_URL) }
        val model = buildComposeInlineTextModel("[cube_alpha]", true,
            androidx.compose.ui.graphics.Color.Blue, false, catalog)

        ExistingComposeInlineEmojiImageLoader.load(model.attachments.single().url, 16) { }

        assertEquals(DARK_URL, ComposeInlineEmojiImageShadow.requests.single().url)
    }

    @Test fun memoryLookupUsesTheIsolatedExactOriginalCache() {
        val original = pngWithTransparentEdges()
        ComposeInlineEmojiImageShadow.cached[LIGHT_URL] = original

        assertSame(original, ExistingComposeInlineEmojiImageLoader.cached(LIGHT_URL, 16))
        assertTrue(ComposeInlineEmojiImageShadow.requests.isEmpty())
    }

    companion object {
        const val LIGHT_URL = "https://emoji-alpha.invalid/emoji.png"
        const val DARK_URL = "https://emoji-alpha.invalid/emoji-dark.png"

        fun pngWithTransparentEdges(): Bitmap {
            val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            Canvas(source).drawRect(4f, 4f, 12f, 12f, Paint().apply { color = Color.WHITE })
            val bytes = ByteArrayOutputStream().use { output ->
                check(source.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            source.recycle()
            return checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
    }
}

@Implements(value = ImageLoader::class, isInAndroidSdk = false)
internal class ComposeInlineEmojiImageShadow {
    data class Request(val url: String, val targetPx: Int, val callback: ImageLoader.Callback)

    companion object {
        val requests = mutableListOf<Request>()
        val cached = mutableMapOf<String, Bitmap>()

        @JvmStatic @Implementation fun cachedExactOriginal(url: String): Bitmap? = cached[url]

        @JvmStatic @Implementation fun loadExactOriginalCancellable(url: String, targetPx: Int,
            callback: ImageLoader.Callback): ImageLoadRequest {
            requests += Request(url, targetPx, callback)
            return ImageLoadRequest.deliver(Handler(Looper.getMainLooper()), null) { }
        }

        @JvmStatic @Implementation fun loadOriginal(url: String, targetPx: Int, callback: ImageLoader.Callback) {
            error("Inline emoji must not use an original path permitting JPEG fallback: $url ($targetPx)")
        }

        @JvmStatic @Implementation fun loadCancellable(url: String, targetPx: Int,
            callback: ImageLoader.Callback): ImageLoadRequest =
            error("Inline emoji must not use the JPEG thumbnail path: $url ($targetPx)")

        @JvmStatic @Implementation fun load(url: String, targetPx: Int, callback: ImageLoader.Callback) {
            error("Inline emoji must not use the JPEG thumbnail path: $url ($targetPx)")
        }
    }
}
