package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeSearchStatusUiTest : ComposeMembershipUiRegressionHarness() {
    private var client: ApiClient? = null

    @After fun closeClient() { client?.close() }

    @Test fun firstPageFailureShowsRetryInsteadOfClaimingNoResults() {
        val services = services()
        var retries = 0
        setScreen {
            ComposeSearchScreen("watch", emptyList(), false, false, services,
                {}, {}, {}, {}, observeLoadMore = false, error = "Network unavailable",
                onRetry = { retries++ })
        }
        text("Network unavailable").assertIsDisplayed()
        text("暂时没找到相关帖子").assertDoesNotExist()
        text("没有找到相关帖子").assertDoesNotExist()
        text("重试").performClick()
        assertEquals(1, retries)
    }

    @Test fun firstPageErrorUsesCrownInputToReachRetryInsteadOfTheEmptyResultList() {
        val base = services()
        val current = mutableStateOf(base)
        val listState = LazyListState()
        var retries = 0
        setScreen(base.theme) {
            ComposeSearchScreen("watch", emptyList(), false, false, current.value,
                {}, {}, {}, {}, listState = listState, observeLoadMore = false,
                error = "无法加载搜索内容，请检查网络后重试。".repeat(12), onRetry = { retries++ })
        }
        assertEquals(0, listState.firstVisibleItemScrollOffset)
        compose.runOnIdle {
            current.value = base.copy(theme = base.theme.copy(
                rotaryRequest = ComposeRotaryRequest(1, 100)))
        }
        compose.waitForIdle()
        assertTrue("Crown rotation must scroll the error content toward retry",
            listState.firstVisibleItemScrollOffset > 0)
        compose.runOnIdle {
            current.value = base.copy(theme = base.theme.copy(
                rotaryRequest = ComposeRotaryRequest(2, 10000)))
        }
        compose.waitForIdle()
        text("重试").assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }

    @Test fun appendFailureKeepsTheExistingPostAndRetryReachable() {
        val services = services()
        val post = FeedItem.from(JSONObject().put("linkid", "one").put("title", "Existing result"))
        var retries = 0
        setScreen {
            ComposeSearchScreen("watch", listOf(post), false, false, services,
                {}, {}, {}, {}, observeLoadMore = false, error = "Network unavailable",
                onRetry = { retries++ })
        }
        text("Existing result").assertExists()
        tapReachable("重试")
        assertEquals(1, retries)
        text("Existing result").assertExists()
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun largeSearchTextKeepsItsFullHeightAndCanStillBeCleared() {
        val palette = theme().copy(uiScale = 0.5f, textScale = 2.4f)
        val query = mutableStateOf("Hi")
        setScreen(palette, systemFontScale = 1.3f) {
            Box(Modifier.fillMaxSize()) {
                ComposeLeadingSearchInput(query.value, "Search", { query.value = it })
            }
        }
        assertInputFits("Hi")
        description("Search").performTextReplacement("OK")
        assertInputFits("OK")
        description("清除").performClick()
        assertEquals("", query.value)
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun longErrorInLargeTypeCanScrollToItsRetryAction() {
        var retries = 0
        setScreen(theme(largeLight = true), systemFontScale = 1.25f) {
            WatchErrorState("无法加载内容，请检查网络连接后重试。".repeat(5),
                onRetry = { retries++ }, fill = true)
        }
        tapReachable("重试")
        assertEquals(1, retries)
    }

    @Test fun fullPageLoadingCanAlsoBeUsedInsideAnUnboundedLazyItem() {
        setScreen {
            LazyColumn { item { WatchLoading(fill = true) } }
        }
        text("加载中").assertIsDisplayed()
    }

    private fun assertInputFits(value: String) {
        val input = description("Search")
        val layouts = mutableListOf<TextLayoutResult>()
        input.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = paintedTextLayout(layouts.single())
        assertEquals(value, layout.layoutInput.text.text)
        assertFalse("The input must not clip its glyphs", layout.hasVisualOverflow)
        assertTrue("The field must grow with its text instead of cutting its height",
            input.fetchSemanticsNode().size.height >= layout.size.height)
    }

    private fun services(): ComposeServices {
        val session = SessionStore(compose.activity).apply { setNoImage(true) }
        val api = ApiClient(session).also { client = it }
        return ComposeServices(compose.activity, session, api, GameDetailClient(api),
            LocalCache(compose.activity), Handler(Looper.getMainLooper()),
            ReadingTimeTracker(compose.activity), null, theme(), ComposeToast {})
    }
}
