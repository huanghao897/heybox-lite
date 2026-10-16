package com.ronan.heyboxlite

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeDetailRefreshStateTest {
    @Test fun cachedParentKeepsItsUnfinishedRefreshAcrossAuthorAndNestedPostReturn() {
        val navigation = ComposeNavigationState()
        navigation.showDetailLoading(post("parent"))
        navigation.showDetailDocument(document("parent"))
        val key = navigation.detail.value!!.entryKey
        assertFalse(navigation.detail.value!!.loading)
        assertTrue(navigation.detail.value!!.refreshPending)
        navigation.navigate("user_space?user=author")
        navigation.showDetailLoading(post("child"))
        navigation.showDetailDocument(document("child"))
        navigation.detail.value = navigation.detail.value!!.copy(refreshPending = false)

        back(navigation, "user_space?user=author")
        back(navigation, "detail")

        val parent = navigation.detail.value!!
        assertEquals(key, parent.entryKey)
        assertEquals("parent", parent.item.id)
        assertEquals("body-parent", parent.content.single().value)
        assertFalse(parent.loading)
        assertTrue("Cached content must not hide an unfinished parent refresh", parent.refreshPending)
    }

    @Test fun completedParentDoesNotBecomePendingWhenANestedChildStillNeedsRefresh() {
        val navigation = ComposeNavigationState()
        navigation.showDetailLoading(post("parent"))
        navigation.showDetailDocument(document("parent"))
        navigation.detail.value = navigation.detail.value!!.copy(refreshPending = false)
        val key = navigation.detail.value!!.entryKey
        navigation.navigate("user_space?user=author")
        navigation.showDetailLoading(post("child"))
        navigation.showDetailDocument(document("child"))
        assertTrue(navigation.detail.value!!.refreshPending)

        back(navigation, "user_space?user=author")
        back(navigation, "detail")

        assertEquals(key, navigation.detail.value!!.entryKey)
        assertFalse(navigation.detail.value!!.loading)
        assertFalse(navigation.detail.value!!.refreshPending)
    }

    @Test fun sameEntryReloadRearmsRefreshPendingBeforeCachedParentReturn() {
        val navigation = ComposeNavigationState()
        val item = post("parent")
        navigation.showDetailLoading(item)
        navigation.showDetailDocument(document("parent"))
        navigation.detail.value = navigation.detail.value!!.copy(refreshPending = false)
        val key = navigation.detail.value!!.entryKey

        navigation.showDetailLoading(item)

        assertEquals(key, navigation.detail.value!!.entryKey)
        assertTrue("A new request on the same entry needs its own pending refresh",
            navigation.detail.value!!.refreshPending)
        navigation.showDetailDocument(document("parent"))
        navigation.navigate("user_space?user=author")
        back(navigation, "detail")
        assertFalse(navigation.detail.value!!.loading)
        assertTrue("Returning after cached rendering must resume the cancelled reload",
            navigation.detail.value!!.refreshPending)
    }

    private fun back(navigation: ComposeNavigationState, expected: String) {
        assertEquals(expected, navigation.backTarget())
        assertTrue(navigation.restoreSavedDetailIfNeeded(expected))
        navigation.setRoute(expected)
    }

    private fun post(id: String) = FeedItem.from(JSONObject().put("linkid", id))

    private fun document(id: String) = ComposeDetailDocument.parse(JSONObject().put("result",
        JSONObject().put("link", JSONObject().put("linkid", id).put("text", "body-$id"))), post(id), false)
}
