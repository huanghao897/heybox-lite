package com.ronan.heyboxlite

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.shadows.ShadowDialog
import kotlin.math.hypot

/** The configured shape, not only the Robolectric hardware qualifier, owns the geometry. */
internal abstract class ComposeRoundLayoutHarness : ComposeWatchActionButtonTestHarness() {
    private val clients = mutableListOf<ApiClient>()
    private lateinit var layoutTheme: ComposeThemeState

    @After fun closeRoundLayoutClients() {
        clients.forEach(ApiClient::close)
    }

    protected fun layoutServices(
        forceRound: Boolean = false,
        uiScale: Float = 1f,
        textScale: Float = 1f,
    ): ComposeServices {
        val activity = compose.activity
        val session = SessionStore(activity)
        session.setNoImage(true)
        session.setRoundScreen(forceRound)
        layoutTheme = composeThemeState(ThemeTokens.of(true, 0, 0), uiScale, textScale,
            session.usesRoundLayout()).copy(motionLevel = MotionLevel.OFF)
        val api = ApiClient(session).also(clients::add)
        return ComposeServices(activity, session, api, GameDetailClient(api), LocalCache(activity),
            Handler(Looper.getMainLooper()), ReadingTimeTracker(activity), null, layoutTheme,
            ComposeToast {})
    }

    protected fun showLayout(services: ComposeServices, content: @Composable () -> Unit) {
        show(services.theme, content = content)
        assertEquals("Forced round and system round must resolve through SessionStore",
            services.session.usesRoundLayout(), services.theme.roundScreen)
    }

    /** Offscreen scrollable rows are not failures: first bring their real target into view. */
    protected fun circleReach(target: SemanticsNodeInteraction): SemanticsNodeInteraction {
        val node = target.fetchSemanticsNode()
        val parent = scrollParent(node)
        if (parent != null) {
            val viewport = visibleScreenBounds(parent)
            val rect = screenBounds(node)
            val minimum = viewport.top + rect.height / 2f
            val maximum = viewport.bottom - rect.height / 2f
            if (minimum <= maximum) {
                val center = (compose.activity.window.decorView.height / 2f)
                    .coerceIn(minimum, maximum)
                val parentId = parent.id
                // Field placeholders can have a nearer horizontal scroll ancestor.
                compose.onNode(SemanticsMatcher("round layout scroll parent $parentId") {
                    it.id == parentId
                }, useUnmergedTree = true).performSemanticsAction(SemanticsActions.ScrollBy) {
                    it(0f, rect.center.y - center)
                }
                compose.waitForIdle()
            }
        }
        return target.assertIsDisplayed()
    }

    protected fun circleReach(label: String) = circleReach(text(label))

    protected fun touchReachable(label: String) {
        val target = circleReach(label)
        assertTouchable(target, label)
        assertPaintedLabelInsideDisplay(label)
        target.performTouchInput { click() }
        compose.waitForIdle()
    }

    protected fun assertTouchable(target: SemanticsNodeInteraction, label: String) {
        target.assertIsDisplayed()
        val node = target.fetchSemanticsNode()
        val rect = screenBounds(node)
        val visible = visibleScreenBounds(node)
        val center = rect.center
        assertTrue("$label has no visible hit-test center: $rect / $visible", visible.contains(center))
        assertDisplayPoint(center, "$label touch center", touchRadius = 8f)
    }

    protected fun assertNoTopToolbar() {
        compose.onNodeWithTag("watch-page-header").assertDoesNotExist()
        compose.onNodeWithContentDescription("\u8fd4\u56de", useUnmergedTree = true).assertDoesNotExist()
    }

    protected fun installHostBack(onBack: () -> Unit) {
        compose.activity.onBackPressedDispatcher.addCallback(compose.activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })
    }

    protected fun pressHostBack() {
        assertNoTopToolbar()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    protected fun assertPaintedLabelInsideDisplay(label: String) {
        val target = text(label, unmerged = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        target.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("A real painted Text layout is required: $label", 1, layouts.size)
        val layout = paintedTextLayout(layouts.single())
        val node = target.fetchSemanticsNode()
        assertEquals(label, layout.layoutInput.text.text)
        assertFalse("Required label is truncated: $label", layout.multiParagraph.didExceedMaxLines)
        val visible = visibleScreenBounds(node)
        label.forEachIndexed { index, character ->
            if (!character.isWhitespace()) {
                val line = layout.getLineForOffset(index)
                assertFalse("Required label is ellipsized: $label", layout.isLineEllipsized(line))
                val glyph = screenRect(node, layout.getBoundingBox(index))
                assertTrue("Glyph is clipped by a scrolling ancestor: $label / $index / $glyph / $visible",
                    glyph.left >= visible.left - 0.5f && glyph.top >= visible.top - 0.5f &&
                        glyph.right <= visible.right + 0.5f && glyph.bottom <= visible.bottom + 0.5f)
                rectCorners(glyph).forEach { assertDisplayPoint(it, "$label glyph $index") }
            }
        }
    }

    protected fun assertControlInsideDisplay(target: SemanticsNodeInteraction, label: String) {
        val rect = screenBounds(target.assertIsDisplayed().fetchSemanticsNode())
        rectCorners(rect).forEach { assertDisplayPoint(it, "$label ($rect; window=${windowOffset()})") }
        assertTouchable(target, label)
    }

    private fun scrollParent(node: SemanticsNode): SemanticsNode? =
        generateSequence(node.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) &&
                it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }

    private fun screenBounds(node: SemanticsNode): Rect {
        val origin = node.positionInWindow + windowOffset()
        return Rect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
    }

    // Semantics bounds describe the viewport; its LayoutNode can be the moving scroll content.
    private fun visibleScreenBounds(node: SemanticsNode): Rect = node.boundsInRoot.translate(
        node.positionInWindow - node.positionInRoot + windowOffset())

    private fun screenRect(node: SemanticsNode, local: Rect): Rect {
        // Dialog coordinates belong to their own window. Place them in the physical activity display.
        val offset = windowOffset()
        val points = rectCorners(local).map { node.layoutInfo.coordinates.localToWindow(it) + offset }
        return Rect(points.minOf { it.x }, points.minOf { it.y },
            points.maxOf { it.x }, points.maxOf { it.y })
    }

    private fun windowOffset(): Offset {
        val activityView = compose.activity.window.decorView
        val view: View = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            ?.window?.decorView ?: activityView
        val origin = IntArray(2).also(view::getLocationOnScreen)
        val inWindow = IntArray(2).also(view::getLocationInWindow)
        val activityOrigin = IntArray(2).also(activityView::getLocationOnScreen)
        val activityInWindow = IntArray(2).also(activityView::getLocationInWindow)
        // positionInWindow already includes decor offsets, so translate only the window origin.
        return Offset((origin[0] - inWindow[0] - activityOrigin[0] + activityInWindow[0]).toFloat(),
            (origin[1] - inWindow[1] - activityOrigin[1] + activityInWindow[1]).toFloat())
    }

    private fun assertDisplayPoint(point: Offset, label: String, touchRadius: Float = 0f) {
        val display = compose.activity.window.decorView
        assertTrue("$label is outside ${display.width}x${display.height}: $point",
            point.x >= -0.5f && point.y >= -0.5f && point.x <= display.width + 0.5f &&
                point.y <= display.height + 0.5f)
        if (layoutTheme.roundScreen) {
            val radius = minOf(display.width, display.height) / 2f - 1f
            assertTrue("$label is outside the configured circular display: $point; radius=$radius",
                hypot(point.x - display.width / 2f, point.y - display.height / 2f) + touchRadius <= radius)
        }
    }

    private fun rectCorners(rect: Rect) =
        listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)
}
