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
    @Test fun onlyEmojiAndGameLinksRequireNativeFeedText() {
        for (plain in listOf("", "Plain text", "Watch 2.18", "A normal game name")) {
            assertFalse(feedTextNeedsSpans(plain))
        }
        for (rich in listOf("Text [smile]", "cube_example", "heygirl_example",
            composeFeedText("<a data-link-type=\"game\" data-game-id=\"42\">Game</a>"))) {
            assertTrue(feedTextNeedsSpans(rich))
        }
        val presentation = ComposeFeedPresentation("Plain", "Text [smile]", null)
        assertFalse(presentation.richTitle)
        assertTrue(presentation.richDescription)
    }

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

    @Test fun screenCacheReusesParsesButNotMutableActionsOrSameIdReplacements() {
        val preload = countingPreload("First game")
        val first = post("<p>First title</p>", preload)
        val cache = ComposeFeedPresentationCache()
        val presentation = cache.get(first)
        val reads = preload.reads
        assertTrue(reads > 0)
        assertEquals(composeFeedText(first.title), presentation.title)
        assertEquals(composeFeedText(first.description), presentation.description)
        first.likes = 42
        first.liked = true
        first.following = true
        repeat(20) { assertSame(presentation, cache.get(first)) }
        assertEquals(reads, preload.reads)
        val replacement = post("Replacement", countingPreload("Next game"))
        val next = cache.get(replacement)
        assertNotSame(presentation, next)
        assertEquals(first.id, replacement.id)
        assertEquals("Replacement", next.title)
        assertEquals("Next game", requireNotNull(next.game).name)
        assertEquals("First game", requireNotNull(presentation.game).name)
    }

    @Test fun screenCacheIsBoundedAndEvictsTheLeastRecentlyUsedEntry() {
        val cache = ComposeFeedPresentationCache(capacity = 2)
        val preloads = (0..2).map { countingPreload("Game $it") }
        val posts = preloads.mapIndexed { index, preload -> post("Title $index", preload) }
        val first = cache.get(posts[0])
        cache.get(posts[1])
        val reads = preloads.map { it.reads }
        assertSame(first, cache.get(posts[0]))
        cache.get(posts[2])
        assertEquals(2, cache.size)
        assertSame(first, cache.get(posts[0]))
        assertEquals(reads[0], preloads[0].reads)
        cache.get(posts[1])
        assertTrue(preloads[1].reads > reads[1])
        assertEquals(2, cache.size)
    }

    @Test fun screenCacheAlsoRetainsEmptyGameScans() {
        val preload = CountingPreload().apply { put("text", "No games") }
        val item = post("", preload)
        val cache = ComposeFeedPresentationCache()
        val first = cache.get(item)
        val reads = preload.reads
        assertTrue(reads > 0)
        assertEquals("\u65e0\u6807\u9898\u5185\u5bb9", first.title)
        assertNull(first.game)
        repeat(20) { assertSame(first, cache.get(item)) }
        assertEquals(reads, preload.reads)
    }

    private fun post(title: String, preload: JSONObject) = FeedItem.from(
        JSONObject().put("linkid", "same-id").put("title", title)
            .put("description", "<p>Summary</p>").put("communityPostPreload", preload),
    )

    private fun countingPreload(name: String) = CountingPreload().apply {
        put("game", JSONObject().put("appid", "42").put("name", name))
    }

    private class CountingPreload : JSONObject() {
        var reads = 0
        override fun opt(key: String): Any? {
            reads++
            return super.opt(key)
        }
    }
}
