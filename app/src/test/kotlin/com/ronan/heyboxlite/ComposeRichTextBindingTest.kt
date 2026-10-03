package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "mdpi")
class ComposeRichTextBindingTest {
    @Before fun prepareRenderer() { EmojiRenderer.clear() }
    @After fun clearRenderer() { EmojiRenderer.clear() }

    @Test fun equalBindingsKeepTheSameTextAndDoNotRequestLayout() {
        val view = view()
        val binding = binding()
        view.bind(binding)
        view.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertFalse(view.isLayoutRequested)
        val rendered = view.text
        repeat(10) { view.bind(binding.copy()) }
        assertSame(rendered, view.text)
        assertFalse(view.isLayoutRequested)
        assertEquals(binding.source, view.tag)
    }

    @Test fun layoutAndBaseColorChangesKeepTheExistingGameLinkSpans() {
        val source = RichGameLinkMarkup.normalizeAnchors(
            "<a data-link-type=\"game\" data-game-id=\"42\">Game</a>")
        val initial = binding(source)
        val view = view()
        view.bind(initial)
        val rendered = view.text
        val next = initial.copy(textColor = Color.RED, lineSpacing = 1.7f,
            mediumWeight = true, maxLines = 2, ellipsize = TextUtils.TruncateAt.END)
        assertFalse(next.needsRender(initial))
        view.bind(next)
        assertSame(rendered, view.text)
        assertEquals(Color.RED, view.currentTextColor)
        assertEquals(1.7f, view.lineSpacingMultiplier, 0f)
        assertEquals(2, view.maxLines)
        assertEquals(TextUtils.TruncateAt.END, view.ellipsize)
        assertEquals(Color.BLUE, (view.text as Spanned)
            .getSpans(0, view.text.length, ForegroundColorSpan::class.java).single().foregroundColor)
    }

    @Test fun sourceThemeLinkColorSizeDensityAndBadgeChangesInvalidateRichRendering() {
        val initial = binding()
        val changes = listOf(initial.copy(source = "Replacement"), initial.copy(darkMode = true),
            initial.copy(linkColor = Color.RED), initial.copy(textSizePx = 24f),
            initial.copy(density = 2f), initial.copy(cy = true))
        for (next in changes) {
            val view = view()
            view.bind(initial)
            val rendered = view.text
            assertTrue(next.needsRender(initial))
            view.bind(next)
            assertNotSame(rendered, view.text)
            assertEquals(RichGameLinkMarkup.parse(if (next.cy) "Cy ${next.source}" else next.source).text,
                view.tag)
            assertEquals(next.textSizePx, view.textSize, 0f)
        }
    }

    @Test fun aNewViewBindsEvenWhenTheSameInputsWereBoundToAnotherView() {
        val first = view()
        val second = view()
        val binding = binding()
        first.bind(binding)
        second.bind(binding)
        assertEquals(binding.source, second.tag)
        assertEquals(first.text.toString(), second.text.toString())
        assertNotSame(first.text, second.text)
    }

    @Test fun removingCyRemovesTheBadgeAndKeepsTheDefaultTagAsSourceText() {
        val view = view()
        val initial = binding().copy(cy = true)
        view.bind(initial)
        assertCyBadge(view.text as Spanned)
        view.bind(initial.copy(cy = false))
        assertEquals(initial.source, view.tag)
        assertTrue((view.text as Spanned)
            .getSpans(0, view.text.length, CenteredImageSpan::class.java).isEmpty())
    }

    @Test fun resetClearsTheDefaultTagAndDecoratorsAndForcesAnEqualSourceToRebind() {
        val view = view()
        val initial = binding().copy(cy = true, maxLines = 2, ellipsize = TextUtils.TruncateAt.END)
        view.bind(initial)
        val rendered = view.text
        assertCyBadge(rendered as Spanned)
        view.reset()
        assertNull(view.tag)
        assertEquals("", view.text.toString())
        val decorators = EmojiRenderer::class.java.getDeclaredField("DECORATORS")
            .apply { isAccessible = true }.get(null) as Map<*, *>
        assertFalse(decorators.containsKey(view))
        view.bind(initial.copy())
        assertNotSame(rendered, view.text)
        assertEquals("Cy ${initial.source}", view.tag)
        assertCyBadge(view.text as Spanned)
        view.reset()
        view.bind(initial.copy(cy = false, textColor = Color.RED, textSizePx = 20f,
            lineSpacing = 1.7f, mediumWeight = true, maxLines = 1, ellipsize = null))
        assertEquals(initial.source, view.tag)
        assertEquals(Color.RED, view.currentTextColor)
        assertEquals(20f, view.textSize, 0f)
        assertEquals(1.7f, view.lineSpacingMultiplier, 0f)
        assertEquals(1, view.maxLines)
        assertNull(view.ellipsize)
        assertTrue((view.text as Spanned).getSpans(0, view.text.length,
            CenteredImageSpan::class.java).isEmpty())
    }

    @Test fun bitmapWaitersCannotUpdateAResetOrReboundView() {
        val initial = binding("Old source")
        val view = view()
        view.bind(initial)
        val type = Class.forName("com.ronan.heyboxlite.EmojiRenderer\$Waiter")
        val constructor = type.getDeclaredConstructor(TextView::class.java,
            String::class.java, java.lang.Boolean.TYPE).apply { isAccessible = true }
        val waiter = constructor.newInstance(view, initial.source, initial.darkMode)
        @Suppress("UNCHECKED_CAST")
        val waiters = EmojiRenderer::class.java.getDeclaredField("WAITERS")
            .apply { isAccessible = true }.get(null) as MutableMap<String, MutableList<Any>>
        val notify = EmojiRenderer::class.java.getDeclaredMethod("notifyWaiters",
            String::class.java, java.lang.Boolean.TYPE).apply { isAccessible = true }
        view.reset()
        val empty = view.text
        waiters["old-bitmap"] = mutableListOf(waiter)
        notify.invoke(null, "old-bitmap", true)
        assertNull(view.tag)
        assertSame(empty, view.text)
        view.bind(initial.copy(source = "Current source"))
        val current = view.text
        waiters["old-bitmap"] = mutableListOf(waiter)
        notify.invoke(null, "old-bitmap", true)
        assertEquals("Current source", view.tag)
        assertSame(current, view.text)
    }

    @Test fun queuedEmojiRefreshKeepsDecoratorsAndDoesNotRepeatOrOverwriteItsBinding() {
        val loading = EmojiStore::class.java.getDeclaredField("loading").apply { isAccessible = true }
        val loaded = EmojiStore::class.java.getDeclaredField("catalogLoaded").apply { isAccessible = true }
        val ready = EmojiStore::class.java.getDeclaredField("READY").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val callbacks = ready.get(null) as MutableList<Runnable>
        val previousLoading = loading.getBoolean(null)
        val previousLoaded = loaded.getBoolean(null)
        val previousCallbacks = callbacks.toList()
        try {
            loading.setBoolean(null, true)
            loaded.setBoolean(null, false)
            callbacks.clear()
            val source = RichGameLinkMarkup.normalizeAnchors(
                "<a data-link-type=\"game\" data-game-id=\"42\">Game</a> [compose_missing_937]")
            val binding = binding(source).copy(cy = true)
            val view = view()
            view.bind(binding)
            val callback = callbacks.single()
            val expectedTag = RichGameLinkMarkup.parse("Cy $source").text
            assertEquals(expectedTag, view.tag)
            repeat(10) { view.bind(binding.copy()) }
            assertEquals(1, callbacks.size)
            val initial = view.text
            callback.run()
            val refreshed = view.text
            assertNotSame(initial, refreshed)
            assertCyBadge(refreshed as Spanned)
            assertEquals(Color.BLUE, refreshed.getSpans(0, refreshed.length,
                ForegroundColorSpan::class.java).single().foregroundColor)
            view.bind(binding.copy())
            assertSame(refreshed, view.text)
            assertEquals(expectedTag, view.tag)
            view.reset()
            val pooledText = view.text
            callback.run()
            assertNull(view.tag)
            assertSame(pooledText, view.text)
            view.bind(binding.copy(source = "Replacement", cy = false))
            val replacement = view.text
            callback.run()
            assertSame(replacement, view.text)
            assertEquals("Replacement", view.tag)
        } finally {
            loading.setBoolean(null, previousLoading)
            loaded.setBoolean(null, previousLoaded)
            callbacks.clear()
            callbacks.addAll(previousCallbacks)
        }
    }

    private fun assertCyBadge(text: Spanned) {
        assertTrue(text.getSpans(0, text.length, CenteredImageSpan::class.java)
            .any { text.getSpanStart(it) == 0 && text.getSpanEnd(it) == 2 })
    }

    private fun view() = ComposeRichTextView(ApplicationProvider.getApplicationContext<Context>())

    private fun binding(source: String = "Initial text") = ComposeRichTextBinding(
        source = source, darkMode = false, textColor = Color.WHITE, linkColor = Color.BLUE,
        textSizePx = 12f, lineSpacing = 1.5f, mediumWeight = false, cy = false,
        maxLines = Int.MAX_VALUE, ellipsize = null, density = 1f,
    )
}
