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
@Config(sdk = [28], application = Application::class, qualifiers = "mdpi")
class ComposeFeedPresentationTest {
    @Test fun textNormalizationKeepsExistingFallbackAndInlineMarkup() {
        val sources = listOf("", "Plain text", "<p>First</p><p>Second</p>",
            "[{\"type\":\"text\",\"text\":\"Structured text\"}]",
            "<a data-link-type=\"game\" data-game-id=\"42\">Game</a> [smile]")
        for (source in sources) {
            assertEquals(RichContent.commentText(source).ifEmpty { RichContent.plainText(source) },
                composeFeedText(source))
        }
        val rich = composeFeedText(sources.last())
        assertEquals(1, RichGameLinkMarkup.parse(rich).links.size)
        assertTrue(rich.contains("[smile]"))
    }

    @Test fun gamePresentationKeepsTheExistingNameCoverAndMetadataOrder() {
        val preload = JSONObject().put("game", JSONObject().put("appid", "42")
            .put("name", "Game").put("image", "https://example.com/game.png")
            .put("platforms", JSONArray().put("steam")).put("score", "8.5")
            .put("price", JSONObject().put("current", "25")))
        val content = requireNotNull(composeGameCardPresentation(preload))
        assertEquals(1, content.count)
        assertEquals("Game", content.name)
        assertEquals("https://example.com/game.png", content.coverUrl)
        assertEquals("PC \u00b7 8.5 \u00b7 25", content.metadata)
    }

    @Test fun countOnlyCardsStillHaveAnEmptyPresentationForTheExistingUiFallbacks() {
        val preload = JSONObject().put("game_cards", JSONArray()
            .put(JSONObject().put("appid", "42"))
            .put(JSONObject().put("appid", "43")))
        val content = requireNotNull(composeGameCardPresentation(preload))
        assertEquals(2, content.count)
        assertEquals("", content.name)
        assertEquals("", content.coverUrl)
        assertEquals("", content.metadata)
    }

    @Test fun absentAndNonGamePreloadsDoNotCreateACard() {
        assertNull(composeGameCardPresentation(null))
        assertNull(composeGameCardPresentation(JSONObject().put("text", "A normal post")))
    }

    @Test fun presentationIsDetachedFromMutablePayloadAndDoesNotCacheActionFields() {
        val game = JSONObject().put("appid", "42").put("name", "Original")
        val preload = JSONObject().put("game", game)
        val item = FeedItem.from(JSONObject().put("linkid", "post")
            .put("communityPostPreload", preload))
        val original = requireNotNull(composeGameCardPresentation(item.contentPreload))
        item.likes = 37
        item.liked = true
        item.following = true
        item.followPending = true
        item.favorited = true
        assertEquals(original, composeGameCardPresentation(item.contentPreload))
        game.put("name", "Replacement")
        assertEquals("Original", original.name)
        assertEquals("Replacement", requireNotNull(composeGameCardPresentation(preload)).name)
    }
}
