package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeSearchHistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var api: ApiClient? = null

    @After fun closeClient() { api?.close() }

    @Test fun previousSearchesAreVisibleAndSelectingOneRestoresTheQuery() = verifyHistory(false)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun historyFitsTheRoundWatchAndItsClearControlIsReachable() = verifyHistory(true)

    private fun verifyHistory(round: Boolean) {
        val session = SessionStore(compose.activity)
        session.clearSearchHistory()
        session.addSearchHistory("Steam")
        session.addSearchHistory("Watch")
        val client = ApiClient(session).also { api = it }
        val controller = ComposeSearchController(session, client, LocalCache(compose.activity),
            Handler(Looper.getMainLooper()))
        val services = ComposeServices(compose.activity, session, client, GameDetailClient(client),
            LocalCache(compose.activity), Handler(Looper.getMainLooper()),
            ReadingTimeTracker(compose.activity), null,
            composePreviewTheme(round).copy(motionLevel = MotionLevel.OFF), ComposeToast {})
        val query = mutableStateOf("")
        compose.setContent {
            Box(Modifier.size(if (round) 192.dp else 240.dp, if (round) 192.dp else 320.dp)) {
                ComposeSearchScreen(query.value, emptyList(), false, false, services,
                    onQueryChange = { query.value = it }, onOpen = {}, onBack = {}, onLoadMore = {},
                    history = controller.history.value, onClearHistory = controller::clearHistory,
                    observeLoadMore = false)
            }
        }
        compose.onNodeWithText("最近搜索").assertIsDisplayed()
        compose.onNodeWithText("Watch").assertIsDisplayed()
        compose.onNodeWithText("Steam").assertIsDisplayed()
        compose.runOnIdle { captureMembershipUi(compose.activity, "search-history-${if (round) "round192" else "square240"}") }
        compose.onNodeWithText("Watch").performClick()
        compose.runOnIdle { assertEquals("Watch", query.value) }
        compose.onNodeWithText("最近搜索").assertDoesNotExist()
        compose.runOnIdle { query.value = "" }
        compose.onNodeWithContentDescription("清空搜索历史").performClick()
        compose.runOnIdle {
            assertTrue(controller.history.value.isEmpty())
            assertTrue(SessionStore(compose.activity).searchHistory().isEmpty())
            controller.close()
        }
        compose.onNodeWithText("最近搜索").assertDoesNotExist()
        compose.onNodeWithText("输入关键词开始搜索").assertIsDisplayed()
    }
}
