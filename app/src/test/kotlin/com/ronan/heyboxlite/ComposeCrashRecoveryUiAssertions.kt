package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal class ComposeCrashRecoveryUiAssertions(
    private val compose: ComposeTestRule,
    private val activity: () -> ComponentActivity,
    private val name: () -> String,
) {
    fun capture(step: String) {
        compose.runOnIdle {
            val view = activity().window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val file = artifact(step + "-" + view.width + "x" + view.height, "png")
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                println("Native offline crash recovery screenshot: " + file.absolutePath)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("Native page must not be blank: $file", pixels.toSet().size > 5)
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun reach(tag: String): SemanticsNodeInteraction = reach(compose.onNodeWithTag(tag))

    fun reach(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        node.performScrollTo()
        val target = node.fetchSemanticsNode()
        val scroller = generateSequence(target.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }
        if (scroller != null) {
            val window = rawWindowBounds(target)
            val viewport = scroller.boundsInRoot.translate(scroller.positionInWindow - scroller.positionInRoot)
            val delta = window.center.y - viewport.center.y
            compose.onNode(SemanticsMatcher("scroll parent " + scroller.id) { it.id == scroller.id },
                useUnmergedTree = true).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
            compose.waitForIdle()
        }
        return node.assertIsDisplayed()
    }

    fun assertContentInDisplay() = assertInDisplay(
        rawWindowBounds(compose.onNodeWithTag("crash-content").fetchSemanticsNode()), "safe content")

    fun assertFullyVisible(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val raw = rawWindowBounds(node)
        val clipped = node.boundsInRoot.translate(node.positionInWindow - node.positionInRoot)
        assertInDisplay(raw, tag)
        val context = "$tag must not be clipped by a scroll parent"
        assertEquals(context, raw.left, clipped.left, 0.5f)
        assertEquals(context, raw.top, clipped.top, 0.5f)
        assertEquals(context, raw.right, clipped.right, 0.5f)
        assertEquals(context, raw.bottom, clipped.bottom, 0.5f)
    }

    fun touch(tag: String, label: String? = null) {
        val node = reach(tag)
        if (label != null) assertLabelComplete(label)
        assertFullyVisible(tag)
        assertVisibleTextDoesNotOverlap()
        capture("before-$tag")
        node.performTouchInput { click() }
        compose.waitForIdle()
    }

    fun assertLabelComplete(value: String) {
        val node = reach(compose.onNodeWithText(value, useUnmergedTree = true))
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("No text layout for $value", layouts.isNotEmpty())
        val semantics = node.fetchSemanticsNode()
        layouts.forEach { layout ->
            val diagnostic = diagnostic(value, semantics, layout)
            val context = value + "; diagnostic: " + diagnostic.absolutePath
            assertEquals(context, value, layout.layoutInput.text.text)
            assertTrue(context, layout.lineCount > 0)
            assertFalse("maxLines lost characters: $context", layout.multiParagraph.didExceedMaxLines)
            assertEquals("Missing final characters: $context", value.length,
                layout.getLineEnd(layout.lineCount - 1))
            val width = minOf(semantics.size.width, layout.size.width).toFloat()
            val height = minOf(semantics.size.height, layout.size.height).toFloat()
            val visible = semantics.boundsInRoot.translate(semantics.positionInWindow - semantics.positionInRoot)
            for (line in 0 until layout.lineCount) {
                assertFalse("Ellipsis: $context", layout.isLineEllipsized(line))
                assertTrue("Line width: $context", layout.getLineLeft(line) >= -0.5f &&
                    layout.getLineRight(line) <= width + 0.5f)
                assertTrue("Line height: $context", layout.getLineTop(line) >= -0.5f &&
                    layout.getLineBottom(line) <= height + 0.5f)
            }
            value.forEachIndexed { offset, character ->
                if (!character.isWhitespace()) {
                    val line = layout.getLineForOffset(offset)
                    assertTrue("Character range $offset: $context", offset >= layout.getLineStart(line) &&
                        offset < layout.getLineEnd(line, visibleEnd = true))
                    val glyph = layout.getBoundingBox(offset)
                    assertTrue("Character clipped in Text $offset: $context", glyph.left >= -0.5f &&
                        glyph.top >= -0.5f && glyph.right <= width + 0.5f && glyph.bottom <= height + 0.5f)
                    val windowGlyph = glyph.translate(semantics.positionInWindow)
                    assertTrue("Character clipped in ancestor $offset: $context",
                        windowGlyph.left >= visible.left - 0.5f && windowGlyph.top >= visible.top - 0.5f &&
                            windowGlyph.right <= visible.right + 0.5f && windowGlyph.bottom <= visible.bottom + 0.5f)
                    assertInDisplay(windowGlyph, "character $offset; $context")
                }
            }
        }
    }

    fun assertVisibleTextDoesNotOverlap() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true).fetchSemanticsNodes().filter {
            it.boundsInRoot.width > 0.5f && it.boundsInRoot.height > 0.5f &&
                it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text.isNotBlank() }
        }
        nodes.forEachIndexed { index, first ->
            nodes.drop(index + 1).forEach { second ->
                val a = first.boundsInRoot
                val b = second.boundsInRoot
                assertFalse("Text overlap: " + first.config + " / " + second.config,
                    minOf(a.right, b.right) - maxOf(a.left, b.left) > 0.5f &&
                        minOf(a.bottom, b.bottom) - maxOf(a.top, b.top) > 0.5f)
            }
        }
    }

    private fun assertInDisplay(bounds: Rect, context: String) {
        val view = activity().window.decorView
        assertTrue("Empty bounds: $context", bounds.width > 0f && bounds.height > 0f)
        assertTrue("Outside window: $bounds; $context", bounds.left >= -0.5f && bounds.top >= -0.5f &&
            bounds.right <= view.width + 0.5f && bounds.bottom <= view.height + 0.5f)
        if (!activity().resources.configuration.isScreenRound) return
        val radius = minOf(view.width, view.height) / 2f - 2f
        listOf(bounds.left to bounds.top, bounds.right to bounds.top,
            bounds.left to bounds.bottom, bounds.right to bounds.bottom).forEach { (x, y) ->
            val dx = x - view.width / 2f
            val dy = y - view.height / 2f
            assertTrue("Outside circle: $bounds; $context", dx * dx + dy * dy <= radius * radius)
        }
    }

    private fun diagnostic(value: String, node: SemanticsNode, layout: TextLayoutResult): File {
        val lines = JSONArray()
        for (line in 0 until layout.lineCount) lines.put(JSONObject()
            .put("left", layout.getLineLeft(line).toDouble()).put("right", layout.getLineRight(line).toDouble())
            .put("top", layout.getLineTop(line).toDouble()).put("bottom", layout.getLineBottom(line).toDouble())
            .put("start", layout.getLineStart(line)).put("end", layout.getLineEnd(line))
            .put("visibleEnd", layout.getLineEnd(line, visibleEnd = true))
            .put("ellipsized", layout.isLineEllipsized(line)))
        val data = JSONObject().put("text", value).put("nodeSize", node.size.toString())
            .put("textSize", layout.size.toString()).put("paragraphWidth", layout.multiParagraph.width.toDouble())
            .put("paragraphHeight", layout.multiParagraph.height.toDouble())
            .put("overflowFlag", layout.hasVisualOverflow).put("maxLinesExceeded", layout.multiParagraph.didExceedMaxLines)
            .put("lines", lines)
        return artifact("text-layout", "json").also { it.writeText(data.toString(2), Charsets.UTF_8) }
    }

    private fun artifact(step: String, extension: String): File {
        val safe = (name() + "-" + step).replace(Regex("[^A-Za-z0-9._-]"), "-")
        return File("build/outputs/ui-regression/crash-" + safe + "-" + UUID.randomUUID() + "." + extension).also {
            val parent = requireNotNull(it.parentFile)
            assertTrue(parent.isDirectory || parent.mkdirs())
        }
    }

    private fun rawWindowBounds(node: SemanticsNode) = Rect(node.positionInWindow.x, node.positionInWindow.y,
        node.positionInWindow.x + node.size.width, node.positionInWindow.y + node.size.height)
}
