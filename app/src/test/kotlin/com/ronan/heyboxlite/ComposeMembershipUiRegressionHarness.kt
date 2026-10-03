package com.ronan.heyboxlite

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.junit.runner.Description

internal abstract class ComposeMembershipUiRegressionHarness {
    @get:Rule(order = 0) val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule(order = 1) val testName = TestName()
    private var contentInstalled = false
    private var variant = "dark"

    // Run before the Compose rule disposes the Activity so layout failures leave native evidence.
    @get:Rule(order = 2) val failureScreenshot = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            if (contentInstalled) runCatching { capture("failure") }
                .exceptionOrNull()?.let(error::addSuppressed)
        }
    }

    protected fun theme(largeLight: Boolean = false): ComposeThemeState {
        val base = composePreviewTheme(compose.activity.resources.configuration.isScreenRound)
        return if (!largeLight) base else base.copy(dark = false,
            background = Color.White, panel = Color(0xFFF2F2F2), panelElevated = Color(0xFFE5E5E5),
            text = Color.Black, muted = Color.DarkGray, subtle = Color.Gray,
            hairline = Color(0xFFCCCCCC), accent = Color(0xFF414141), onAccent = Color.White,
            textScale = 1.4f, uiScale = 1.2f)
    }

    protected fun setScreen(
        theme: ComposeThemeState = theme(),
        systemFontScale: Float = 1f,
        restoration: StateRestorationTester? = null,
        content: @Composable () -> Unit,
    ) {
        variant = (if (theme.dark) "dark" else "light") +
            "-text${theme.textScale}-ui${theme.uiScale}-font$systemFontScale"
        val screen: @Composable () -> Unit = {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, systemFontScale)) {
                HeyboxComposeTheme(theme, content)
            }
        }
        if (restoration == null) compose.setContent(screen) else restoration.setContent(screen)
        contentInstalled = true
        compose.waitForIdle()
        val config = compose.activity.resources.configuration
        val view = compose.activity.window.decorView
        assertEquals("mdpi fixture width", config.screenWidthDp, view.width)
        assertEquals("mdpi fixture height", config.screenHeightDp, view.height)
        capture("initial")
    }

    protected fun text(value: String, unmerged: Boolean = false) =
        compose.onNodeWithText(value, useUnmergedTree = unmerged)

    protected fun tag(value: String) = compose.onNodeWithTag(value)

    protected fun description(value: String) = compose.onNodeWithContentDescription(value)

    protected fun capture(step: String) {
        compose.runOnIdle {
            captureMembershipUi(compose.activity,
                "${javaClass.simpleName}-${testName.methodName}-$variant-$step")
        }
    }

    protected fun reach(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        node.performScrollTo().assertIsDisplayed()
        val target = node.fetchSemanticsNode()
        val scroller = generateSequence(target.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }
        if (scroller != null) {
            val window = boundsInWindow(target)
            val viewport = scroller.boundsInRoot.translate(scroller.positionInWindow - scroller.positionInRoot)
            val minimumCenter = viewport.top + window.height / 2f
            val maximumCenter = viewport.bottom - window.height / 2f
            // Prefer the circle's center, without moving tall labels behind the fixed header.
            val center = if (minimumCenter <= maximumCenter) {
                (compose.activity.window.decorView.height / 2f).coerceIn(minimumCenter, maximumCenter)
            } else viewport.center.y
            val delta = window.center.y - center
            compose.onNode(SemanticsMatcher("nearest scroll parent ${scroller.id}") {
                it.id == scroller.id
            }, useUnmergedTree = true).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
            compose.waitForIdle()
        }
        return node.assertIsDisplayed()
    }

    protected fun assertLabelFits(value: String) {
        val node = reach(text(value, unmerged = true))
        compose.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("TextLayoutResult missing for $value", layouts.isNotEmpty())
        val semantics = node.fetchSemanticsNode()
        layouts.forEach { layout ->
            val diagnostic = writeTextDiagnostic(value, semantics, layout)
            val context = "label $value; diagnostic: ${diagnostic.absolutePath}"
            assertEquals("Stale or unrelated TextLayoutResult: $context", value, layout.layoutInput.text.text)
            assertTrue("No laid-out lines: $context", layout.lineCount > 0)
            assertFalse("Text exceeds maxLines: $context", layout.multiParagraph.didExceedMaxLines)
            assertEquals("Missing final characters: $context", value.length,
                layout.getLineEnd(layout.lineCount - 1))
            val width = minOf(layout.size.width, semantics.size.width).toFloat()
            val height = minOf(layout.size.height, semantics.size.height).toFloat()
            val origin = semantics.positionInWindow
            val visible = semantics.boundsInRoot.translate(origin - semantics.positionInRoot)
            for (line in 0 until layout.lineCount) {
                assertFalse("Ellipsized line $line: $context", layout.isLineEllipsized(line))
                assertTrue("Line $line exceeds measured width: $context",
                    layout.getLineLeft(line) >= -0.5f && layout.getLineRight(line) <= width + 0.5f)
                assertTrue("Line $line exceeds measured height: $context",
                    layout.getLineTop(line) >= -0.5f && layout.getLineBottom(line) <= height + 0.5f)
            }
            value.forEachIndexed { offset, character ->
                if (!character.isWhitespace()) {
                    val line = layout.getLineForOffset(offset)
                    assertTrue("Character $offset is outside the visible line range: $context",
                        offset >= layout.getLineStart(line) && offset < layout.getLineEnd(line, visibleEnd = true))
                    val glyph = layout.getBoundingBox(offset)
                    assertTrue("Invalid character bounds at $offset: $context",
                        glyph.left.isFinite() && glyph.top.isFinite() && glyph.right.isFinite() &&
                            glyph.bottom.isFinite() && glyph.width >= 0f && glyph.height > 0f)
                    assertTrue("Character $offset is clipped by its Text node: $glyph; $context",
                        glyph.left >= -0.5f && glyph.top >= -0.5f &&
                            glyph.right <= width + 0.5f && glyph.bottom <= height + 0.5f)
                    val windowGlyph = glyph.translate(origin)
                    assertTrue("Character $offset is clipped by an ancestor: $windowGlyph / $visible; $context",
                        windowGlyph.left >= visible.left - 0.5f && windowGlyph.top >= visible.top - 0.5f &&
                            windowGlyph.right <= visible.right + 0.5f && windowGlyph.bottom <= visible.bottom + 0.5f)
                    assertInDisplay(windowGlyph, "$context; character $offset", corners = true)
                }
            }
            // A shrunk Text node can retain a wider paragraph envelope. Validate every visible
            // line and character, not that envelope; maxLines, ellipsis and clipping still fail.
            if (layout.hasVisualOverflow) println("Paragraph overflow flag with complete, unclipped characters: " +
                diagnostic.absolutePath)
        }
        assertVisibleTextDoesNotOverlap()
    }

    private fun writeTextDiagnostic(value: String, node: SemanticsNode, layout: TextLayoutResult): File {
        val lines = JSONArray()
        for (line in 0 until layout.lineCount) lines.put(JSONObject()
            .put("line", line).put("left", layout.getLineLeft(line).toDouble()).put("right", layout.getLineRight(line).toDouble())
            .put("top", layout.getLineTop(line).toDouble()).put("bottom", layout.getLineBottom(line).toDouble())
            .put("start", layout.getLineStart(line)).put("logicalEnd", layout.getLineEnd(line))
            .put("visibleEnd", layout.getLineEnd(line, visibleEnd = true))
            .put("ellipsized", layout.isLineEllipsized(line)))
        val characters = JSONArray()
        value.forEachIndexed { offset, character ->
            if (!character.isWhitespace() && offset < layout.layoutInput.text.length) {
                characters.put(JSONObject().put("offset", offset).put("character", character.toString())
                    .put("bounds", rectJson(layout.getBoundingBox(offset))))
            }
        }
        val diagnostic = JSONObject().put("label", value).put("test", testName.methodName)
            .put("variant", variant).put("layoutText", layout.layoutInput.text.text)
            .put("nodeSize", JSONObject().put("width", node.size.width).put("height", node.size.height))
            .put("layoutSize", JSONObject().put("width", layout.size.width).put("height", layout.size.height))
            .put("paragraphWidth", layout.multiParagraph.width.toDouble()).put("paragraphHeight", layout.multiParagraph.height.toDouble())
            .put("didOverflowWidth", layout.didOverflowWidth).put("didOverflowHeight", layout.didOverflowHeight)
            .put("hasVisualOverflow", layout.hasVisualOverflow).put("didExceedMaxLines", layout.multiParagraph.didExceedMaxLines)
            .put("constraints", layout.layoutInput.constraints.toString()).put("maxLines", layout.layoutInput.maxLines)
            .put("nodeInWindow", rectJson(boundsInWindow(node))).put("clippedNodeInRoot", rectJson(node.boundsInRoot))
            .put("lines", lines).put("characters", characters)
        val file = File("build/outputs/ui-regression/checkin-${javaClass.simpleName}-" +
            "${testName.methodName}-$variant-text-layout-${UUID.randomUUID()}.json")
        requireNotNull(file.parentFile).let { assertTrue(it.isDirectory || it.mkdirs()) }
        file.writeText(diagnostic.toString(2), Charsets.UTF_8)
        println("Text geometry: $value; node=${node.size}; layout=${layout.size}; " +
            "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}; " +
            "overflow=${layout.hasVisualOverflow}; lines=$lines; diagnostic=${file.absolutePath}")
        return file
    }

    private fun rectJson(rect: Rect) = JSONObject().put("left", rect.left.toDouble()).put("top", rect.top.toDouble())
        .put("right", rect.right.toDouble()).put("bottom", rect.bottom.toDouble())

    protected fun assertLabelsSeparated(first: String, second: String) {
        reach(text(second, unmerged = true))
        val a = boundsInWindow(text(first, unmerged = true).fetchSemanticsNode())
        val b = boundsInWindow(text(second, unmerged = true).fetchSemanticsNode())
        assertFalse("Overlapping fields: $first / $second", overlaps(a, b))
    }

    protected fun tapReachable(value: String) {
        val node = reach(text(value))
        capture("before-tap-$value")
        assertInDisplay(boundsInWindow(node.fetchSemanticsNode()), "control $value", corners = false)
        assertLabelFits(value)
        // Touch injection exercises hit testing; performClick alone can activate an off-screen semantic action.
        node.performTouchInput { click() }
        compose.waitForIdle()
    }

    protected fun assertBackReachable() {
        val back = description("\u8fd4\u56de").assertIsDisplayed()
        assertInDisplay(boundsInWindow(back.fetchSemanticsNode()), "back button", corners = false)
        back.performTouchInput { click() }
        compose.waitForIdle()
    }

    protected fun assertVisibleTextDoesNotOverlap() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true).fetchSemanticsNodes().filter { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() } &&
                node.boundsInRoot.width > 0.5f && node.boundsInRoot.height > 0.5f
        }
        nodes.forEachIndexed { index, first ->
            nodes.drop(index + 1).forEach { second ->
                assertFalse("Text overlaps: ${first.config.getOrNull(SemanticsProperties.Text)} / " +
                    "${second.config.getOrNull(SemanticsProperties.Text)}",
                    overlaps(first.boundsInRoot, second.boundsInRoot))
            }
        }
    }

    protected fun assertInDisplay(bounds: Rect, label: String, corners: Boolean) {
        val view = compose.activity.window.decorView
        assertTrue("$label must have non-empty bounds: $bounds", bounds.width > 0 && bounds.height > 0)
        assertTrue("$label is outside the window: $bounds (${view.width}x${view.height})",
            bounds.left >= -0.5f && bounds.top >= -0.5f &&
                bounds.right <= view.width + 0.5f && bounds.bottom <= view.height + 0.5f)
        if (!compose.activity.resources.configuration.isScreenRound) return
        val points = if (corners) listOf(bounds.left to bounds.top, bounds.right to bounds.top,
            bounds.left to bounds.bottom, bounds.right to bounds.bottom)
            else listOf(bounds.center.x to bounds.center.y)
        val radius = minOf(view.width, view.height) / 2f - 3f
        points.forEach { (x, y) ->
            val dx = x - view.width / 2f
            val dy = y - view.height / 2f
            assertTrue("$label is clipped by the circular display: $bounds", dx * dx + dy * dy < radius * radius)
        }
    }

    private fun boundsInWindow(node: SemanticsNode): Rect = Rect(node.positionInWindow.x,
        node.positionInWindow.y, node.positionInWindow.x + node.size.width,
        node.positionInWindow.y + node.size.height)

    private fun overlaps(a: Rect, b: Rect): Boolean =
        minOf(a.right, b.right) - maxOf(a.left, b.left) > 0.5f &&
            minOf(a.bottom, b.bottom) - maxOf(a.top, b.top) > 0.5f
}
