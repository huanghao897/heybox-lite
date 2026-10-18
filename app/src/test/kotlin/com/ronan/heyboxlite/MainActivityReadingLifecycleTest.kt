package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.ronan.heyboxlite.ComposeCommentActionsTestFixtures.RecordingCallbacks
import java.io.Closeable
import java.time.Duration
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    instrumentedPackages = ["com.ronan.heyboxlite"], shadows = [ReadingLifecyclePresenceShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityReadingLifecycleTest {
    @Test fun composeReadingResumesAfterBackgroundWithoutActivityMetadataMirrors() {
        Fixture().use { f ->
            f.openCompose(loaded = true)
            assertNull(f.activity.currentDetailItem)
            assertNull(f.activity.currentDetailBody)

            f.lifecycle.onResume()
            advance(1)
            f.lifecycle.onPause()
            assertEquals(1000L, f.reading.stats().totalArticleMs)

            advance(5)
            f.lifecycle.onResume()
            advance(2)
            f.lifecycle.onPause()

            val stats = f.reading.stats()
            assertEquals(3000L, stats.totalArticleMs)
            assertEquals(0L, stats.totalPostMs)
            assertEquals(1, stats.totalCount)
            assertEquals("compose-topic", stats.topics.single().name)
            assertEquals(3000L, stats.topics.single().ms)
        }
    }

    @Test fun loadingComposeDetailDoesNotResumeAnOlderNativePost() {
        Fixture().use { f ->
            f.setNativePost()
            f.openCompose(loaded = false)

            f.lifecycle.onResume()
            advance(2)
            f.lifecycle.onPause()

            assertEquals(0L, f.reading.stats().totalMs())
            assertEquals(0, f.reading.stats().totalCount)
        }
    }

    @Test fun hiddenComposeSurfaceLeavesReadingOwnershipWithTheNativeDetail() {
        Fixture().use { f ->
            f.openCompose(loaded = true)
            f.setNativePost()
            f.activity.composeLayer.visibility = View.INVISIBLE

            f.lifecycle.onResume()
            advance(2)
            f.lifecycle.onPause()

            val stats = f.reading.stats()
            assertEquals(0L, stats.totalArticleMs)
            assertEquals(2000L, stats.totalPostMs)
            assertEquals("native-topic", stats.topics.single().name)
        }
    }

    @Test fun aSavedDetailDoesNotKeepTimingOnAnotherRoute() {
        Fixture().use { f ->
            f.openCompose(loaded = true)
            f.lifecycle.onResume()
            advance(1)
            f.lifecycle.onPause()
            f.host.navigation.navigate("user_space?user=author")
            f.activity.screen = "user_space"

            f.lifecycle.onResume()
            advance(3)
            f.lifecycle.onPause()

            assertEquals(1000L, f.reading.stats().totalArticleMs)
            assertNull(f.activity.composeDetailContext())
        }
    }

    private class Fixture : Closeable {
        private val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        // Attach the native context without starting MainActivity's live startup services.
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        private val session = SessionStore(activity)
        private val api = ApiClient(session)
        val reading = ReadingTimeTracker(activity)
        val host: ComposeAppHost
        val lifecycle = MainActivityLifecycle(activity)

        init {
            activity.session = session
            activity.readingTimeTracker = reading
            activity.composeLayer = FrameLayout(activity)
            host = ComposeAppHost(owner.get(), activity.composeLayer, session, api,
                LocalCache(activity), activity.handler, reading, null,
                ThemeTokens.of(false, Color.BLACK, Color.GRAY), false, 1f, 1f,
                RecordingCallbacks(), ComposeToast {})
            activity.composeAppHost = host
        }

        fun openCompose(loaded: Boolean) {
            val item = FeedItem.from(JSONObject().put("linkid", "compose-post"))
            item.article = true
            item.topicName = "compose-topic"
            host.navigation.showDetailLoading(item)
            if (loaded) {
                host.navigation.detail.value = host.navigation.detail.value!!.copy(
                    body = JSONObject(), loading = false)
            }
            activity.screen = "detail"
        }

        fun setNativePost() {
            activity.currentDetailItem = FeedItem.from(JSONObject().put("linkid", "native-post"))
            activity.currentDetailItem.topicName = "native-topic"
            activity.currentDetailBody = JSONObject()
        }

        override fun close() {
            activity.handler.removeCallbacksAndMessages(null)
            host.close()
            api.close()
            owner.pause().stop().destroy()
        }
    }

    private fun advance(seconds: Long) = ShadowSystemClock.advanceBy(Duration.ofSeconds(seconds))
}

@Implements(value = PresenceReporter::class, isInAndroidSdk = false)
internal class ReadingLifecyclePresenceShadow {
    companion object {
        @JvmStatic @Implementation fun ping(session: SessionStore, reading: ReadingTimeTracker,
            callback: PresenceReporter.Callback?) = Unit
    }
}
