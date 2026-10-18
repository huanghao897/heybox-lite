package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    shadows = [DetailLoadCompletionApiShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeAccountSessionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var session: SessionStore
    private lateinit var api: ApiClient
    private lateinit var host: ComposeAppHost
    private val requests get() = Shadow.extract<DetailLoadCompletionApiShadow>(api).requests
    private val feedRequests get() = requests.filter { it.path == EndpointProvider.feeds() }
    private val searchRequests get() = requests.filter { it.path == EndpointProvider.search() }

    @After fun close() {
        if (::host.isInitialized) compose.runOnIdle { host.close() }
        if (::api.isInitialized) api.close()
    }

    @Test fun logoutWhileFeedIsBusyRestartsImmediatelyAndPreservesTheVisiblePosition() {
        showHost("feed")
        compose.runOnIdle {
            val posts = (1..20).map { post("post-$it").apply {
                liked = true; favorited = true; following = true; followPending = true
            } }
            host.feed.items.value = posts
            host.feed.presentations.value = posts.associateWith(::composeFeedPresentation)
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(8)
        compose.runOnIdle {
            val position = host.feedListState.firstVisibleItemIndex
            val offset = host.feedListState.firstVisibleItemScrollOffset
            val state = host.feedListState
            val old = feedRequests.single().callback
            session.clearSession()

            host.invalidateProfile()

            assertEquals(2, feedRequests.size)
            assertTrue(host.feed.loading.value)
            assertSame(state, host.feedListState)
            assertEquals(position, host.feedListState.firstVisibleItemIndex)
            assertEquals(offset, host.feedListState.firstVisibleItemScrollOffset)
            assertTrue(session.noImage())
            assertFalse(host.feed.items.value.any { it.liked || it.favorited || it.following || it.followPending })
            old.onSuccess(body("old-account"))
            old.onError("old failure")
            assertTrue(host.feed.loading.value)
            assertEquals(20, host.feed.items.value.size)
            feedRequests.last().callback.onError("offline")
            assertFalse(host.feed.loading.value)
            host.feed.refresh()
            assertEquals(3, feedRequests.size)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(8, host.feedListState.firstVisibleItemIndex) }
    }

    @Test fun accountSwitchInSearchRestartsItsQueryAndDefersFeedUntilReentry() {
        showHost("search")
        compose.runOnIdle {
            host.feed.loadInitial()
            host.search.setQuery("watch")
            advance()
            assertEquals(1, feedRequests.size)
            assertEquals(1, searchRequests.size)
            val oldSearch = searchRequests.single().callback
            login("77")

            host.invalidateProfile()

            assertFalse(host.feed.loading.value)
            assertFalse(host.search.loading.value)
            assertEquals("watch", host.search.query.value)
            advance()
            assertEquals(1, feedRequests.size)
            assertEquals(2, searchRequests.size)
            assertEquals(OfficialRequestParams.search("watch", 0, 20), searchRequests.last().params)
            oldSearch.onSuccess(body("old-search"))
            assertTrue(host.search.items.value.isEmpty())
            assertTrue(host.search.loading.value)
            searchRequests.last().callback.onSuccess(body("new-search"))
            assertEquals(listOf("new-search"), host.search.items.value.map { it.id })
            host.setRoute("feed")
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, feedRequests.size)
            assertEquals(OfficialRequestParams.feed(1, null, "", true), feedRequests.last().params)
            assertTrue(host.feed.loading.value)
        }
    }

    @Test fun reauthenticationOfTheSameAccountResetsDroppedRequestsOnlyOnce() {
        showHost("feed")
        compose.runOnIdle {
            val old = feedRequests.single().callback
            session.saveLogin(JSONObject().put("heyboxid", "42"))

            host.invalidateProfile()
            host.invalidateProfile()

            assertEquals("42", session.userId())
            assertEquals(2, feedRequests.size)
            assertTrue(host.feed.loading.value)
            old.onError("old failure")
            assertTrue(host.feed.loading.value)
            feedRequests.last().callback.onError("new failure")
            assertFalse(host.feed.loading.value)
        }
    }

    @Test fun changingOnlyReadingSettingsRecomposesTheMountedDetail() {
        showHost("settings_home")
        compose.runOnIdle {
            val item = post("reading-settings")
            host.showDetailLoading(item)
            val body = JSONObject().put("result", JSONObject().put("link", JSONObject()
                .put("linkid", item.id).put("content", "mounted-reading-body")))
            host.showDetailDocument(ComposeDetailDocument.parse(body, item, false))
        }
        val before = readingStyle()
        assertEquals(FontWeight.Medium, before.fontWeight)
        compose.runOnIdle {
            session.setBodyBold(false)
            session.setBodyLetterSpacing(40)
            host.updateTheme(ThemeTokens.of(false, Color.BLACK, Color.GRAY), false, 1f, 1f)
        }
        val after = readingStyle()
        assertEquals(FontWeight.Normal, after.fontWeight)
        assertEquals(0.2.em, after.letterSpacing)
        assertEquals(before.fontSize, after.fontSize)
    }

    private fun readingStyle(): androidx.compose.ui.text.TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("mounted-reading-body", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style
    }

    private fun showHost(route: String) {
        compose.runOnUiThread {
            session = SessionStore(compose.activity).apply {
                setNoImage(true)
                setMotionLevel(MotionLevel.OFF)
            }
            login("42")
            api = ApiClient(session)
            val parent = FrameLayout(compose.activity)
            host = ComposeAppHost(compose.activity, parent, session, api, LocalCache(compose.activity),
                Handler(Looper.getMainLooper()), ReadingTimeTracker(compose.activity), null,
                ThemeTokens.of(false, Color.BLACK, Color.GRAY), false, 1f, 1f,
                ComposeCommentActionsTestFixtures.RecordingCallbacks(), ComposeToast {})
            host.setRoute(route)
            compose.activity.setContentView(parent)
        }
        compose.waitForIdle()
    }

    private fun login(id: String) {
        val cookie = LegacyCookieCrypto(compose.activity).encrypt("heybox_id=$id; pkey=offline-session-test")
        compose.activity.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE)
            .edit().putString(SecureStrings.encryptedCookieKey(), cookie).commit()
        session.saveLogin(JSONObject().put("heyboxid", id))
        assertTrue(session.isLoggedIn())
    }

    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(420))
    private fun post(id: String) = FeedItem.from(JSONObject().put("linkid", id).put("title", id))
    private fun body(id: String) = JSONObject().put("result", JSONObject().put("links",
        JSONArray().put(JSONObject().put("linkid", id).put("title", id))))
}
