package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TestName
import org.robolectric.shadows.ShadowDialog

internal abstract class ComposeWatchActionButtonTestHarness {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val testName = TestName()
    private var diagnosticSequence = 0

    protected fun theme(dark: Boolean = true, large: Boolean = false,
                        uiScale: Float? = null): ComposeThemeState {
        val round = compose.activity.resources.configuration.isScreenRound
        return composeThemeState(ThemeTokens.of(dark, 0, 0),
            uiScale ?: if (round) 0.82f else 1f, if (large) 1.4f else 1f, round)
    }

    protected fun show(theme: ComposeThemeState, large: Boolean = false,
                       content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (large) 1.25f else 1f)) {
                HeyboxComposeTheme(theme) {
                    Box(Modifier.fillMaxSize().background(theme.background),
                        contentAlignment = Alignment.Center) { content() }
                }
            }
        }
        compose.waitForIdle()
        val config = compose.activity.resources.configuration
        val view = compose.activity.window.decorView
        assertEquals("mdpi fixture width", config.screenWidthDp, view.width)
        assertEquals("mdpi fixture height", config.screenHeightDp, view.height)
    }

    protected fun text(label: String, unmerged: Boolean = false) =
        compose.onNodeWithText(label, useUnmergedTree = unmerged)

    protected fun reach(label: String): SemanticsNodeInteraction {
        val target = text(label).performScrollTo().assertIsDisplayed()
        val node = target.fetchSemanticsNode()
        val parent = generateSequence(node.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }
        if (parent != null) {
            val window = bounds(target)
            val viewport = parent.boundsInRoot.translate(parent.positionInWindow - parent.positionInRoot)
            val minCenter = viewport.top + window.height / 2f
            val maxCenter = viewport.bottom - window.height / 2f
            if (minCenter <= maxCenter) {
                val center = (compose.activity.window.decorView.height / 2f).coerceIn(minCenter, maxCenter)
                compose.onNode(SemanticsMatcher("scroll parent ${parent.id}") { it.id == parent.id },
                    useUnmergedTree = true).performSemanticsAction(SemanticsActions.ScrollBy) {
                    it(0f, window.center.y - center)
                }
            }
        }
        return target.assertIsDisplayed()
    }

    protected fun assertFits(label: String, wrapped: Boolean = false) {
        val step = "layout-${diagnosticSequence++}"
        capture(step).recycle()
        val node = text(label, unmerged = true)
        val layout = textLayout(node)
        val semantics = node.fetchSemanticsNode()
        val evidence = writeTextDiagnostic(label, semantics, layout, step)
        val context = "$label; ${evidence.absolutePath}"
        assertEquals("Unrelated Text layout: $context", label, layout.layoutInput.text.text)
        assertTrue("No laid-out lines: $context", layout.lineCount > 0)
        assertFalse("Label exceeds maxLines: $context", layout.multiParagraph.didExceedMaxLines)
        assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1))
        if (wrapped) assertTrue("Long label must wrap", layout.lineCount > 1)
        val width = minOf(layout.size.width, semantics.size.width).toFloat()
        val height = minOf(layout.size.height, semantics.size.height).toFloat()
        val visible = semantics.boundsInRoot.translate(semantics.positionInWindow - semantics.positionInRoot)
        for (line in 0 until layout.lineCount) {
            assertFalse("Ellipsized label: $context", layout.isLineEllipsized(line))
            assertTrue("Line exceeds Text width: $context", layout.getLineLeft(line) >= -0.5f &&
                layout.getLineRight(line) <= width + 0.5f)
            assertTrue("Line exceeds Text height: $context", layout.getLineTop(line) >= -0.5f &&
                layout.getLineBottom(line) <= height + 0.5f)
        }
        label.forEachIndexed { offset, character ->
            if (!character.isWhitespace()) {
                val line = layout.getLineForOffset(offset)
                assertTrue("Missing character $offset: $context", offset >= layout.getLineStart(line) &&
                    offset < layout.getLineEnd(line, visibleEnd = true))
                val glyph = layout.getBoundingBox(offset)
                assertTrue("Invalid glyph $offset: $context", glyph.left.isFinite() && glyph.top.isFinite() &&
                    glyph.right.isFinite() && glyph.bottom.isFinite() && glyph.width > 0 && glyph.height > 0)
                assertTrue("Glyph $offset clipped by Text: $glyph; $context", glyph.left >= -0.5f &&
                    glyph.top >= -0.5f && glyph.right <= width + 0.5f && glyph.bottom <= height + 0.5f)
                val windowGlyph = transformedBounds(semantics, glyph)
                assertTrue("Glyph $offset clipped by ancestor: $windowGlyph / $visible; $context",
                    windowGlyph.left >= visible.left - 0.5f && windowGlyph.top >= visible.top - 0.5f &&
                        windowGlyph.right <= visible.right + 0.5f && windowGlyph.bottom <= visible.bottom + 0.5f)
                assertInDisplay(windowGlyph)
            }
        }
        // Compose can shrink Text while retaining its original paragraph width.
        // Only that envelope-only overflow is allowed after every glyph has passed.
        if (layout.hasVisualOverflow) {
            assertTrue("Unexplained overflow: $context", layout.didOverflowWidth &&
                layout.multiParagraph.width > width && !layout.didOverflowHeight)
        }
        assertInDisplay(bounds(node))
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("A real Text layout is required", 1, layouts.size)
        val raw = layouts.single()
        writeTextDiagnostic(raw.layoutInput.text.text, node.fetchSemanticsNode(), raw,
            "raw-${diagnosticSequence++}")
        return paintedTextLayout(raw)
    }

    private fun transformedBounds(node: SemanticsNode, rect: Rect): Rect {
        val coordinates = node.layoutInfo.coordinates
        val corners = listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)
            .map(coordinates::localToWindow)
        return Rect(corners.minOf { it.x }, corners.minOf { it.y },
            corners.maxOf { it.x }, corners.maxOf { it.y })
    }

    private fun writeTextDiagnostic(label: String, node: SemanticsNode,
                                    layout: TextLayoutResult, step: String): File {
        val lines = JSONArray()
        for (line in 0 until layout.lineCount) lines.put(JSONObject()
            .put("left", layout.getLineLeft(line).toDouble()).put("right", layout.getLineRight(line).toDouble())
            .put("top", layout.getLineTop(line).toDouble()).put("bottom", layout.getLineBottom(line).toDouble())
            .put("baseline", layout.getLineBaseline(line).toDouble()).put("start", layout.getLineStart(line))
            .put("end", layout.getLineEnd(line)).put("visibleEnd", layout.getLineEnd(line, true))
            .put("ellipsized", layout.isLineEllipsized(line)))
        val glyphs = JSONArray()
        layout.layoutInput.text.text.forEachIndexed { offset, character ->
            if (!character.isWhitespace()) glyphs.put(JSONObject().put("offset", offset)
                .put("character", character.toString()).put("local", rectJson(layout.getBoundingBox(offset)))
                .put("window", rectJson(transformedBounds(node, layout.getBoundingBox(offset)))))
        }
        val diagnostic = JSONObject().put("label", label).put("layoutText", layout.layoutInput.text.text)
            .put("nodeWidth", node.size.width).put("nodeHeight", node.size.height)
            .put("layoutWidth", layout.size.width).put("layoutHeight", layout.size.height)
            .put("paragraphWidth", layout.multiParagraph.width.toDouble())
            .put("paragraphHeight", layout.multiParagraph.height.toDouble())
            .put("hasVisualOverflow", layout.hasVisualOverflow).put("didOverflowWidth", layout.didOverflowWidth)
            .put("didOverflowHeight", layout.didOverflowHeight).put("maxLines", layout.layoutInput.maxLines)
            .put("constraints", layout.layoutInput.constraints.toString())
            .put("style", layout.layoutInput.style.toString()).put("clippedBounds", rectJson(node.boundsInRoot))
            .put("lines", lines).put("glyphs", glyphs)
        val file = File("build/outputs/ui-regression/buttons/${testName.methodName}-$step.json")
        requireNotNull(file.parentFile).let { assertTrue(it.isDirectory || it.mkdirs()) }
        file.writeText(diagnostic.toString(2), Charsets.UTF_8)
        println("Button Text geometry: ${file.absolutePath}; overflow=${layout.hasVisualOverflow}")
        return file
    }

    private fun rectJson(rect: Rect) = JSONObject().put("left", rect.left.toDouble())
        .put("top", rect.top.toDouble()).put("right", rect.right.toDouble()).put("bottom", rect.bottom.toDouble())

    protected fun assertPlaceholderVerticalFit(label: String, theme: ComposeThemeState) {
        val step = "placeholder-${diagnosticSequence++}"
        val bitmap = capture(step)
        try {
            val target = text(label, unmerged = true)
            val node = target.fetchSemanticsNode()
            val layout = textLayout(target)
            val evidence = writeTextDiagnostic(label, node, layout, step)
            val context = "$label; ${evidence.absolutePath}"
            assertEquals(label, layout.layoutInput.text.text)
            assertEquals("Placeholder must remain single-line: $context", 1, layout.lineCount)
            assertFalse("Placeholder overflows vertically: $context", layout.didOverflowHeight)
            val height = minOf(layout.size.height, node.size.height).toFloat()
            assertTrue("Placeholder line clipped: $context", layout.getLineTop(0) >= -0.5f &&
                layout.getLineBottom(0) <= height + 0.5f)
            val visible = node.boundsInRoot.translate(node.positionInWindow - node.positionInRoot)
            assertEquals("Placeholder ink", theme.subtle.toArgb(), layout.layoutInput.style.color.toArgb())
            var visibleGlyphs = 0
            label.forEachIndexed { offset, character ->
                if (!character.isWhitespace()) {
                    val glyph = layout.getBoundingBox(offset)
                    assertTrue("Placeholder glyph $offset clipped by Text: $context", glyph.top.isFinite() &&
                        glyph.bottom.isFinite() && glyph.height > 0 && glyph.top >= -0.5f &&
                        glyph.bottom <= height + 0.5f)
                    val windowGlyph = transformedBounds(node, glyph)
                    assertTrue("Placeholder glyph $offset clipped by ancestor: $context",
                        windowGlyph.top >= visible.top - 0.5f && windowGlyph.bottom <= visible.bottom + 0.5f)
                    // Narrow single-line fields may clip horizontally; check ink for complete visible glyphs.
                    if (windowGlyph.left >= visible.left && windowGlyph.right <= visible.right && glyph.width > 0) {
                        assertInDisplay(windowGlyph)
                        assertTrue("Placeholder glyph $offset must paint ink: $context",
                            inkPixels(bitmap, windowGlyph, theme.subtle, theme.panelElevated) > 0)
                        visibleGlyphs++
                    }
                }
            }
            assertTrue("Placeholder must show complete glyphs: $context", visibleGlyphs > 0)
        } finally {
            bitmap.recycle()
        }
    }

    protected fun assertSurface(
        target: SemanticsNodeInteraction,
        theme: ComposeThemeState,
        enabled: Boolean,
        step: String,
        primary: Boolean = false,
    ) {
        val bitmap = capture(step)
        try {
            target.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val rect = bounds(target)
            val density = compose.activity.resources.displayMetrics.density
            val minimumWidth = (48 * theme.uiScale).coerceAtLeast(28f) * density
            val minimumHeight = ((if (primary) 40 else 36) * theme.uiScale).coerceAtLeast(28f) * density
            assertTrue("A useful scaled watch target is required: $rect",
                rect.width >= minimumWidth - 0.5f && rect.height >= minimumHeight - 0.5f)
            assertInDisplay(rect)
            val fill = if (primary) {
                if (enabled) theme.accent else theme.panelElevated
            } else if (enabled) theme.panelElevated else theme.panel
            val ink = if (enabled) { if (primary) theme.onAccent else theme.text } else theme.subtle
            val outline = (if (enabled) theme.hairline else theme.hairline.copy(alpha = 0.6f))
                .compositeOver(fill)
            val label = target.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                .orEmpty().joinToString("") { it.text }
            val labelTarget = text(label, unmerged = true)
            val labelNode = labelTarget.fetchSemanticsNode()
            val layout = textLayout(labelTarget)
            writeTextDiagnostic(label, labelNode, layout, step)
            assertEquals("Button label must resolve to enabled/disabled ink", ink.toArgb(),
                layout.layoutInput.style.color.toArgb())
            var filled = 0
            var outlined = 0
            for (y in rect.top.toInt() until rect.bottom.toInt()) {
                for (x in rect.left.toInt() until rect.right.toInt()) {
                    val pixel = bitmap.getPixel(x, y)
                    if (near(pixel, fill)) filled++
                    if (near(pixel, outline)) outlined++
                }
            }
            assertTrue("Button must paint a filled surface", filled > rect.width * 3)
            if (!primary) assertTrue("Button must paint its hairline outline", outlined > rect.width / 2)
            var paintedText = 0
            label.forEachIndexed { offset, character ->
                if (!character.isWhitespace()) {
                    val glyph = transformedBounds(labelNode, layout.getBoundingBox(offset))
                    val paintedGlyph = inkPixels(bitmap, glyph, ink, fill)
                    assertTrue("Glyph $offset must paint enabled/disabled label ink", paintedGlyph > 0)
                    paintedText += paintedGlyph
                }
            }
            assertTrue("Button must paint enabled/disabled label ink", paintedText > 3)
        } finally {
            bitmap.recycle()
        }
    }

    protected fun bounds(target: SemanticsNodeInteraction): Rect {
        val node = target.fetchSemanticsNode()
        return Rect(node.positionInWindow.x, node.positionInWindow.y,
            node.positionInWindow.x + node.size.width, node.positionInWindow.y + node.size.height)
    }

    private fun assertInDisplay(rect: Rect) {
        val view = surfaceView()
        assertTrue("Target is outside the window: $rect", rect.left >= 0 && rect.top >= 0 &&
            rect.right <= view.width && rect.bottom <= view.height)
        if (!compose.activity.resources.configuration.isScreenRound) return
        val radius = minOf(view.width, view.height) / 2f - 2f
        listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach {
            val dx = it.x - view.width / 2f
            val dy = it.y - view.height / 2f
            assertTrue("Button or label is outside circular display: $rect",
                dx * dx + dy * dy < radius * radius)
        }
    }

    private fun capture(step: String): Bitmap = compose.runOnIdle {
        val view = surfaceView()
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            view.draw(Canvas(bitmap))
            val file = File("build/outputs/ui-regression/buttons/" +
                "${testName.methodName}-$step-${view.width}x${view.height}.png")
            val parent = requireNotNull(file.parentFile)
            assertTrue(parent.isDirectory || parent.mkdirs())
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }

    private fun surfaceView(): View = ShadowDialog.getLatestDialog()
        ?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView

    private fun inkPixels(bitmap: Bitmap, rect: Rect, ink: Color, fill: Color): Int {
        var count = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until
            kotlin.math.ceil(rect.bottom).toInt().coerceAtMost(bitmap.height)) {
            for (x in rect.left.toInt().coerceAtLeast(0) until
                kotlin.math.ceil(rect.right).toInt().coerceAtMost(bitmap.width)) {
                if (isAntialiasedInk(bitmap.getPixel(x, y), ink, fill)) count++
            }
        }
        return count
    }

    private fun near(pixel: Int, color: Color): Boolean {
        val expected = color.toArgb()
        return listOf(0, 8, 16, 24).all { shift ->
            kotlin.math.abs(((pixel ushr shift) and 255) - ((expected ushr shift) and 255)) <= 2
        }
    }

    private fun isAntialiasedInk(pixel: Int, ink: Color, fill: Color): Boolean {
        val foreground = ink.toArgb()
        val background = fill.toArgb()
        fun channel(color: Int, shift: Int) = (color ushr shift) and 255
        val strongest = listOf(0, 8, 16).maxBy {
            kotlin.math.abs(channel(foreground, it) - channel(background, it))
        }
        val delta = channel(foreground, strongest) - channel(background, strongest)
        if (delta == 0) return false
        val coverage = (channel(pixel, strongest) - channel(background, strongest)).toFloat() / delta
        if (coverage < 0.2f || coverage > 1f) return false
        return listOf(0, 8, 16, 24).all {
            val expected = channel(background, it) + coverage * (channel(foreground, it) - channel(background, it))
            kotlin.math.abs(channel(pixel, it) - expected) <= 2f
        }
    }
}
