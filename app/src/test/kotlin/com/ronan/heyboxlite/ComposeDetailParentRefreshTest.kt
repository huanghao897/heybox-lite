package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.ronan.heyboxlite.ComposeCommentActionsTestFixtures.RecordingCallbacks
import java.io.Closeable
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [DetailLoadCompletionApiShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeDetailParentRefreshTest {
    @Test fun cachedParentStillNeedsLoadAfterNestedChildRequestFinishesAndReturns() {
        Fixture().use { f ->
            val navigation = f.host.navigation
            navigation.showDetailLoading(post("parent"))
            f.host.showDetailDocument(document("parent"))
            val key = f.host.currentDetailEntryKey()
            assertFalse(navigation.detail.value!!.loading)
            assertTrue(f.host.detailNeedsLoad())
            navigation.navigate("user_space?user=author")
            navigation.showDetailLoading(post("child"))
            f.host.showDetailDocument(document("child"))

            f.host.finishDetailRequest()

            assertFalse(f.host.detailNeedsLoad())
            back(navigation, "user_space?user=author")
            back(navigation, "detail")
            assertEquals(key, f.host.currentDetailEntryKey())
            assertEquals("parent", f.host.currentDetailItem()!!.id)
            assertFalse(navigation.detail.value!!.loading)
            assertTrue("Finishing a child must not finish its saved parent's request", f.host.detailNeedsLoad())
        }
    }

    @Test fun completedCachedParentDoesNotReloadJustBecauseItsChildRequestWasCancelled() {
        Fixture().use { f ->
            val navigation = f.host.navigation
            navigation.showDetailLoading(post("parent"))
            f.host.showDetailDocument(document("parent"))
            f.host.finishDetailRequest()
            val key = f.host.currentDetailEntryKey()
            assertFalse(f.host.detailNeedsLoad())
            navigation.navigate("user_space?user=author")
            navigation.showDetailLoading(post("child"))
            f.host.showDetailDocument(document("child"))
            assertTrue(f.host.detailNeedsLoad())

            back(navigation, "user_space?user=author")
            back(navigation, "detail")

            assertEquals(key, f.host.currentDetailEntryKey())
            assertEquals("body-parent", navigation.detail.value!!.content.single().value)
            assertFalse("A completed parent must keep its own completion state", f.host.detailNeedsLoad())
        }
    }

    private class Fixture : Closeable {
        private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        private val activity = controller.get()
        private val session = SessionStore(activity)
        private val api = ApiClient(session)
        // Keep the parent unattached so creating a real host does not start screen effects.
        val host = ComposeAppHost(activity, FrameLayout(activity), session, api,
            LocalCache(activity), Handler(Looper.getMainLooper()), ReadingTimeTracker(activity), null,
            ThemeTokens.of(false, Color.BLACK, Color.GRAY), false, 1f, 1f,
            RecordingCallbacks(), ComposeToast {})

        override fun close() {
            host.close()
            api.close()
            controller.pause().stop().destroy()
        }
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
