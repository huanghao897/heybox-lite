package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    shadows = [ComposeInlineEmojiImageShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmojiRendererReliabilityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val loadedField = field("catalogLoaded")
    private var wasLoaded = false
    private var wasLoading = false
    private lateinit var oldReady: List<Runnable>

    @Before fun prepare() {
        EmojiRenderer.clear()
        ComposeInlineEmojiImageShadow.requests.clear()
        ComposeInlineEmojiImageShadow.cached.clear()
        wasLoaded = loadedField.getBoolean(null)
        wasLoading = field("loading").getBoolean(null)
        oldReady = ready().toList()
        loadedField.setBoolean(null, false)
        ready().clear()
    }

    @After fun cleanup() {
        EmojiRenderer.clear()
        loadedField.setBoolean(null, wasLoaded)
        field("loading").setBoolean(null, wasLoading)
        ready().clear()
        ready().addAll(oldReady)
    }

    @Test fun subscriptionsBeforeCatalogStartAreRetainedAndUnknownSpansAreNotCached() {
        field("loading").setBoolean(null, false)
        val view = TextView(context)
        val source = "[cube_catalog_delayed_976]"
        EmojiRenderer.set(view, source)
        assertEquals(1, ready().size)
        assertTrue(spans(view).isEmpty())
        EmojiStore.register("cube_catalog_delayed_976", "https://fixtures.invalid/delayed.png", "")
        ready().single().run()
        val request = ComposeInlineEmojiImageShadow.requests.single()
        request.callback.onLoaded(ComposeInlineEmojiImageLoaderTest.pngWithTransparentEdges())
        val drawable = drawable(spans(view).single())
        assertEquals(0, Color.alpha(drawable.bitmap.getPixel(0, 0)))
        assertEquals(Color.WHITE, drawable.bitmap.getPixel(8, 8))
    }

    @Test fun sameSourceThemeRebindAndResetRejectOldCatalogCallbacks() {
        val source = "[cube_same_source_976]"
        val view = TextView(context)
        EmojiRenderer.set(view, source, false)
        val old = ready().single()
        EmojiRenderer.set(view, source, true)
        assertEquals(1, ready().size)
        val current = view.text
        old.run()
        assertSame(current, view.text)
        EmojiRenderer.reset(view)
        EmojiRenderer.set(view, source, false)
        val rebound = view.text
        old.run()
        assertSame(rebound, view.text)
        EmojiRenderer.reset(view)
        assertTrue(ready().isEmpty())
    }

    @Test fun moreThanTwentyFourViewsAndRepeatedTokensShareOneLoadWithoutLosingWaiters() {
        EmojiStore.register("cube_many_views_976", "https://fixtures.invalid/shared.png", "")
        val views = (0 until 32).map { TextView(context) }
        views.forEach { EmojiRenderer.set(it, "[cube_many_views_976] [cube_many_views_976]") }
        assertEquals(1, ComposeInlineEmojiImageShadow.requests.size)
        ComposeInlineEmojiImageShadow.requests.single().callback
            .onLoaded(ComposeInlineEmojiImageLoaderTest.pngWithTransparentEdges())
        views.forEach { assertEquals(2, spans(it).size) }
        views.forEach(EmojiRenderer::reset)
    }

    @Test fun cancelledSameUrlRequestCannotCompleteTheReboundOwnersLoad() {
        EmojiStore.register("cube_rebound_976", "https://fixtures.invalid/rebound.png", "")
        val view = TextView(context)
        EmojiRenderer.set(view, "[cube_rebound_976]")
        val old = ComposeInlineEmojiImageShadow.requests.single()
        EmojiRenderer.reset(view)
        EmojiRenderer.set(view, "[cube_rebound_976]")
        val current = ComposeInlineEmojiImageShadow.requests.last()
        assertNotSame(old, current)
        old.callback.onLoaded(image(Color.RED))
        assertTrue(spans(view).isEmpty())
        current.callback.onLoaded(image(Color.GREEN))
        assertEquals(Color.GREEN, drawable(spans(view).single()).bitmap.getPixel(8, 8))
    }

    @Test fun clearInvalidatesPendingCompletionBeforeTheSameUrlIsRequestedAgain() {
        val url = "https://fixtures.invalid/clear-generation.png"
        EmojiStore.register("cube_clear_generation_976", url, "")
        val view = TextView(context)
        EmojiRenderer.set(view, "[cube_clear_generation_976]")
        val old = ComposeInlineEmojiImageShadow.requests.single()
        EmojiRenderer.clear()
        old.callback.onLoaded(image(Color.RED))
        assertNull(EmojiRenderer.cachedBitmap(url))
        assertTrue(spans(view).isEmpty())
        EmojiRenderer.set(view, "[cube_clear_generation_976]")
        val current = ComposeInlineEmojiImageShadow.requests.last()
        assertNotSame(old, current)
        old.callback.onLoaded(image(Color.RED))
        assertTrue(spans(view).isEmpty())
        current.callback.onLoaded(image(Color.GREEN))
        assertEquals(Color.GREEN, drawable(spans(view).single()).bitmap.getPixel(8, 8))
        EmojiRenderer.reset(view)
    }

    @Test fun darkRebindRejectsLightBitmapAndKeepsGameIconAndBlueText() {
        EmojiStore.register("cube_theme_976", "https://fixtures.invalid/light.png",
            "https://fixtures.invalid/dark.png")
        val source = RichContent.commentText("[cube_theme_976] " +
            "<a data-link-type='game' data-game-id='42'>Game</a>")
        val view = TextView(context)
        RichInlineRenderer.set(view, source, false, Color.BLUE)
        val light = ComposeInlineEmojiImageShadow.requests.single()
        RichInlineRenderer.set(view, source, true, Color.CYAN)
        val dark = ComposeInlineEmojiImageShadow.requests.last()
        light.callback.onLoaded(image(Color.RED))
        assertEquals(1, spans(view).size)
        dark.callback.onLoaded(image(Color.GREEN))
        assertEquals(2, spans(view).size)
        val text = view.text as Spanned
        assertEquals(Color.CYAN, text.getSpans(0, text.length,
            ForegroundColorSpan::class.java).single().foregroundColor)
        assertTrue(text.toString().endsWith("Game"))
    }

    @Test fun nonGameLinkKeepsBlueLabelAndIconWhenItsRemoteIconCompletes() {
        val source = RichContent.commentText("<a data-link-type='text' " +
            "href='https://example.invalid/lottery' icon-url='https://fixtures.invalid/ticket.png'>Lottery</a>")
        val view = TextView(context)
        RichInlineRenderer.set(view, source, false, Color.BLUE)
        assertEquals(1, spans(view).size)
        val request = ComposeInlineEmojiImageShadow.requests.single()
        assertEquals("https://fixtures.invalid/ticket.png", request.url)
        request.callback.onLoaded(image(Color.MAGENTA))
        assertEquals(1, spans(view).size)
        val text = view.text as Spanned
        assertEquals("Lottery", RichInlineRenderer.plainText(source))
        assertEquals(Color.BLUE, text.getSpans(0, text.length,
            ForegroundColorSpan::class.java).single().foregroundColor)
    }

    private fun spans(view: TextView): Array<CenteredImageSpan> =
        (view.text as Spanned).getSpans(0, view.text.length, CenteredImageSpan::class.java)

    private fun image(color: Int) = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }

    private fun drawable(span: CenteredImageSpan): BitmapDrawable = CenteredImageSpan::class.java
        .getDeclaredField("drawable").apply { isAccessible = true }.get(span) as BitmapDrawable

    private fun field(name: String) = EmojiStore::class.java.getDeclaredField(name)
        .apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun ready() = field("READY").get(null) as MutableList<Runnable>
}
