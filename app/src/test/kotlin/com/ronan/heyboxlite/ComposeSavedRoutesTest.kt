package com.ronan.heyboxlite

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ComposeSavedRoutesTest {
    @Test fun favoriteFolderRoundTripsUnicodeAndQueryCharacters() {
        val folder = ComposeFavoriteFolder("a/b & c", "动作 + 独立？=#", 12)
        val decoded = ComposeFavoriteFolder.fromRoute(folder.route())
        assertEquals(folder.id, decoded.id)
        assertEquals(folder.name, decoded.name)
    }

    @Test fun folderAndNestedDetailReturnToTheCorrectLevel() {
        val state = ComposeNavigationState()
        val folder = ComposeFavoriteFolder("42", "游戏", 3)
        state.navigate(folder.route())
        assertEquals("favorites", state.backTarget())
        state.showDetailLoading(FeedItem.from(JSONObject().put("linkid", "post")))
        assertEquals(folder.route(), state.detailReturnRouteSpec())
        assertTrue(ComposeSwipePresentation.isLiveRoute(folder.route()))
    }

    @Test fun historySearchMatchesTitleAuthorAndSummaryWithoutCaseSensitivity() {
        fun post(id: String, title: String, author: String, description: String) = FeedItem.from(
            JSONObject().put("linkid", id).put("title", title).put("description", description)
                .put("user", JSONObject().put("username", author)))
        val items = listOf(post("1", "Steam", "A", "第一篇"), post("2", "B", "小盒友", "动作游戏"))
        assertSame(items, filterReadingHistory(items, "  "))
        assertEquals("1", filterReadingHistory(items, " STEAM ").single().id)
        assertEquals("2", filterReadingHistory(items, "小盒友").single().id)
        assertEquals("2", filterReadingHistory(items, "动作").single().id)
        assertTrue(filterReadingHistory(items, "missing").isEmpty())
    }
}
