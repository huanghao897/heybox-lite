package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    shadows = [SectionChipOfflineImageShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeSectionChipUiTest : ComposeMembershipUiRegressionHarness() {
    @Before fun resetOfflineImages() { SectionChipOfflineImageShadow.requests.clear() }

    @Test fun shortChipPaintsPillEdgesReadableTextAndSmallIcon() {
        val palette = theme()
        showCentered(palette) { ComposeSectionChip(SHORT, ICON, palette, false) }
        assertChip(palette, SHORT, expectIcon = true)
        assertEquals(listOf(ICON to 12), SectionChipOfflineImageShadow.requests)
    }

    @Test fun noImageRemovesTheIconItsGapAndAllRequests() {
        val palette = theme().copy(uiScale = 0.5f, textScale = 1.4f)
        val noImage = mutableStateOf(false)
        showCentered(palette, largeText = true) {
            ComposeSectionChip(SHORT, ICON, palette, noImage.value)
        }
        assertChip(palette, SHORT, expectIcon = true, fontScale = 1.3f)
        val withIcon = bounds(tag("section-chip")).width
        compose.runOnIdle { noImage.value = true; SectionChipOfflineImageShadow.requests.clear() }
        assertChip(palette, SHORT, expectIcon = false, fontScale = 1.3f)
        assertEquals("Remove the actual 10dp icon and scaled 3dp gap", 11.5f,
            withIcon - bounds(tag("section-chip")).width, 1f)
        assertEquals("Only readable capsule padding, not an invisible icon slot, may indent the label", 5.46f,
            bounds(label()).left - bounds(tag("section-chip")).left, 1f)
        assertTrue(SectionChipOfflineImageShadow.requests.isEmpty())
        compose.runOnIdle { noImage.value = false }
        assertChip(palette, SHORT, expectIcon = true, fontScale = 1.3f)
    }

    @Test fun missingIconUsesASmallGlobeAndBlankSectionLeavesNoCapsule() {
        val palette = theme()
        val name = mutableStateOf(SHORT)
        showCentered(palette) { ComposeSectionChip(name.value, "", palette, false) }
        assertChip(palette, SHORT, expectIcon = true, remoteIcon = false)
        assertTrue(SectionChipOfflineImageShadow.requests.isEmpty())
        compose.runOnIdle { name.value = "  " }
        tag("section-chip").assertDoesNotExist()
        icon().assertDoesNotExist()
    }

    @Test fun squareFeedEllipsizesWithoutMovingOrSqueezingActions() = verifyFeed()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundFeedEllipsizesWithoutMovingOrSqueezingActions() = verifyFeed()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowFeedMinUiBigTextKeepsReadableActions() = verifyFeed(largeText = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundFeedMinUiBigTextKeepsReadableActions() = verifyFeed(largeText = true)

    @Test fun squareDetailKeepsSectionFirstBesideTags() = verifyDetail()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundDetailKeepsSectionFirstBesideTags() = verifyDetail()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowDetailMinUiBigTextHasNoPhantomIconOrOversizedRow() = verifyDetail(largeText = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundDetailMinUiBigTextHasNoPhantomIconOrOversizedRow() = verifyDetail(largeText = true)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun detailKeepsAllTagsWhenTheyNeedMoreThanTwoLines() {
        val palette = theme()
        val tags = listOf("strategy", "review", "discussion", "guides")
        val source = link(SHORT).put("tags", JSONArray().apply { tags.forEach(::put) })
        showCentered(palette) {
            ComposeDetailHeader(FeedItem.from(source), ComposeMediaSettings(enabled = false), link = source)
        }
        val result = layout(tag("detail-tags"))
        assertEquals(tags.joinToString("  ") { "#$it" }, result.layoutInput.text.text)
        assertTrue("The fixture must exercise more than two lines of real tags", result.lineCount > 2)
        assertFalse("No tags may be hidden by a line cap", result.multiParagraph.didExceedMaxLines)
        assertEquals(result.layoutInput.text.length, result.getLineEnd(result.lineCount - 1))
        repeat(result.lineCount) { assertFalse(result.isLineEllipsized(it)) }
        assertInDisplay(bounds(tag("detail-tags")), "full detail tags", corners = true)
        assertChip(palette, SHORT, expectIcon = false)
        assertTagsBesideSection(palette, expectTips = false)
        capture("all-tags-wrapped")
    }

    @Test fun visibleLineGeometryStillRejectsGenuinelyClippedText() {
        val palette = theme()
        showCentered(palette) {
            Text(SHORT, Modifier.width(100.dp).height(6.dp).testTag("clipped-label"),
                fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.sp, maxLines = 1)
        }
        val target = tag("clipped-label")
        val failure = runCatching { assertVisibleLine(target, layout(target)) }.exceptionOrNull()
        assertTrue("The geometry regression must continue to reject actual glyph clipping", failure is AssertionError)
    }

    private fun verifyFeed(largeText: Boolean = false) {
        val palette = palette(largeText)
        val section = mutableStateOf(LONG)
        val noImage = mutableStateOf(largeText)
        val actions = mutableListOf<FeedAction>()
        showCentered(palette, largeText) {
            ComposeFeedCard(post(section.value), palette, noImage.value, "", {},
                { _, action -> actions += action }, Modifier.testTag("partition-feed-card"),
                showFollow = false, showSecondaryActions = false)
        }
        val fontScale = if (largeText) 1.3f else 1f
        assertChip(palette, LONG, !largeText, ellipsized = true, fontScale = fontScale)
        val like = description("\u70b9\u8d5e")
        val comment = description("\u8bc4\u8bba")
        val originalActions = listOf(bounds(like), bounds(comment))
        val chip = bounds(tag("section-chip"))
        assertTrue("A long section must yield to like/comment controls", chip.right <= originalActions[0].left)
        originalActions.forEach { assertInDisplay(it, "feed action", corners = true) }
        assertTrue("Like/comment controls must not overlap", originalActions[0].right <= originalActions[1].left)
        assertActionLabel("128", like)
        assertActionLabel("24", comment)
        assertVisibleTextDoesNotOverlap()
        val article = text("\u6587\u7ae0", unmerged = true)
        assertTrue("Article type is separate from partition identity", bounds(article).bottom <= chip.top)
        like.performClick()
        comment.performClick()
        assertEquals(listOf(FeedAction.LIKE, FeedAction.COMMENT), actions)
        compose.runOnIdle { section.value = SHORT }
        assertChip(palette, SHORT, !largeText, fontScale = fontScale)
        originalActions.zip(listOf(bounds(like), bounds(comment))).forEach { (before, after) ->
            assertEquals("Section length must not affect action width", before.width, after.width, 0.5f)
            assertEquals("Section length must not move actions", before.left, after.left, 0.5f)
        }
        compose.runOnIdle { noImage.value = true; SectionChipOfflineImageShadow.requests.clear() }
        assertChip(palette, SHORT, false, fontScale = fontScale)
        assertTrue("No-image feed must stay offline", SectionChipOfflineImageShadow.requests.isEmpty())
    }

    private fun verifyDetail(largeText: Boolean = false) {
        val palette = palette(largeText)
        val section = mutableStateOf(SHORT)
        val noImage = mutableStateOf(largeText)
        val tags = mutableStateOf(true)
        showCentered(palette, largeText) {
            ComposeDetailHeader(post(section.value), ComposeMediaSettings(enabled = !noImage.value),
                link = link(section.value, tags.value))
        }
        val fontScale = if (largeText) 1.3f else 1f
        assertChip(palette, SHORT, !largeText, fontScale = fontScale)
        assertTagsBesideSection(palette)
        assertVisibleTextDoesNotOverlap()
        compose.runOnIdle { section.value = LONG }
        assertChip(palette, LONG, !largeText, ellipsized = true, fontScale = fontScale)
        assertTagsBesideSection(palette)
        compose.runOnIdle { noImage.value = true; SectionChipOfflineImageShadow.requests.clear() }
        assertChip(palette, LONG, false, ellipsized = true, fontScale = fontScale)
        assertTrue("No-image detail must not fetch hidden icons", SectionChipOfflineImageShadow.requests.isEmpty())
        compose.runOnIdle { section.value = SHORT; tags.value = false }
        tag("detail-tags").assertDoesNotExist()
        assertChip(palette, SHORT, false, fontScale = fontScale)
        assertTrue("Section-only detail must wrap its content, not fill a separate row",
            bounds(tag("section-chip")).width < bounds(tag("partition-content")).width * 0.75f)
        compose.runOnIdle { section.value = ""; tags.value = true }
        tag("section-chip").assertDoesNotExist()
        tag("detail-tags").assertIsDisplayed()
    }

    private fun assertTagsBesideSection(palette: ComposeThemeState, expectTips: Boolean = true) {
        val chip = bounds(tag("section-chip"))
        val tags = bounds(tag("detail-tags"))
        assertTrue("Partition must stay first, not become a hashtag or oversized standalone row",
            chip.right <= tags.left && abs(chip.center.y - tags.center.y) <= 1f)
        assertTrue("Only a small inter-metadata gap is needed", tags.left - chip.right <= 6 * palette.uiScale + 1)
        assertInDisplay(tags, "detail tags", corners = true)
        if (expectTips) text("#tips", unmerged = true).assertIsDisplayed()
        assertFalse(layout(label()).layoutInput.text.text.startsWith("#"))
    }

    private fun assertActionLabel(value: String, action: SemanticsNodeInteraction) {
        val target = compose.onNode(hasText(value) and hasAnyAncestor(
            hasContentDescription(if (value == "128") "\u70b9\u8d5e" else "\u8bc4\u8bba")),
            useUnmergedTree = true)
        val layout = layout(target)
        assertEquals(1, layout.lineCount)
        assertFalse("Required action count must not be ellipsized", layout.isLineEllipsized(0))
        assertFalse("Required action count must not be clipped", layout.hasVisualOverflow)
        assertContains(bounds(action), bounds(target), "action count")
    }

    private fun assertChip(
        palette: ComposeThemeState,
        name: String,
        expectIcon: Boolean,
        ellipsized: Boolean = false,
        remoteIcon: Boolean = true,
        fontScale: Float = 1f,
    ) {
        val target = tag("section-chip").assertIsDisplayed()
        target.assertContentDescriptionEquals("\u5206\u533a\uff1a$name")
        val chip = bounds(target)
        val label = label()
        val text = layout(label)
        val labelBounds = bounds(label)
        println("Section text geometry: " + JSONObject().put("test", testName.methodName)
            .put("text", name).put("chip", chip.toString()).put("label", labelBounds.toString())
            .put("measuredTextSize", text.size.toString()).put("paragraphHeight", text.multiParagraph.height.toDouble())
            .put("lineTop", text.getLineTop(0).toDouble()).put("lineBottom", text.getLineBottom(0).toDouble())
            .put("visibleEnd", text.getLineEnd(0, visibleEnd = true)).put("ellipsized", text.isLineEllipsized(0))
            .put("fontScale", text.layoutInput.density.fontScale.toDouble())
            .put("didExceedMaxLines", text.multiParagraph.didExceedMaxLines))
        assertEquals("Partition semantics must retain the original name", name, text.layoutInput.text.text)
        assertEquals(10.5f * palette.textScale, text.layoutInput.style.fontSize.value, 0.01f)
        assertEquals("UI scale must not shrink readable partition text", 14.sp * palette.textScale,
            text.layoutInput.style.lineHeight)
        assertEquals(0.sp, text.layoutInput.style.letterSpacing)
        assertEquals(fontScale, text.layoutInput.density.fontScale, 0.01f)
        assertEquals(1, text.lineCount)
        assertEquals("Only long section labels should ellipsize", ellipsized, text.isLineEllipsized(0))
        assertVisibleLine(label, text)
        assertContains(chip, labelBounds, "section text")
        // CJK fallback metrics can exceed the requested line height; only the actual
        // painted line plus 2dp scaled padding per edge may determine capsule height.
        val iconSize = if (expectIcon) (12 * palette.uiScale).coerceIn(10f, 14f) else 0f
        assertEquals("Capsule must add no height beyond its intrinsic text/icon and compact padding",
            maxOf(labelBounds.height, iconSize) + 2 * (2 * palette.uiScale).roundToInt(), chip.height, 1f)
        assertTrue("Capsule width must be capped", chip.width <= maxOf(112 * palette.uiScale, 80f) + 1)
        assertInDisplay(chip, "section capsule", corners = true)
        val iconBounds = if (expectIcon) bounds(icon().assertIsDisplayed()).also {
            val size = (12 * palette.uiScale).coerceIn(10f, 14f)
            assertEquals(size, it.width, 1f)
            assertEquals(size, it.height, 1f)
            assertTrue("Icon must not crowd the label", it.right < bounds(label).left)
            assertContains(chip, it, "section icon")
        } else null.also { icon().assertDoesNotExist() }
        capture("partition-${if (ellipsized) "long" else "short"}-${if (expectIcon) "icon" else "no-image"}")
        assertPaintedPill(palette, chip, labelBounds, text, iconBounds.takeIf { remoteIcon })
    }

    private fun assertVisibleLine(target: SemanticsNodeInteraction, result: TextLayoutResult) {
        val rect = bounds(target)
        assertEquals("A capsule must paint exactly one line", 1, result.lineCount)
        // didOverflowHeight also reports omitted maxLines, so inspect the visible line itself.
        assertTrue("The painted first line is vertically clipped: $rect / ${result.multiParagraph.height}",
            result.getLineTop(0) >= -0.5f && result.getLineBottom(0) <= rect.height + 0.5f &&
                result.multiParagraph.height <= rect.height + 0.5f)
        assertTrue("The painted first line is horizontally clipped",
            result.getLineLeft(0) >= -0.5f && result.getLineRight(0) <= rect.width + 0.5f)
    }

    private fun assertPaintedPill(palette: ComposeThemeState, chip: Rect, text: Rect,
                                 layout: TextLayoutResult, icon: Rect?) {
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try {
            fun pixel(x: Float, y: Float) = bitmap.getPixel(x.toInt(), y.toInt())
            assertEquals("A pill must cut away its bounding-box corner", palette.panel.toArgb(),
                pixel(chip.left + 1, chip.top + 1))
            // At 15% of its height a true semicircular cap is outside; a small-radius rectangle is inside.
            assertEquals("Capsule cap must stay truly round as large text makes it taller", palette.panel.toArgb(),
                pixel(chip.left + 1, chip.top + chip.height * 0.15f))
            assertTrue("The pill must paint its quiet filled surface",
                count(bitmap, chip) { it == palette.panelElevated.toArgb() } > chip.width)
            assertTrue("The label must paint actual native glyph ink",
                count(bitmap, text) { luminance(it) > luminance(palette.panelElevated.toArgb()) + 24 } > 2)
            assertUnclippedGlyphPixels(bitmap, palette, text, layout)
            val iconPixels = icon?.let { count(bitmap, it) { pixel -> pixel == SectionChipOfflineImageShadow.INK } } ?: 0
            if (icon != null) assertTrue("The fixture icon must really render inside its small slot", iconPixels > 4)
            assertEquals("No icon pixels may escape the small slot or remain in no-image mode", iconPixels,
                count(bitmap, chip) { it == SectionChipOfflineImageShadow.INK })
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertUnclippedGlyphPixels(bitmap: Bitmap, palette: ComposeThemeState,
                                          text: Rect, layout: TextLayoutResult) {
        val reference = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            reference.eraseColor(palette.panelElevated.toArgb())
            val canvas = ComposeCanvas(reference.asImageBitmap())
            canvas.translate(text.left, text.top)
            layout.multiParagraph.paint(canvas, color = palette.muted)
            var expectedInk = 0
            for (y in 0 until reference.height) for (x in 0 until reference.width) {
                val expected = reference.getPixel(x, y)
                if (luminance(expected) <= luminance(palette.panelElevated.toArgb()) + 24) continue
                expectedInk++
                assertTrue("A visible glyph escapes its measured line: $x,$y / $text",
                    x + 1 >= text.left && x < text.right && y + 1 >= text.top && y < text.bottom)
                val actual = bitmap.getPixel(x, y)
                assertTrue("A glyph/ellipsis pixel is clipped by the pill or its ancestors at $x,$y",
                    listOf(0, 8, 16).all { shift ->
                        abs((actual ushr shift and 255) - (expected ushr shift and 255)) <= 3
                    })
            }
            assertTrue("The unclipped native reference must contain real glyph ink", expectedInk > 3)
        } finally {
            reference.recycle()
        }
    }

    private fun count(bitmap: Bitmap, rect: Rect, matches: (Int) -> Boolean): Int {
        var result = 0
        for (y in floor(rect.top).toInt().coerceAtLeast(0) until ceil(rect.bottom).toInt().coerceAtMost(bitmap.height)) {
            for (x in floor(rect.left).toInt().coerceAtLeast(0) until ceil(rect.right).toInt().coerceAtMost(bitmap.width)) {
                if (matches(bitmap.getPixel(x, y))) result++
            }
        }
        return result
    }

    private fun luminance(argb: Int) = ((argb ushr 16 and 255) + (argb ushr 8 and 255) + (argb and 255)) / 3

    private fun assertContains(parent: Rect, child: Rect, label: String) {
        assertTrue("$label must not escape its control: $child / $parent",
            child.left >= parent.left - 0.5f && child.top >= parent.top - 0.5f &&
                child.right <= parent.right + 0.5f && child.bottom <= parent.bottom + 0.5f)
    }

    private fun label() = compose.onNodeWithTag("section-chip-label", useUnmergedTree = true)
    private fun icon() = compose.onNodeWithTag("section-chip-icon", useUnmergedTree = true)

    private fun bounds(target: SemanticsNodeInteraction): Rect {
        val node = target.fetchSemanticsNode()
        return Rect(node.positionInWindow.x, node.positionInWindow.y,
            node.positionInWindow.x + node.size.width, node.positionInWindow.y + node.size.height)
    }

    private fun layout(target: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        target.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return paintedTextLayout(results.single())
    }

    private fun palette(largeText: Boolean) = theme().let {
        if (largeText) it.copy(uiScale = 0.5f, textScale = 1.4f) else it
    }

    private fun showCentered(palette: ComposeThemeState, largeText: Boolean = false,
                             content: @Composable () -> Unit) {
        setScreen(palette, systemFontScale = if (largeText) 1.3f else 1f) {
            Box(Modifier.fillMaxSize().background(palette.panel), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().padding(horizontal =
                    if (palette.roundScreen) 26.dp else 8.dp).testTag("partition-content"),
                    horizontalAlignment = Alignment.CenterHorizontally) { content() }
            }
        }
    }

    private fun post(section: String) = FeedItem.from(link(section))

    private fun link(section: String, tags: Boolean = true) = JSONObject()
        .put("linkid", "section-fixture").put("title", "Review").put("link_style", 101)
        .put("link_award_num", 128).put("comment_num", 24)
        .put("user", JSONObject().put("username", "Ada"))
        .put("topics", JSONArray().apply {
            if (section.isNotBlank()) put(JSONObject().put("name", section).put("pic_url", ICON))
        })
        .put("tags", JSONArray().apply { if (tags) { put(section); put("tips") } })

    companion object {
        private const val SHORT = "\u6e38\u620f"
        private const val LONG = "\u6e38\u620f\u8ba8\u8bba\u4e0e\u7b56\u7565\u653b\u7565\u793e\u533a\u957f\u5206\u533a\u540d\u79f0"
        private const val ICON = "https://section-chip.invalid/icon.png"
    }
}

/** Only image I/O is replaced: Compose layout, text and the native rasterizer stay real. */
@Implements(value = ImageLoader::class, isInAndroidSdk = false)
internal class SectionChipOfflineImageShadow {
    companion object {
        const val INK = 0xFFB8B8B8.toInt()
        val requests = mutableListOf<Pair<String, Int>>()

        @JvmStatic @Implementation fun cachedThumbnail(url: String, targetPx: Int): Bitmap {
            require(url == "https://section-chip.invalid/icon.png") { "Unexpected network image: $url" }
            requests += url to targetPx
            return Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(INK) }
        }

        @JvmStatic @Implementation fun loadCancellable(url: String, targetPx: Int,
            callback: ImageLoader.Callback): ImageLoadRequest = error("Unexpected uncached image request: $url")
    }
}
