package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    shadows = [DetailLoadCompletionApiShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeTutorialHostUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var host: ComposeAppHost
    private lateinit var api: ApiClient

    @After fun close() {
        if (::host.isInitialized) compose.runOnUiThread { host.close() }
        if (::api.isInitialized) api.close()
    }

    @Test fun mountedHostOffersItsRealRouteAndBackDismissesOnlyTheTutorial() {
        mount("feed")
        compose.onNodeWithTag("tutorial-viewport").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(TutorialTopic.HOME, host.tutorials.topic)
            assertTrue(host.handleBack())
            assertEquals("feed", host.currentRoute())
            host.showDetailLoading(FeedItem.from(JSONObject().put("linkid", "fixture")))
        }
        compose.onNodeWithTag("tutorial-viewport").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(TutorialTopic.DETAIL, host.tutorials.topic)
            host.setSurfaceActive(false)
        }
        compose.onNodeWithTag("tutorial-viewport").assertDoesNotExist()
    }

    @Test fun realHostReselectRefreshesComposeAfterTheOldRequestFinishes() {
        TutorialTopic.entries.forEach(TutorialStore(compose.activity)::complete)
        mount("feed")
        val requests = Shadow.extract<DetailLoadCompletionApiShadow>(api).requests
        compose.runOnIdle {
            assertEquals(1, requests.size)
            host.reselectFeed()
        }
        compose.runOnIdle {
            assertEquals(1, requests.size)
            requests.single().callback.onError("offline fixture")
        }
        compose.runOnIdle {
            assertEquals(2, requests.size)
            assertEquals("icon", requests.last().params["refresh_type"])
        }
    }

    private fun mount(route: String) {
        compose.runOnUiThread {
            val activity = compose.activity
            val session = SessionStore(activity).apply {
                setNoImage(true)
                setMotionLevel(MotionLevel.OFF)
            }
            api = ApiClient(session)
            val parent = FrameLayout(activity)
            host = ComposeAppHost(activity, parent, session, api, LocalCache(activity),
                Handler(Looper.getMainLooper()), ReadingTimeTracker(activity), null,
                ThemeTokens.of(true, 0, 0), false, 1f, 1f,
                ComposeCommentActionsTestFixtures.RecordingCallbacks(), ComposeToast {})
            host.setRoute(route)
            activity.setContentView(parent)
        }
        compose.waitForIdle()
    }
}
