package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeReadingSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun mountedBodyAndPrimaryCommentUseTheSameSavedBoldAndLetterSpacing() {
        val reading = mutableStateOf(ComposeReadingSettings())
        val item = FeedItem.from(JSONObject().put("linkid", "post"))
        val comment = JSONObject().put("commentid", "root").put("text", "primary-comment")
            .put("user", JSONObject().put("username", "Author"))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false).copy(reading = reading.value)) {
                Column {
                    ComposeDetailBlock(RichContent.Block.text("reading-body"), ComposeMediaSettings(false), null, {})
                    ComposeDetailThread(comment, item, false, ComposeMediaSettings(false), {})
                }
            }
        }
        assertEquals(FontWeight.Medium, textLayout("reading-body").layoutInput.style.fontWeight)
        assertEquals(FontWeight.Medium, textLayout("primary-comment").layoutInput.style.fontWeight)

        compose.runOnIdle { reading.value = reading.value.copy(bold = false, letterSpacing = 40, lineSpacing = 180) }

        val body = textLayout("reading-body").layoutInput.style
        val primary = textLayout("primary-comment").layoutInput.style
        assertEquals(FontWeight.Normal, body.fontWeight)
        assertEquals(FontWeight.Normal, primary.fontWeight)
        assertEquals(0.2.em, body.letterSpacing)
        assertEquals(body.letterSpacing, primary.letterSpacing)
        assertTrue(primary.lineHeight.value > primary.fontSize.value)
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundReadingRetainsWideTextAndRespondsToTheConfiguredInset() {
        val reading = mutableStateOf(ComposeReadingSettings())
        val theme = composePreviewTheme(true)
        compose.setContent {
            HeyboxComposeTheme(theme.copy(reading = reading.value)) {
                BoxWithConstraints(Modifier.fillMaxWidth().testTag("page")) {
                    val inset = watchReadingHorizontal(maxWidth, LocalHeyboxTheme.current)
                    Column(Modifier.fillMaxWidth().padding(horizontal = inset).testTag("reading-width")) {
                        ComposeDetailBlock(RichContent.Block.text("reading-body"), ComposeMediaSettings(false), null, {})
                    }
                }
            }
        }
        val page = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val original = compose.onNodeWithTag("reading-width").fetchSemanticsNode().boundsInRoot
        assertTrue("Round body must retain at least 80 percent of the screen width", original.width >= page.width * 0.8f)

        compose.runOnIdle { reading.value = reading.value.copy(pagePadding = 30) }
        val changed = compose.onNodeWithTag("reading-width").fetchSemanticsNode().boundsInRoot
        assertTrue(changed.width < original.width)
        assertEquals((30 * theme.uiScale).roundToInt().toFloat(), changed.left - page.left, 0.01f)
    }

    private fun textLayout(text: String): TextLayoutResult {
        val values = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
        return values.single()
    }
}
