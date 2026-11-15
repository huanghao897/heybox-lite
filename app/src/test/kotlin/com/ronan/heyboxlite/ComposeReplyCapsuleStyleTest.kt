package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeReplyCapsuleStyleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun squareReplyCapsuleUsesOriginalFilledSize() = verifyStyle(round = false, width = 220)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundReplyCapsuleUsesOriginalFilledSize() = verifyStyle(round = true, width = 158)

    private fun verifyStyle(round: Boolean, width: Int) {
        val root = JSONObject().put("commentid", "root").put("text", "Root")
            .put("child_num", 12).put("user", JSONObject().put("userid", "author")
                .put("username", "Author"))
        val group = JSONObject().put("comment", JSONArray().put(root))
        val theme = composePreviewTheme(round)
        compose.setContent {
            HeyboxComposeTheme(theme) {
                Box(Modifier.width(width.dp)) {
                    ComposeDetailThread(group, FeedItem.from(JSONObject().put("linkid", "post")),
                        round, ComposeMediaSettings(enabled = false), {})
                }
            }
        }
        compose.onNodeWithText("展开 5 条回复").assertIsDisplayed()
        val capsule = compose.onNodeWithTag("comment-reply-visual-展开 5 条回复", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("The reply action retains the original compact filled capsule",
            26f * theme.uiScale, capsule.height, .5f)
        compose.runOnIdle { captureMembershipUi(compose.activity,
            "reply-capsule-restored-${if (round) "round" else "square"}") }
    }
}
