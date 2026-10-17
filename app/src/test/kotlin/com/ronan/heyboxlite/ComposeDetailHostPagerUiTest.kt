package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeDetailHostPagerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var host: ComposeAppHost
    private var api: ApiClient? = null
    private val backs = mutableListOf<String>()

    @After fun close() {
        if (::host.isInitialized) compose.runOnUiThread { host.close() }
        api?.close()
    }

    @Test fun realHostReturnsFromCommentsBeforeLeavingThePost() {
        showHost()
        comments()
        compose.runOnIdle {
            assertNull(host.composeSwipeTarget(host.navigation.presentationRoute(), 1))
            assertTrue(host.handleBack())
        }
        compose.waitForIdle()
        compose.onNodeWithTag("compose-detail-list").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(backs.isEmpty())
            assertNotNull(host.composeSwipeTarget(host.navigation.presentationRoute(), 1))
            assertTrue(host.handleBack())
            assertEquals(listOf("feed"), backs)
        }
    }

    @Test fun realRouteWithoutReturnToolbarKeepsSystemBackPagerPriority() {
        showHost()
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.addCallback(compose.activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { host.handleBack() }
            })
        }
        comments()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("compose-detail-list").assertIsDisplayed()
        assertTrue(backs.isEmpty())
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(listOf("feed"), backs) }
    }

    @Test fun nestedPostReturnsToTheParentsSavedCommentsPage() {
        showHost()
        comments()
        compose.onNodeWithTag("compose-detail-comments-list").performScrollToIndex(4)
        compose.runOnIdle { enter("child") }
        compose.onNodeWithTag("compose-detail-list").assertIsDisplayed()
        compose.runOnIdle { assertTrue(host.handleBack()) }
        compose.onNodeWithTag("compose-detail-comments-list").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("parent", host.currentDetailItem()?.id)
            assertNull(host.composeSwipeTarget(host.navigation.presentationRoute(), 1))
        }
    }

    @Test fun squareSecondaryMenusUseTheFullViewportAndKeepSystemBack() = verifySecondaryMenus()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundSecondaryMenusUseTheFullViewportAndKeepSystemBack() = verifySecondaryMenus()

    @Test fun headerlessSecondaryMenuKeepsTheRealRightSwipeBack() {
        showHost()
        compose.runOnIdle { host.setRoute("settings_home") }
        compose.onNodeWithTag("watch-page-header").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("profile"), backs) }
    }

    @Test fun primaryProfileRetainsItsOwnHeading() {
        showHost()
        compose.runOnIdle { host.setRoute("profile") }
        compose.onNodeWithTag("watch-page-header").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    private fun verifySecondaryMenus() {
        showHost()
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.addCallback(compose.activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { host.handleBack() }
            })
        }
        val routes = listOf("settings_home", "display_settings", "display_preview", "startup_settings",
            "app_settings", "video_settings", "splash_preview", "about", "announcement_board",
            "reading_center", "favorites", "favorite_folder?folder=test&name=Folder", "watch_later",
            "reading_history", "reading_stats", "leaderboard", "search", "login", "user_space",
            "checkin_center", "checkin_page_pairing", "checkin_page_mobile_login", "checkin_page_task_settings",
            "checkin_page_membership", "checkin_page_checkout", "checkin_page_redeem", "checkin_page_purchases",
            "checkin_page_history", "checkin_page_history_detail")
        routes.forEach { route ->
            compose.runOnIdle { host.setRoute(route) }
            compose.onNodeWithTag("watch-page-header").assertDoesNotExist()
            compose.onNodeWithContentDescription("返回").assertDoesNotExist()
            val viewport = compose.onAllNodes(hasTestTag("watch-page-content") or
                hasTestTag("saved-page-content") or hasTestTag("search-page-content"))
                .fetchSemanticsNodes().single().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertEquals("$route must not reserve toolbar height", root.top, viewport.top, 0.5f)
            assertEquals("$route content must fill its viewport", root.bottom, viewport.bottom, 0.5f)
            if (route in setOf("settings_home", "reading_center", "favorites", "search")) {
                compose.runOnIdle { captureMembershipUi(compose.activity, "headerless-$route") }
            }
            compose.runOnIdle {
                val target = host.navigation.backTarget()
                compose.activity.onBackPressedDispatcher.onBackPressed()
                assertEquals("$route system back", target, backs.last())
            }
        }
    }

    private fun comments() {
        compose.onNodeWithTag("compose-detail-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("compose-detail-comments-list").assertIsDisplayed()
    }

    private fun showHost() {
        compose.runOnUiThread {
            val activity = compose.activity
            val session = SessionStore(activity).apply {
                setNoImage(true)
                setRememberDetailScroll(false)
            }
            val client = ApiClient(session).also { api = it }
            val parent = FrameLayout(activity)
            val callbacks = object : ComposeAppCallbacks by
                ComposeCommentActionsTestFixtures.RecordingCallbacks() {
                override fun backTo(route: String) {
                    backs += route
                    if (route == "detail") host.setRoute(route)
                }
            }
            host = ComposeAppHost(activity, parent, session, client, LocalCache(activity),
                Handler(Looper.getMainLooper()), ReadingTimeTracker(activity), null,
                ThemeTokens.of(true, 0, 0), session.usesRoundLayout(), 1f, 1f, callbacks, ComposeToast {})
            enter("parent")
            activity.setContentView(parent)
        }
        compose.waitForIdle()
    }

    private fun enter(id: String) {
        val post = FeedItem.from(JSONObject().put("linkid", id).put("title", id))
        host.showDetailLoading(post)
        host.navigation.detail.value = host.navigation.detail.value!!.copy(
            content = listOf(RichContent.Block.text("Body $id")),
            comments = (1..10).map { ComposeCommentActionsTestFixtures.comment("$id-$it") },
            loading = false,
        )
    }
}
