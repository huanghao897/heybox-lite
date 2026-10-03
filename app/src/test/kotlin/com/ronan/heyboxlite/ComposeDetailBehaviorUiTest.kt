package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeDetailBehaviorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun prepareRenderer() { EmojiRenderer.clear() }
    @After fun clearRenderer() { EmojiRenderer.clear() }

    @Test fun detailAuthorMetadataAndNavigationUseTheDetailResponse() {
        val opened = mutableListOf<List<String>>()
        val item = FeedItem.from(JSONObject().put("linkid", "post").put("title", "Feed title")
            .put("user", JSONObject().put("userid", "feed-author").put("username", "Feed author")))
        val link = JSONObject().put("title", "Detail title").put("uid", "detail-author")
            .put("create_time", 1_600_000_000L)
            .put("user", JSONObject().put("username", "Detail author")
                .put("avartar", "https://example.invalid/detail.png").put("level", 24)
                .put("signature", "Detail signature"))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeDetailHeader(item, ComposeMediaSettings(enabled = false),
                    { id, name, avatar -> opened += listOf(id, name, avatar) }, link)
            }
        }
        compose.onNodeWithText("Feed author").assertDoesNotExist()
        compose.onNodeWithText("Lv.24", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(Format.relativeTime(1_600_000_000L) + " · Detail signature",
            useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Detail author").performClick()
        assertEquals(listOf(listOf("detail-author", "Detail author", "https://example.invalid/detail.png")), opened)
    }

    @Test fun rootPostIdCannotOverrideTheNestedAuthorAndEmptyAvatarUsesItsAlias() {
        val opened = mutableListOf<List<String>>()
        val item = FeedItem.from(JSONObject().put("linkid", "post").put("title", "Title")
            .put("user", JSONObject().put("userid", "feed-author").put("username", "Feed author")))
        val link = JSONObject().put("id", "post-id").put("user", JSONObject()
            .put("userid", "detail-author").put("username", "Detail author")
            .put("avatar", "").put("avartar", "https://example.invalid/alias.png"))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeDetailHeader(item, ComposeMediaSettings(enabled = false),
                    { id, name, avatar -> opened += listOf(id, name, avatar) }, link)
            }
        }
        compose.onNodeWithText("Detail author").performClick()
        assertEquals(listOf(listOf("detail-author", "Detail author", "https://example.invalid/alias.png")), opened)
    }

    @Test fun nestedUserIdAliasIsAllowedWithoutTreatingTheRootIdAsAnAuthor() {
        val opened = mutableListOf<String>()
        val item = FeedItem.from(JSONObject().put("linkid", "post").put("title", "Title")
            .put("user", JSONObject().put("userid", "feed-author").put("username", "Feed author")))
        val link = JSONObject().put("id", "post-id").put("user", JSONObject()
            .put("id", "nested-author").put("username", "Detail author"))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeDetailHeader(item, ComposeMediaSettings(enabled = false),
                    { id, _, _ -> opened += id }, link)
            }
        }
        compose.onNodeWithText("Detail author").performClick()
        assertEquals(listOf("nested-author"), opened)
    }

    @Test fun cachedRepliesExpandBeforeRemoteLoadingAndLateRepliesClearTheFailure() {
        val group = mutableStateOf(replyGroup(7))
        val root = group.value.getJSONArray("comment").getJSONObject(0)
        val actions = mutableListOf<ComposeDetailAction>()
        val item = FeedItem.from(JSONObject().put("linkid", "post"))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ComposeDetailThread(group.value, item, false, ComposeMediaSettings(enabled = false),
                        {}, actions::add)
                }
            }
        }
        compose.onNodeWithText("Reply 6").assertDoesNotExist()
        compose.onNodeWithText("展开 5 条回复").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithText("Reply 7").assertExists()
        compose.onNodeWithText("展开 5 条回复").performScrollTo().performClick()
        compose.runOnIdle {
            assertSame(root, (actions.single() as ComposeDetailAction.LoadReplies).root)
        }
        compose.mainClock.advanceTimeBy(5_001L)
        compose.onNodeWithText("回复加载失败，重试").assertExists()
        compose.runOnIdle { group.value = replyGroup(13) }
        compose.onNodeWithText("回复加载失败，重试").assertDoesNotExist()
        compose.onNodeWithText("Reply 12").assertExists()
        compose.onNodeWithText("Reply 13").assertDoesNotExist()
        compose.onNodeWithText("展开 1 条回复").assertExists()
        compose.onNodeWithText("收起回复").performScrollTo().performClick()
        compose.onNodeWithText("Reply 6").assertDoesNotExist()
    }

    private fun replyGroup(count: Int): JSONObject {
        val comments = JSONArray().put(JSONObject().put("commentid", "root")
            .put("child_num", 13).put("text", "Root body")
            .put("user", JSONObject().put("username", "Root author")))
        for (index in 1..count) comments.put(JSONObject().put("commentid", "reply-$index")
            .put("create_at", index).put("text", "Reply body $index")
            .put("user", JSONObject().put("username", "Reply $index")))
        return JSONObject().put("comment", comments)
    }
}
