package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
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
class ComposeCommentInlineMetaUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val meta = "1m \u00b7 CN"
    private val heading = "Bo \u56de\u590d Li\uff1a"

    @Test fun squareShortReplyKeepsMetadataOnTheBodyLine() = verifyShortReply(false, 220)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundShortReplyKeepsMetadataOnTheBodyLine() = verifyShortReply(true, 152)

    private fun verifyShortReply(round: Boolean, width: Int) {
        val theme = composePreviewTheme(round)
        compose.setContent {
            HeyboxComposeTheme(theme) {
                ComposeCommentReplyText("Bo", "Li", "ok", false, meta,
                    Modifier.width(width.dp).testTag("reply"))
            }
        }
        val layout = layout()
        val text = layout.layoutInput.text
        assertEquals("${heading}ok  $meta", text.text)
        assertEquals(1, layout.lineCount)
        assertEquals(11.sp, layout.layoutInput.style.fontSize)
        val suffix = text.spanStyles.single { it.item.fontSize == 9.sp }
        assertEquals(theme.muted, suffix.item.color)
        assertEquals("${heading}ok".length, suffix.start)
        assertEquals(text.length, suffix.end)
        assertFits(layout)
        compose.onNodeWithText(meta, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun blankOrUpdatedMetadataNeverLeavesASeparateRowOrTrailingGap() {
        val metadata = mutableStateOf(meta)
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeCommentReplyText("Bo", "Li", "ok", false, metadata.value,
                    Modifier.width(220.dp).testTag("reply"))
            }
        }
        for (value in listOf(meta, "", " \t ", "2m \u00b7 JP")) {
            compose.runOnIdle { metadata.value = value }
            val text = layout().layoutInput.text
            assertEquals("${heading}ok" + if (value.isBlank()) "" else "  $value", text.text)
            assertEquals(if (value.isBlank()) 0 else 1,
                text.spanStyles.count { it.item.fontSize == 9.sp })
            assertEquals(1, layout().lineCount)
        }
    }

    @Test fun narrowSquareLargeTextWrapsMetadataNaturally() = verifyWrapping(false)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun narrowRoundLargeTextWrapsMetadataNaturally() = verifyWrapping(true)

    private fun verifyWrapping(round: Boolean) {
        val body = "one two three four five six"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.25f)) {
                HeyboxComposeTheme(composePreviewTheme(round).copy(textScale = 1.2f)) {
                    Box(Modifier.size(192.dp).testTag("surface")
                        .then(if (round) Modifier.clip(CircleShape) else Modifier),
                        contentAlignment = Alignment.Center) {
                        ComposeCommentReplyText("Bo", "Li", body, false, meta,
                            Modifier.width(112.dp).testTag("reply"))
                    }
                }
            }
        }
        val layout = layout()
        assertEquals("$heading$body  $meta", layout.layoutInput.text.text)
        assertFalse(layout.layoutInput.text.text.contains('\n'))
        assertFalse(layout.layoutInput.text.text.contains('\r'))
        assertTrue(layout.lineCount > 1)
        assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines)
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        assertFits(layout)
        compose.onNodeWithText(meta, useUnmergedTree = true).assertDoesNotExist()
        if (round) {
            val surface = compose.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
            val bounds = compose.onNodeWithTag("reply").fetchSemanticsNode().boundsInRoot
            fun insideCircle(x: Float, y: Float) {
                val dx = bounds.left + x - surface.center.x
                val dy = bounds.top + y - surface.center.y
                assertTrue(dx * dx + dy * dy <= surface.width * surface.width / 4f)
            }
            for (line in 0 until layout.lineCount) {
                insideCircle(layout.getLineLeft(line), layout.getLineTop(line))
                insideCircle(layout.getLineRight(line), layout.getLineBottom(line))
            }
        }
    }

    @Test fun squareSuffixPreservesRichBodyAnnotationsAndInlineImages() = verifyRichSuffix(false, 220)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun narrowRoundSuffixPreservesRichBodyAnnotationsAndInlineImages() = verifyRichSuffix(true, 112)

    private fun verifyRichSuffix(round: Boolean, width: Int) {
        val catalog = ComposeInlineTextTestCatalog().apply { register("cube_wave", "wave") }
        val images = ComposeInlineTextTestImages().apply {
            cache["wave" to 16] = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(android.graphics.Color.MAGENTA) }
        }
        val source = "A [cube_wave] \uE100Game\uE101 Z"
        val model = buildComposeInlineTextModel(source, false, Color.Blue, true, catalog)
        val prefix = buildAnnotatedString {
            append("Bo\uff1a")
            addStyle(SpanStyle(color = Color.Green, fontWeight = FontWeight.Bold), 0, length)
            addStringAnnotation("user", "author-id", 0, length)
        }
        val metadata = mutableStateOf(meta)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.size(if (round) 192.dp else 240.dp)
                    .then(if (round) Modifier.clip(CircleShape) else Modifier),
                    contentAlignment = Alignment.Center) {
                    val suffix = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.Gray, fontSize = 9.sp)) {
                            append("  ${metadata.value}")
                        }
                    }
                    ComposeInlineText(source, false, Color.White, Color.Blue,
                        Modifier.width(width.dp).testTag("reply"), cy = true,
                        imageLoader = images, emojiCatalog = catalog, prefix = prefix, suffix = suffix)
                }
            }
        }
        for (value in listOf(meta, "2m \u00b7 JP")) {
            compose.runOnIdle { metadata.value = value }
            val layout = layout()
            val text = layout.layoutInput.text
            assertEquals(prefix.text + model.text.text + "  $value", text.text)
            assertEquals(3, layout.placeholderRects.filterNotNull().size)
            if (round) assertTrue(layout.lineCount > 1)
            for (attachment in model.attachments) {
                val start = prefix.length + attachment.start
                val end = prefix.length + attachment.end
                val annotation = text.getStringAnnotations(start, end).single { it.item == attachment.id }
                assertEquals(start, annotation.start)
                assertEquals(end, annotation.end)
            }
            val user = text.getStringAnnotations("user", 0, text.length).single()
            assertEquals(0, user.start)
            assertEquals(prefix.length, user.end)
            val author = text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
            assertEquals(Color.Green, author.item.color)
            assertEquals(0, author.start)
            assertEquals(prefix.length, author.end)
            val game = text.spanStyles.single { it.item.color == Color.Blue }
            assertEquals("\uFFFCGame", text.text.substring(game.start, game.end))
            val suffix = text.spanStyles.single { it.item.fontSize == 9.sp }
            assertEquals(Color.Gray, suffix.item.color)
            assertEquals(prefix.length + model.text.length, suffix.start)
            assertEquals(text.length, suffix.end)
            assertFits(layout)
            for (rect in layout.placeholderRects.filterNotNull()) {
                assertTrue(rect.left >= 0f && rect.right <= layout.size.width)
                assertTrue(rect.top >= 0f && rect.bottom <= layout.size.height)
            }
        }
        assertTrue(images.requests.isEmpty())
    }

    @Test fun squareThreadKeepsRootMetadataAndReplyFrameAlignment() = verifyThread(false, 220)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundThreadKeepsRootMetadataAndReplyFrameAlignment() = verifyThread(true, 158)

    private fun verifyThread(round: Boolean, width: Int) {
        val created = 1_600_000_000L
        fun user(name: String, level: Int) = JSONObject().put("username", name).put("level", level)
        val root = JSONObject().put("commentid", "root").put("text", "Root body")
            .put("create_at", created).put("ip_location", "Rootland").put("user", user("Ada", 24))
        val reply = JSONObject().put("commentid", "reply").put("text", "ok").put("replyid", "root")
            .put("create_at", created).put("ip_location", "CN").put("user", user("Bo", 56))
        val group = JSONObject().put("comment", JSONArray().put(root).put(reply))
        val rootMeta = composeCommentMeta(root, created)
        val replyMeta = composeCommentMeta(reply, created)
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(round)) {
                Box(Modifier.width(width.dp)) {
                    ComposeDetailThread(group, FeedItem.from(JSONObject().put("linkid", "post")),
                        round, ComposeMediaSettings(enabled = false), {})
                }
            }
        }
        compose.onNodeWithText(rootMeta, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Bo\uff1aok  $replyMeta", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(replyMeta, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Lv.24", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Lv.56", useUnmergedTree = true).assertDoesNotExist()
        val rootBounds = compose.onNodeWithTag("comment-root").fetchSemanticsNode().boundsInRoot
        val frame = compose.onNodeWithTag("comment-replies-root").fetchSemanticsNode().boundsInRoot
        assertEquals(rootBounds.left, frame.left, 0.5f)
        assertEquals(rootBounds.right, frame.right, 0.5f)
    }

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("reply").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(results)
        }
        return results.single()
    }

    private fun assertFits(layout: TextLayoutResult) {
        assertFalse(layout.hasVisualOverflow)
        for (line in 0 until layout.lineCount) {
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(layout.getLineLeft(line) >= -1f)
            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
        }
    }
}
