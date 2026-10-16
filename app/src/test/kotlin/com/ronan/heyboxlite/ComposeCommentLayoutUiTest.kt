package com.ronan.heyboxlite

import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeCommentLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun squareRepliesStartAtThePrimaryBodyInsteadOfTheAvatar() =
        verifyLayout(round = false, width = 220, expectedInset = 32f)

    @Test fun narrowSquareUsesTheCompactPrimaryBodyStartForReplies() =
        verifyLayout(round = false, width = 148, expectedInset = 0f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundRepliesUseTheReducedPrimaryBodyInset() =
        verifyLayout(round = true, width = 158, expectedInset = 0f)

    @Test @Config(qualifiers = "w320dp-h320dp-round-mdpi")
    fun wideRoundStillPreservesBodyWidthInsteadOfIndentingForItsAvatar() =
        verifyLayout(round = true, width = 280, expectedInset = 0f)

    @Test fun squareUiScalingMovesTheAvatarAndReplyFrameTogetherWithoutShrinkingText() =
        verifyLayout(round = false, width = 220, expectedInset = 22.4f, uiScale = 0.7f)

    @Test fun squareCustomAndSystemFontScalesTriggerTheSameCompactLayoutForBodyAndReplies() =
        verifyLayout(round = false, width = 220, expectedInset = 0f,
            textScale = 1.2f, systemFontScale = 1.25f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundLargeTextWrapsWithoutClippingOrReintroducingReplyLevels() =
        verifyLayout(round = true, width = 148, expectedInset = 0f,
            textScale = 1.4f, systemFontScale = 1.2f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun sessionWatchScaleIsAppliedOnceLikeTheLegacyNativeRenderer() {
        val session = SessionStore(compose.activity).apply {
            setRoundScreen(true)
            setTextScale(140)
        }
        verifyLayout(round = true, width = 148, expectedInset = 0f,
            uiScale = session.uiScale() / 100f, textScale = session.textScale() / 100f)
    }

    @Test fun changingTheAvailableWidthRepositionsBothThePrimaryBodyAndReplyFrame() {
        val width = mutableStateOf(220)
        val group = thread()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Column(Modifier.width(width.value.dp).verticalScroll(rememberScrollState())) {
                    ComposeDetailThread(group, post(), false, ComposeMediaSettings(enabled = false), {})
                }
            }
        }
        for ((availableWidth, expectedInset) in listOf(220 to 32f, 148 to 0f, 220 to 32f)) {
            compose.runOnIdle { width.value = availableWidth }
            assertAlignment(expectedInset)
        }
    }

    private fun verifyLayout(
        round: Boolean,
        width: Int,
        expectedInset: Float,
        uiScale: Float = 1f,
        textScale: Float = 1f,
        systemFontScale: Float = 1f,
    ) {
        val group = thread()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, systemFontScale)) {
                HeyboxComposeTheme(composePreviewTheme(round).copy(uiScale = uiScale, textScale = textScale)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(Modifier.width(width.dp).verticalScroll(rememberScrollState())) {
                            ComposeDetailThread(group, post(), round, ComposeMediaSettings(enabled = false), {})
                        }
                    }
                }
            }
        }
        assertAlignment(expectedInset)
        val root = compose.onNodeWithText("Primary body")
        val reply = compose.onNodeWithText("Bo\uff1aReply")
        assertNativeSize(root, if (round) 12.5f else 13f, textScale, systemFontScale)
        assertNativeSize(reply, 12.5f, textScale, systemFontScale)
        assertWrapping(root)
        assertWrapping(reply)
        compose.onNodeWithText("Lv.24", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Lv.56", useUnmergedTree = true).assertDoesNotExist()
        val avatar = compose.onNodeWithContentDescription("Ada的头像").fetchSemanticsNode().boundsInRoot
        val primary = root.fetchSemanticsNode().boundsInRoot
        assertTrue("The primary text must stay below its header", primary.top >= avatar.bottom)
        compose.runOnIdle {
            captureMembershipUi(compose.activity,
                "comment-body-alignment-${if (round) "round" else "square"}-$width-$textScale-$systemFontScale")
        }
    }

    private fun assertAlignment(expectedInset: Float) {
        val primary = compose.onNodeWithText("Primary body").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("comment-root").fetchSemanticsNode().boundsInRoot
        val replies = compose.onNodeWithTag("comment-replies-root").fetchSemanticsNode().boundsInRoot
        assertEquals("Reply frame must follow actual primary text, not the comment or avatar edge",
            primary.left, replies.left, 0.6f)
        assertEquals(primary.right, replies.right, 0.6f)
        assertEquals("Compact and round layouts must not retain desktop indentation",
            expectedInset, primary.left - root.left, 0.6f)
        assertTrue(replies.width > 0f)
    }

    private fun assertNativeSize(
        node: SemanticsNodeInteraction,
        legacyBaseSp: Float,
        textScale: Float,
        systemFontScale: Float,
    ) {
        val layout = layout(node)
        assertEquals(legacyBaseSp * textScale, layout.layoutInput.style.fontSize.value, 0.001f)
        val config = Configuration(compose.activity.resources.configuration).apply { fontScale = systemFontScale }
        val context = compose.activity.createConfigurationContext(config)
        val native = UiComponents.label(context, "Native", legacyBaseSp, Color.BLACK, textScale)
        assertEquals("Compose must use the legacy native text size with the same scaling",
            native.textSize, layout.layoutInput.style.fontSize.value * systemFontScale, 0.05f)
    }

    private fun assertWrapping(node: SemanticsNodeInteraction) {
        val result = layout(node)
        assertFalse("Comment text must never be clipped", result.hasVisualOverflow)
        assertEquals(result.layoutInput.text.length, result.getLineEnd(result.lineCount - 1))
        for (line in 0 until result.lineCount) {
            assertFalse(result.isLineEllipsized(line))
            assertTrue(result.getLineLeft(line) >= -1f)
            assertTrue(result.getLineRight(line) <= result.size.width + 1f)
        }
    }

    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return paintedTextLayout(results.single())
    }

    private fun post() = FeedItem.from(JSONObject().put("linkid", "post"))

    private fun thread(): JSONObject {
        fun user(name: String, level: Int) = JSONObject().put("username", name).put("level", level)
        val root = JSONObject().put("commentid", "root").put("text", "Primary body")
            .put("user", user("Ada", 24))
        val reply = JSONObject().put("commentid", "reply").put("text", "Reply").put("replyid", "root")
            .put("user", user("Bo", 56))
        return JSONObject().put("comment", JSONArray().put(root).put(reply))
    }
}
