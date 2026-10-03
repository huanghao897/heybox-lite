package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class ComposeWatchUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var api: ApiClient? = null

    @After fun closeServices() { api?.close() }

    @Test fun compactSwitchKeepsWholeRowToggleSemantics() {
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                var checked by remember { mutableStateOf(false) }
                WatchSwitchRow("表冠滚动", checked) { checked = it }
            }
        }
        compose.onNodeWithText("表冠滚动").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("表冠滚动").performClick().assertIsOff()
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun roundReadingCenterCanReachAndOpenTheLastEntry() {
        val services = services(true)
        val post = FeedItem.from(JSONObject().put("linkid", "one").put("title", "继续阅读的测试文章"))
        var opened = false
        compose.setContent {
            HeyboxComposeTheme(services.theme) {
                Box(Modifier.fillMaxSize()) {
                    ComposeReadingCenterScreen(ComposeReadingCenterState(post, 12, 400, "今天 5 分钟", true),
                        false, services, {}, {}, {}, { opened = true }, {})
                }
            }
        }
        compose.onNodeWithText("历史记录").performScrollTo().assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.35f) }
        compose.onNodeWithText("历史记录").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, opened) }
        val backCenter = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot.center
        val bounds = compose.activity.window.decorView
        val radius = minOf(bounds.width, bounds.height) / 2f
        val dx = backCenter.x - bounds.width / 2f
        val dy = backCenter.y - bounds.height / 2f
        org.junit.Assert.assertTrue("Return icon must be inside the circular display",
            dx * dx + dy * dy < (radius - 8f) * (radius - 8f))
        val lastCenter = compose.onNodeWithText("历史记录").fetchSemanticsNode().boundsInRoot.center
        val lastDx = lastCenter.x - bounds.width / 2f
        val lastDy = lastCenter.y - bounds.height / 2f
        org.junit.Assert.assertTrue("Last menu item must be reachable inside the circular display",
            lastDx * lastDx + lastDy * lastDy < (radius - 8f) * (radius - 8f))
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            android.graphics.Bitmap.createBitmap(view.width, view.height,
                android.graphics.Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        org.junit.Assert.assertTrue("Rendered page must not be blank", pixels.toSet().size > 5)
        val output = File("build/outputs/ui-regression/reading-center-round.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun historySearchFiltersRealRowsAndSurvivesStateRestoration() {
        val services = services(false)
        val posts = listOf("Alpha", "Beta").mapIndexed { index, title ->
            FeedItem.from(JSONObject().put("linkid", "post-$index").put("title", title))
        }
        val restoration = StateRestorationTester(compose)
        val query = mutableStateOf("")
        restoration.setContent {
            HeyboxComposeTheme(services.theme) {
                ComposeHistoryScreen(ComposeSavedPostsState(posts, loaded = true), query.value,
                    { query.value = it }, services, {}, { _, _ -> }, {}, {}, rememberLazyListState())
            }
        }
        compose.onNodeWithContentDescription("搜索历史").performTextInput("missing")
        compose.onNodeWithText("没有找到相关历史记录").assertIsDisplayed()
        compose.onNodeWithText("×").performClick()
        compose.onNodeWithContentDescription("搜索历史").performTextInput("Alpha")
        compose.onNodeWithText("没有找到相关历史记录").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("搜索历史").assertTextContains("Alpha")
    }

    private fun services(round: Boolean): ComposeServices {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val session = SessionStore(context)
        session.setNoImage(true)
        val client = ApiClient(session)
        api = client
        return ComposeServices(compose.activity, session, client, GameDetailClient(client),
            LocalCache(context), Handler(Looper.getMainLooper()), ReadingTimeTracker(context), null,
            composePreviewTheme(round), ComposeToast {})
    }
}
