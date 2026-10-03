package com.ronan.heyboxlite

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeDetailPresentationTest {
    @Test fun sectionAndTagsComeFromTheDetailResponseNotFromInventedTopicLabels() {
        val item = FeedItem.from(JSONObject().put("linkid", "post").put("title", "Feed title")
            .put("topics", JSONArray().put(JSONObject().put("name", "Old section"))))
        val link = JSONObject().put("title", "Detail title [呲牙]")
            .put("topics", JSONArray().put(JSONObject().put("name", "Game community")
                .put("pic_url", "https://example.invalid/community.png")))
            .put("tags", JSONArray().put("Discussion").put("Game community").put("Review"))
            .put("user", JSONObject().put("level_info", JSONObject().put("level", 24)))
        val before = link.toString()
        val value = ComposeDetailHeaderPresentation.from(item, link)
        assertEquals("Detail title [呲牙]", value.title)
        assertEquals("Game community", value.section)
        assertEquals("https://example.invalid/community.png", value.sectionIcon)
        assertEquals(listOf("Discussion", "Review"), value.tags)
        assertEquals(24, value.level)
        assertEquals(before, link.toString())
    }

    @Test fun feedFallbackKeepsItsSectionButDoesNotDuplicateItAsAHashtag() {
        val item = FeedItem.from(JSONObject().put("linkid", "fallback").put("title", "Title")
            .put("topics", JSONArray().put(JSONObject().put("name", "Community")
                .put("pic_url", "https://example.invalid/icon.png"))))
        val content = ComposeDetailHeaderPresentation.from(item)
        assertEquals("Community", content.section)
        assertEquals(item.topicIcon, content.sectionIcon)
        assertTrue(content.tags.isEmpty())
    }

    @Test fun replyUpdatesPublishANewGroupAndNeverMutateEarlierSnapshots() {
        val item = FeedItem.from(JSONObject().put("linkid", "post"))
        val root = JSONObject().put("commentid", "root").put("text", "Root")
        val group = JSONObject().put("comment", JSONArray().put(root)).put("child_num", 9)
        val original = ComposeDetailState(item, emptyList(), emptyList(), listOf(group), false)
        val state = ComposeNavigationState()
        state.detail.value = original
        val added = JSONObject().put("commentid", "reply").put("text", "Reply")
        state.appendDetailReplies("root", listOf(added))
        val updated = requireNotNull(state.detail.value)
        assertNotSame(original, updated)
        assertNotSame(group, updated.comments[0])
        assertEquals(1, group.getJSONArray("comment").length())
        assertEquals(2, updated.comments[0].getJSONArray("comment").length())
        assertSame(root, updated.comments[0].getJSONArray("comment").getJSONObject(0))
        assertEquals(9, updated.comments[0].getInt("child_num"))
        state.appendDetailReplies("root", listOf(added))
        assertEquals(2, state.detail.value!!.comments[0].getJSONArray("comment").length())
    }

    @Test fun singletonRootGetsRetainedWhenRemoteRepliesArrive() {
        val item = FeedItem.from(JSONObject().put("linkid", "post"))
        val root = JSONObject().put("commentid", "root")
        val state = ComposeNavigationState()
        state.detail.value = ComposeDetailState(item, emptyList(), emptyList(), listOf(root), false)
        state.appendDetailReplies("root", listOf(JSONObject().put("commentid", "reply")))
        val group = state.detail.value!!.comments[0]
        assertFalse(root.has("comment"))
        assertSame(root, group.getJSONArray("comment").getJSONObject(0))
        assertEquals("reply", group.getJSONArray("comment").getJSONObject(1).getString("commentid"))
    }

    @Test fun fallbackBodyKeepsParagraphsOfficialEmojiAndGameLinkMetadata() {
        val source = "<p>Before [cube_惊讶]</p><p>Try " +
            "<a data-link-type=\"game\" data-game-id=\"42\">Game</a> after</p>"
        val value = composeDetailFallbackText(source)
        val plain = RichInlineRenderer.plainText(value)
        assertEquals(RichContent.plainText(source), plain)
        assertTrue(plain.contains("Before [cube_惊讶]"))
        assertTrue(plain.contains("Try Game after"))
        val parsed = RichGameLinkMarkup.parse(value)
        assertEquals(1, parsed.links.size)
        assertEquals("42", parsed.links.single().game.appId)
    }

    @Test fun detailParserKeepsEmbeddedGameCardsAtTheirArticlePositions() {
        val first = JSONObject().put("type", "game").put("appid", "101")
            .put("name", "First game").put("image", "first-cover")
        val second = JSONObject().put("type", "game").put("appid", "202")
            .put("name", "Second game").put("image", "second-cover")
        val link = JSONObject().put("is_article", 1).put("text", JSONArray()
            .put(JSONObject().put("type", "html").put("text", "<p>Before</p>"))
            .put(first)
            .put(JSONObject().put("type", "html").put("text", "<p>Between</p>"))
            .put(second)
            .put(JSONObject().put("type", "html").put("text", "<p>After</p>")))
        val blocks = DetailContentParser().resolve(link, "", null).blocks
        assertEquals(listOf("Before", "101", "Between", "202", "After"), blocks.map { it.value })
        val cards = blocks.filter { it.kind == RichContent.Block.GAME_CARD }
        assertEquals(listOf("101", "202"), cards.map { it.value })
        assertEquals(listOf("First game", "Second game"), cards.map {
            GameCardData.fromEmbedded(it.gameObject, it.value)?.name
        })
    }
}
