package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeInlineTextUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val images = ComposeInlineTextTestImages()
    private val magenta = image(android.graphics.Color.MAGENTA)
    private val green = image(android.graphics.Color.GREEN)

    @Test fun cachedEmojiCyAndBlueGameIconRenderTogetherWithoutNativeTextViews() {
        val catalog = ComposeInlineTextTestCatalog().apply { register("cube_wave", "wave") }
        images.cache["wave" to 16] = magenta
        compose.setContent {
            Box(Modifier.width(200.dp).background(Color.Black)) {
                ComposeInlineText("A [cube_wave] \uE100Game\uE101 Z", false, Color.White, Color.Blue,
                    Modifier.fillMaxWidth().testTag("inline"), fontWeight = FontWeight.Bold, cy = true,
                    imageLoader = images, emojiCatalog = catalog)
            }
        }

        val layout = layout()
        assertEquals("Cy A [cube_wave] \uFFFCGame Z", layout.layoutInput.text.text)
        assertEquals(3, layout.placeholderRects.filterNotNull().size)
        assertEquals(listOf(15f, 16f, 13f), layout.placeholderRects.filterNotNull().map { it.height })
        assertEquals(32f, layout.placeholderRects.filterNotNull()[1].width, 0.01f)
        assertEquals(FontWeight.Bold, layout.layoutInput.style.fontWeight)
        assertEquals(12.sp, layout.layoutInput.style.fontSize)
        assertEquals(18.sp, layout.layoutInput.style.lineHeight)
        assertEquals(Color.Blue, layout.layoutInput.text.spanStyles.single().item.color)
        assertTrue(images.requests.isEmpty())
        assertContains(android.graphics.Color.MAGENTA)
        assertContains(android.graphics.Color.BLUE)
        compose.runOnIdle { assertNoNativeText(compose.activity.findViewById(android.R.id.content)) }
    }

    @Test fun lateCatalogAndImageCompletionKeepCyGameColorAndReadableLoadingFallback() {
        val catalog = ComposeInlineTextTestCatalog(loaded = false)
        compose.setContent {
            Box(Modifier.width(200.dp).background(Color.Black)) {
                ComposeInlineText("[cube_late] \uE100Game\uE101", false, Color.White, Color.Blue,
                    Modifier.testTag("inline"), cy = true, imageLoader = images, emojiCatalog = catalog)
            }
        }
        assertEquals(2, layout().placeholderRects.filterNotNull().size)
        assertTrue(images.requests.isEmpty())
        compose.runOnIdle {
            catalog.register("cube_late", "late-image")
            catalog.loaded = true
            catalog.ready.single().run()
        }
        compose.waitForIdle()
        assertEquals(2, layout().placeholderRects.filterNotNull().size)
        assertEquals("Cy [cube_late] \uFFFCGame", layout().layoutInput.text.text)
        assertEquals("late-image", images.requests.single().url)
        compose.runOnIdle { images.requests.single().complete(magenta) }

        val loaded = layout()
        assertEquals(3, loaded.placeholderRects.filterNotNull().size)
        assertEquals(Color.Blue, loaded.layoutInput.text.spanStyles.single().item.color)
        assertContains(android.graphics.Color.MAGENTA)
    }

    @Test fun replacedOrDisposedContentCannotAcceptOldCatalogOrImageCallbacks() {
        val catalog = ComposeInlineTextTestCatalog(loaded = false)
        val source = mutableStateOf("[cube_old]")
        val visible = mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(200.dp).background(Color.Black)) {
                if (visible.value) ComposeInlineText(source.value, false, Color.White, Color.Blue,
                    Modifier.testTag("inline"), imageLoader = images, emojiCatalog = catalog)
            }
        }
        compose.waitForIdle()
        val oldReady = catalog.ready.single()
        compose.runOnIdle { source.value = "[cube_current]" }
        compose.waitForIdle()
        assertEquals(2, catalog.ready.size)
        compose.runOnIdle {
            catalog.register("cube_old", "old")
            catalog.register("cube_current", "current")
            catalog.loaded = true
            oldReady.run()
        }
        assertTrue("A replaced source must not reparse on its old catalog callback", images.requests.isEmpty())
        compose.runOnIdle { catalog.ready.last().run() }
        compose.waitForIdle()
        val current = images.requests.single()
        assertEquals("current", current.url)
        compose.runOnIdle {
            catalog.register("cube_next", "next")
            source.value = "[cube_next]"
        }
        compose.waitForIdle()
        assertTrue(current.cancelled)
        compose.runOnIdle { current.complete(magenta) }
        assertMissing(android.graphics.Color.MAGENTA)
        val next = images.requests.last()
        assertEquals("next", next.url)
        compose.runOnIdle { next.complete(green) }
        assertContains(android.graphics.Color.GREEN)
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertTrue(next.cancelled)
        compose.runOnIdle {
            next.complete(magenta)
            catalog.ready.forEach { it.run() }
        }
        assertMissing(android.graphics.Color.MAGENTA)
        assertMissing(android.graphics.Color.GREEN)
    }

    @Test fun themeAndFontSizeChangesCancelLoadsAndNeverReuseThePreviousEmoji() {
        val catalog = ComposeInlineTextTestCatalog().apply { register("cube_wave", "light", "dark") }
        val dark = mutableStateOf(false)
        val size = mutableStateOf(12.sp)
        compose.setContent {
            Box(Modifier.size(200.dp).background(Color.Black)) {
                ComposeInlineText("[cube_wave]", dark.value, Color.White, Color.Blue,
                    Modifier.testTag("inline"), fontSize = size.value, lineHeight = 32.sp,
                    imageLoader = images, emojiCatalog = catalog)
            }
        }
        compose.waitForIdle()
        val light = images.requests.single()
        compose.runOnIdle { light.complete(magenta) }
        assertContains(android.graphics.Color.MAGENTA)
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
        assertTrue(light.cancelled)
        assertMissing(android.graphics.Color.MAGENTA)
        val initialDark = images.requests.last()
        assertEquals("dark", initialDark.url)
        compose.runOnIdle { size.value = 24.sp }
        compose.waitForIdle()
        assertTrue(initialDark.cancelled)
        val enlarged = images.requests.last()
        assertEquals(28, enlarged.targetPx)
        compose.runOnIdle {
            light.complete(magenta)
            initialDark.complete(magenta)
            enlarged.complete(green)
        }
        assertContains(android.graphics.Color.GREEN)
        assertMissing(android.graphics.Color.MAGENTA)
        assertEquals(28f, layout().placeholderRects.single()!!.height, 0.01f)
        assertEquals(24.sp, layout().layoutInput.style.fontSize)
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun narrowRoundLargeTextKeepsInlineImagesInsideTheChordAndEllipsizesTheLastLine() {
        val catalog = ComposeInlineTextTestCatalog().apply { register("cube_wave", "wave") }
        images.cache["wave" to 21] = magenta
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                Box(Modifier.size(192.dp).clip(CircleShape).background(Color.Black),
                    contentAlignment = Alignment.Center) {
                    ComposeInlineText("[cube_wave] \uE100Game\uE101 " + "readable text ".repeat(20),
                        false, Color.White, Color.Blue, Modifier.width(136.dp).testTag("inline"),
                        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                        cy = true, maxLines = 3, ellipsize = TextUtils.TruncateAt.END,
                        imageLoader = images, emojiCatalog = catalog)
                }
            }
        }
        val layout = layout()
        assertEquals(3, layout.lineCount)
        assertTrue(layout.isLineEllipsized(2))
        assertEquals(3, layout.placeholderRects.filterNotNull().size)
        val bounds = compose.onNodeWithTag("inline").fetchSemanticsNode().boundsInRoot
        fun insideCircle(x: Float, y: Float) {
            val dx = bounds.left + x - 96f
            val dy = bounds.top + y - 96f
            assertTrue("Visible inline text must fit the round screen", dx * dx + dy * dy <= 96f * 96f)
        }
        for (line in 0 until layout.lineCount) {
            assertTrue(layout.getLineLeft(line) >= -1f)
            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            insideCircle(layout.getLineLeft(line), layout.getLineTop(line))
            insideCircle(layout.getLineRight(line), layout.getLineBottom(line))
        }
        for (rect in layout.placeholderRects.filterNotNull()) {
            assertTrue(rect.left >= 0f && rect.right <= layout.size.width)
            assertTrue(rect.top >= 0f && rect.bottom <= layout.size.height)
            insideCircle(rect.left, rect.top)
            insideCircle(rect.right, rect.bottom)
        }
        assertContains(android.graphics.Color.MAGENTA)
    }

    private fun layout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("inline").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        return layouts.single()
    }

    private fun assertNoNativeText(view: View) {
        var type: Class<*>? = view.javaClass
        while (type != null) {
            assertNotEquals("android.widget.TextView", type.name)
            type = type.superclass
        }
        if (view is ViewGroup) repeat(view.childCount) { assertNoNativeText(view.getChildAt(it)) }
    }

    private fun image(color: Int) = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }

    private fun assertContains(color: Int) = assertTrue("Expected rendered pixels of $color", pixels().any { it == color })
    private fun assertMissing(color: Int) = assertFalse("Stale image must not render pixels of $color", pixels().any { it == color })

    private fun pixels(): IntArray = compose.runOnIdle {
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
    }
}
