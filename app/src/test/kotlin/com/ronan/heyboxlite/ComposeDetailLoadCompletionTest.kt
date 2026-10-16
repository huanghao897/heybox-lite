package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.Closeable
import java.time.Duration
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [DetailLoadCompletionApiShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeDetailLoadCompletionTest {
    @Test fun plainDetailReportsCompletionOnceWithoutWaitingForTheDelayedRender() {
        Fixture().use { f ->
            f.loader.load(f.item)
            assertEquals(0, f.host.finished)
            assertEquals(EndpointProvider.linkTree(), f.requests.single().path)

            f.requests.single().callback.onSuccess(body("fresh"))

            assertEquals(1, f.host.finished)
            assertTrue("A finished response is still pending publication", f.loader.hasPendingRender())
            f.advance()
            assertFalse(f.loader.hasPendingRender())
            assertEquals("fresh", f.host.documents.last().getJSONObject("result")
                .getJSONObject("link").getString("text"))
            assertEquals(1, f.host.finished)
            assertEquals(1, f.requests.size)
        }
    }

    @Test fun articleRequestRemainsPendingUntilWebEnrichmentSucceedsOrFails() {
        for (success in listOf(true, false)) Fixture(article = true).use { f ->
            f.loader.load(f.item)
            f.advance()
            assertEquals(0, f.host.finished)
            f.requests.single().callback.onSuccess(body("article"))
            assertEquals(2, f.requests.size)
            assertEquals(EndpointProvider.linkWebView(), f.requests.last().path)
            f.advance()
            assertEquals(0, f.host.finished)
            val visibleCount = f.host.documents.size

            if (success) f.requests.last().callback.onSuccess(JSONObject().put("result", JSONObject()))
            else f.requests.last().callback.onError("offline enrichment")

            assertEquals(1, f.host.finished)
            f.advance()
            assertEquals("A terminal web response must finish even without new game cards",
                visibleCount, f.host.documents.size)
            assertEquals(1, f.host.finished)
        }
    }

    @Test fun requestErrorsBlockedResponsesAndMissingLinksAllReportCompletion() {
        for (response in listOf("error", "blocked", "missing-link")) Fixture().use { f ->
            f.loader.load(f.item)
            f.advance()
            assertEquals(0, f.host.finished)
            val callback = f.requests.single().callback

            when (response) {
                "error" -> callback.onError("offline")
                "blocked" -> callback.onSuccess(JSONObject().put("status", "need_bind_phone"))
                else -> callback.onSuccess(JSONObject().put("result", JSONObject()))
            }

            assertEquals("Completion was omitted for $response", 1, f.host.finished)
            f.advance()
            assertEquals(1, f.host.finished)
            assertTrue(f.host.toasts.isNotEmpty() || f.host.messages.isNotEmpty())
        }
    }

    @Test fun offlineLoadFinishesAfterEntryWithoutDispatchingAnyRequest() {
        Fixture().use { f ->
            f.setConnected(false)

            f.loader.load(f.item)

            assertTrue(f.requests.isEmpty())
            assertEquals(0, f.host.finished)
            f.advance()
            assertEquals(1, f.host.finished)
            assertTrue(f.host.documents.isNotEmpty())
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun cancelledCallbacksCannotFinishOrRenderTheAbandonedRequest() {
        Fixture().use { f ->
            f.loader.load(f.item)
            val old = f.requests.single().callback
            f.loader.cancel()

            old.onSuccess(body("stale"))
            old.onError("stale failure")
            f.advance()

            assertEquals(0, f.host.finished)
            assertTrue(f.host.documents.isEmpty())
            assertTrue(f.host.messages.isEmpty())
            assertTrue(f.host.toasts.isEmpty())
        }
    }

    @Test fun cancellationDropsTheCompletedResponseThatWasNotYetRendered() {
        Fixture().use { f ->
            f.loader.load(f.item)
            f.requests.single().callback.onSuccess(body("pending"))
            assertEquals(1, f.host.finished)
            assertTrue(f.loader.hasPendingRender())

            f.loader.cancel()
            f.advance()

            assertFalse(f.loader.hasPendingRender())
            assertTrue(f.host.documents.isEmpty())
        }
    }

    @Test fun oldCompletionCannotFinishANewerLoadOfTheSameItem() {
        Fixture().use { f ->
            f.loader.load(f.item)
            val old = f.requests.single().callback
            f.loader.load(f.item)
            val current = f.requests.last().callback

            old.onSuccess(body("old"))
            old.onError("old failure")
            assertEquals(0, f.host.finished)
            current.onSuccess(body("current"))
            f.advance()

            assertEquals(1, f.host.finished)
            assertEquals("current", f.host.documents.single().getJSONObject("result")
                .getJSONObject("link").getString("text"))
        }
    }

    @Test fun inactiveHostAndClosedLoaderRejectLateSuccessAndFailureCompletions() {
        for (close in listOf(true, false)) Fixture().use { f ->
            f.loader.load(f.item)
            val callback = f.requests.single().callback
            if (close) f.loader.close() else f.host.active = false

            callback.onSuccess(body("late"))
            callback.onError("late failure")
            f.advance()

            assertEquals(0, f.host.finished)
            assertTrue(f.host.documents.isEmpty())
            assertTrue(f.host.toasts.isEmpty())
            assertTrue(f.host.messages.isEmpty())
        }
    }

    @Test fun cancelledArticleEnrichmentCannotFinishANewerDetailRequest() {
        Fixture(article = true).use { f ->
            f.loader.load(f.item)
            f.requests.single().callback.onSuccess(body("article"))
            val web = f.requests.last().callback
            f.loader.load(f.item)

            web.onSuccess(JSONObject().put("result", JSONObject()))
            web.onError("stale enrichment")

            assertEquals(0, f.host.finished)
            assertEquals(3, f.requests.size)
        }
    }

    private class Fixture(article: Boolean = false) : Closeable {
        private val context = ApplicationProvider.getApplicationContext<Application>()
        private val api = ApiClient(SessionStore(context))
        val requests = Shadow.extract<DetailLoadCompletionApiShadow>(api).requests
        val item: FeedItem = FeedItem.from(JSONObject().put("linkid", "completion-post")
            .put("is_article", if (article) 1 else 0))
        val host = RecordingHost()
        val loader = DetailLoadCoordinator(context, api, LocalCache(context),
            Handler(Looper.getMainLooper()), host)

        init {
            setConnected(true)
            host.onRender = loader::markRendered
        }

        fun setConnected(connected: Boolean) {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            shadowOf(manager).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                if (connected) NetworkInfo.DetailedState.CONNECTED else NetworkInfo.DetailedState.DISCONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, connected))
        }

        fun advance() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(24))

        override fun close() {
            loader.close()
            api.close()
        }
    }

    private class RecordingHost : DetailLoadCoordinator.Host {
        var active = true
        var finished = 0
        var onRender: () -> Unit = {}
        val documents = mutableListOf<JSONObject>()
        val messages = mutableListOf<String>()
        val toasts = mutableListOf<String>()

        override fun active(item: FeedItem) = active
        override fun hideLoading() = Unit
        override fun renderDetail(body: JSONObject, fallback: FeedItem) {
            documents += JSONObject(body.toString())
            onRender()
        }
        override fun refreshOffline(item: FeedItem, body: JSONObject) = Unit
        override fun showMessage(message: String) { messages += message }
        override fun toast(message: String) { toasts += message }
        override fun requestFinished() { finished++ }
    }

    private fun body(text: String) = JSONObject().put("result", JSONObject().put("link",
        JSONObject().put("linkid", "completion-post").put("text", text)))
}

@Implements(value = ApiClient::class, isInAndroidSdk = false)
internal class DetailLoadCompletionApiShadow {
    data class Request(val path: String, val params: Map<String, String>, val callback: ApiClient.Callback)
    val requests = mutableListOf<Request>()

    @Implementation fun get(path: String, extra: Map<String, String>, callback: ApiClient.Callback) {
        requests += Request(path, extra.toMap(), callback)
    }
}
