package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeFavoritesScrollingUiTest : ComposeRoundLayoutHarness() {
    @Test fun squareTabsScrollWithBothPostAndFolderLists() = verifyScrolling()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundTabsScrollWithBothPostAndFolderLists() = verifyScrolling()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundTabsScrollWithBothPostAndFolderLists() = verifyScrolling()

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundTabsScrollWithBothPostAndFolderLists() = verifyScrolling(forceRound = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundTabsStayCompactWithLargeTextAndSmallUi() = verifyScrolling(uiScale = 0.8f, textScale = 1.4f)

    @Test fun emptyAndErrorListsKeepTabsAndUseTheSelectedRetryCallback() {
        val services = layoutServices()
        val tab = mutableStateOf(ComposeFavoriteTab.POSTS)
        val state = mutableStateOf(ComposeSavedPostsState(loading = true))
        var postRetries = 0
        var folderRetries = 0
        showLayout(services) {
            ComposeFavoritesScreen(state.value, emptyList(), tab.value, false, "Folder offline",
                services, { tab.value = it }, {}, {}, { _, _ -> }, { postRetries++ },
                { folderRetries++ }, {}, rememberLazyListState())
        }
        compose.onNodeWithTag("favorites-tabs").assertIsDisplayed()
        compose.runOnIdle { state.value = ComposeSavedPostsState(error = "Post offline") }
        touchReachable("重试")
        text("暂无内容").assertDoesNotExist()
        text("加载失败").assertExists()
        touchTab("收藏夹")
        tab("收藏夹").assertIsSelected()
        touchReachable("重试")
        compose.runOnIdle {
            assertEquals(1, postRetries)
            assertEquals(1, folderRetries)
        }
    }

    private fun verifyScrolling(forceRound: Boolean = false, uiScale: Float = 1f, textScale: Float = 1f) {
        val services = layoutServices(forceRound, uiScale, textScale)
        val posts = (1..8).map {
            FeedItem.from(JSONObject().put("linkid", "post-$it").put("title", "Post $it"))
        }
        val folders = (1..8).map { ComposeFavoriteFolder("folder-$it", "Folder $it", it) }
        val tab = mutableStateOf(ComposeFavoriteTab.POSTS)
        val openedPosts = mutableListOf<String>()
        val openedFolders = mutableListOf<String>()
        lateinit var state: LazyListState
        showLayout(services) {
            state = rememberLazyListState()
            ComposeFavoritesScreen(ComposeSavedPostsState(posts, loaded = true), folders, tab.value,
                false, "", services, { tab.value = it }, { openedPosts += it.id },
                { openedFolders += it.id }, { _, _ -> }, {}, {}, {}, state)
        }
        val list = compose.onNodeWithTag("favorites-list")
        val tabs = compose.onNodeWithTag("favorites-tabs")
        tabs.assertIsDisplayed()
        tab("帖子").assertIsSelected()
        val panel = tabs.fetchSemanticsNode().boundsInRoot
        val control = tab("帖子").fetchSemanticsNode().boundsInRoot
        assertEquals("Only compact padding belongs inside the painted tab panel",
            6f * uiScale, panel.height - control.height, 1.5f)
        assertTrue("Default Material button sizing must not inflate the tabs", panel.height <= 41f)
        listOf("帖子", "收藏夹").forEach { label ->
            assertPaintedLabelInsideDisplay(label, compose.onNode(
                hasText(label) and hasAnyAncestor(hasTestTag("favorites-tabs")), useUnmergedTree = true))
        }
        assertNoTopToolbar()
        compose.runOnIdle { captureMembershipUi(compose.activity, "favorites-${testName.methodName}-initial") }
        list.performScrollToIndex(posts.size)
        tabs.assertIsNotDisplayed()
        touchReachable("Post 8")
        compose.runOnIdle { assertTrue(state.firstVisibleItemIndex > 0) }
        compose.runOnIdle { captureMembershipUi(compose.activity, "favorites-${testName.methodName}-posts-scrolled") }
        list.performScrollToIndex(0)
        touchTab("收藏夹")
        tab("收藏夹").assertIsSelected()
        list.performScrollToIndex(folders.size)
        tabs.assertIsNotDisplayed()
        touchReachable("Folder 8")
        compose.runOnIdle { captureMembershipUi(compose.activity, "favorites-${testName.methodName}-folders-scrolled") }
        list.performScrollToIndex(0)
        touchTab("帖子")
        tab("帖子").assertIsSelected()
        touchReachable("Post 1")
        compose.runOnIdle {
            assertEquals(listOf("post-8", "post-1"), openedPosts)
            assertEquals(listOf("folder-8"), openedFolders)
        }
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("favorites-tabs")))

    private fun touchTab(label: String) {
        val target = circleReach(tab(label))
        assertControlInsideDisplay(target, "favorite tab $label")
        target.performTouchInput { click() }
        compose.waitForIdle()
    }
}
