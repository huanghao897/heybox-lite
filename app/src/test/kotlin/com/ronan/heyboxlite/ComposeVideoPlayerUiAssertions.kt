package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import java.io.File
import java.time.Duration
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.robolectric.Shadows.shadowOf

internal class ComposeVideoPlayerUiAssertions(
    private val compose: ComposeTestRule,
    private val activity: () -> ComponentActivity,
) {
    fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    fun reach(tag: String): SemanticsNodeInteraction = node(tag).performScrollTo().assertIsDisplayed()
    fun touch(tag: String, scroll: Boolean = false) {
        val target = if (scroll) reach(tag) else node(tag).assertIsDisplayed()
        assertInPage(target.fetchSemanticsNode().boundsInRoot)
        target.performTouchInput { click(center) }
    }

    fun assertInPage(bounds: Rect, round: Boolean = false) {
        val page = node("video-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Control must have a measurable touch area: $bounds", bounds.width > 0 && bounds.height > 0)
        assertTrue("Control must be inside the page: $bounds / $page",
            bounds.left >= page.left && bounds.right <= page.right &&
                bounds.top >= page.top && bounds.bottom <= page.bottom)
        if (round) {
            val radius = minOf(page.width, page.height) / 2f - 3f * compose.density.density
            listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight).forEach {
                val dx = it.x - page.center.x
                val dy = it.y - page.center.y
                assertTrue("Whole touch target must fit in the circle: $bounds / $page, point=$it",
                    dx * dx + dy * dy < radius * radius)
            }
        }
    }

    fun assertNoOverlap(first: Rect, second: Rect) {
        assertFalse("Controls must not overlap: $first / $second",
            first.left < second.right && second.left < first.right &&
                first.top < second.bottom && second.top < first.bottom)
    }

    fun assertTextFits(tag: String, scroll: Boolean = true) {
        val target = if (scroll) reach(tag) else node(tag)
        val layouts = mutableListOf<TextLayoutResult>()
        target.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Text layout must be available for $tag", layouts.isNotEmpty())
        val semantics = target.fetchSemanticsNode()
        layouts.forEach { layout ->
            val value = layout.layoutInput.text.text
            val width = minOf(layout.size.width, semantics.size.width).toFloat()
            val height = minOf(layout.size.height, semantics.size.height).toFloat()
            assertTrue("No lines for $tag", layout.lineCount > 0)
            assertFalse("Truncated text for $tag", layout.multiParagraph.didExceedMaxLines)
            assertEquals("Missing characters for $tag", value.length,
                layout.getLineEnd(layout.lineCount - 1))
            for (line in 0 until layout.lineCount) {
                assertFalse("Ellipsized text for $tag", layout.isLineEllipsized(line))
                assertTrue("Line clipped for $tag: " +
                    "${layout.getLineLeft(line)},${layout.getLineTop(line)}.." +
                    "${layout.getLineRight(line)},${layout.getLineBottom(line)} / ${width}x${height}",
                    layout.getLineLeft(line) >= -0.5f && layout.getLineRight(line) <= width + 0.5f &&
                        layout.getLineTop(line) >= -0.5f && layout.getLineBottom(line) <= height + 0.5f)
            }
            value.forEachIndexed { offset, character ->
                if (!character.isWhitespace()) {
                    val glyph = layout.getBoundingBox(offset)
                    assertTrue("Character $offset clipped for $tag: $glyph",
                        glyph.left >= -0.5f && glyph.top >= -0.5f &&
                            glyph.right <= width + 0.5f && glyph.bottom <= height + 0.5f)
                    val visibleGlyph = glyph.translate(semantics.positionInRoot)
                    val visible = semantics.boundsInRoot
                    assertTrue("Ancestor clips character $offset for $tag: $visibleGlyph / $visible",
                        visibleGlyph.left >= visible.left - 0.5f && visibleGlyph.top >= visible.top - 0.5f &&
                            visibleGlyph.right <= visible.right + 0.5f && visibleGlyph.bottom <= visible.bottom + 0.5f)
                }
            }
        }
    }

    fun settleAndroidLayout() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        compose.runOnUiThread {
            val view = activity().window.decorView
            view.measure(View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY))
            view.layout(view.left, view.top, view.right, view.bottom)
        }
        compose.waitForIdle()
    }

    fun pixel(pointInWindow: Offset): Int {
        val bitmap = bitmap()
        return try { bitmap.getPixel(pointInWindow.x.roundToInt(), pointInWindow.y.roundToInt()) }
            finally { bitmap.recycle() }
    }

    fun capture(name: String) {
        val bitmap = bitmap()
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            // Hidden controls can leave a valid single-color video fixture.
            assertTrue("Native player rendering must not be blank", pixels.any {
                android.graphics.Color.alpha(it) > 0 && it != android.graphics.Color.BLACK
            })
            val file = File("build/outputs/ui-regression/video-player-$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            println("Native video player screenshot: ${file.absolutePath}")
        } finally { bitmap.recycle() }
    }

    private fun bitmap(): Bitmap {
        settleAndroidLayout()
        return compose.runOnIdle {
            val view = activity().window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
    }
}

internal fun videoPlayerDescendants(view: View): Sequence<View> = sequence {
    yield(view)
    if (view is ViewGroup) for (index in 0 until view.childCount)
        yieldAll(videoPlayerDescendants(view.getChildAt(index)))
}
