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
class ComposeDetailNavigationTest {
    @Test fun nestedPostsAndAuthorsReturnToTheExactParentDocuments() {
        val state = ComposeNavigationState()
        state.setRoute("search?q=watch")
        state.showDetailLoading(post("one"))
        state.showDetailDocument(document("one"))
        val firstKey = state.detail.value!!.entryKey
        val firstPresentation = state.presentationRoute()
        state.navigate("user_space?user=author-one&name=A")
        state.showDetailLoading(post("two"))
        state.showDetailDocument(document("two"))
        state.navigate("user_space?user=author-two&name=B")
        state.showDetailLoading(post("three"))
        state.showDetailDocument(document("three"))

        back(state, "user_space?user=author-two&name=B")
        assertEquals("two", state.detailForPresentation(state.backPresentationRoute())!!.item.id)
        back(state, "detail")
        assertEquals("two", state.detail.value!!.item.id)
        back(state, "user_space?user=author-one&name=A")
        assertEquals(firstPresentation, state.backPresentationRoute())
        back(state, "detail")
        assertEquals(firstKey, state.detail.value!!.entryKey)
        assertEquals("one", state.detail.value!!.item.id)
        assertEquals("body-one", state.detail.value!!.content.single().value)
        back(state, "search?q=watch")
        assertEquals("feed", state.backTarget())
    }

    @Test fun sameTypeNestedPostsDoNotSharePreviewOrEntryState() {
        val state = ComposeNavigationState()
        state.showDetailLoading(post("one"))
        state.showDetailDocument(document("one"))
        val parent = state.presentationRoute()
        state.showDetailLoading(post("two"))
        val child = state.presentationRoute()
        assertNotEquals(parent, child)
        assertEquals(parent, state.backPresentationRoute())
        assertEquals("one", state.detailForPresentation(parent)!!.item.id)
        assertEquals("two", state.detailForPresentation(child)!!.item.id)
        back(state, "detail")
        assertEquals(parent, state.presentationRoute())
        back(state, "feed")
    }

    @Test fun backgroundReplyCompletionBelongsOnlyToItsSavedEntry() {
        val state = ComposeNavigationState()
        state.showDetailLoading(post("one"))
        state.showDetailDocument(document("one"))
        val parentKey = state.detail.value!!.entryKey
        state.navigate("user_space?user=author-one")
        state.showDetailLoading(post("two"))
        state.showDetailDocument(document("two"))
        state.appendDetailReplies("root-one", listOf(JSONObject().put("commentid", "reply-one")), parentKey)
        assertEquals("two", state.detail.value!!.item.id)
        assertEquals(1, state.detail.value!!.comments.single().getJSONArray("comment").length())
        back(state, "user_space?user=author-one")
        back(state, "detail")
        assertEquals(2, state.detail.value!!.comments.single().getJSONArray("comment").length())
        back(state, "feed")
        state.showDetailLoading(post("one"))
        val freshKey = state.detail.value!!.entryKey
        state.updateDetail(parentKey) { it.copy(error = "stale") }
        assertNotEquals(parentKey, freshKey)
        assertEquals("", state.detail.value!!.error)
    }

    @Test fun refreshingTheSamePostDoesNotPushAnotherReturnFrame() {
        val state = ComposeNavigationState()
        state.showDetailLoading(post("one"))
        val key = state.detail.value!!.entryKey
        repeat(3) { state.showDetailLoading(post("one")) }
        assertEquals(key, state.detail.value!!.entryKey)
        assertEquals("feed", state.backTarget())
        back(state, "feed")
    }

    private fun back(state: ComposeNavigationState, expected: String) {
        assertEquals(expected, state.backTarget())
        assertTrue(state.restoreSavedDetailIfNeeded(expected))
        state.setRoute(expected)
    }

    private fun post(id: String) = FeedItem.from(JSONObject().put("linkid", id))
    private fun document(id: String) = ComposeDetailDocument.parse(JSONObject().put("result",
        JSONObject().put("link", JSONObject().put("linkid", id).put("text", "body-$id"))
            .put("comments", JSONArray().put(JSONObject().put("comment", JSONArray().put(
                JSONObject().put("commentid", "root-$id").put("text", "comment-$id")))))), post(id), false)
}
