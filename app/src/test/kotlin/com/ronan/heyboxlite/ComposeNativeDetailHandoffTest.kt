package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ronan.heyboxlite.ComposeCommentActionsTestFixtures.RecordingCallbacks
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    qualifiers = "w240dp-h320dp-mdpi", shadows = [DetailLoadCompletionApiShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeNativeDetailHandoffTest {
    @Test fun oldHttpCannotReplaceANewComposeDocumentWithTheSameId() = verifyHttp("native-post")

    @Test fun oldHttpCannotReplaceANewComposeDocumentWithADifferentId() = verifyHttp("compose-post")

    @Test fun oldWebEnrichmentCannotReplaceANewComposeDocumentWithTheSameId() = verifyWeb("native-post")

    @Test fun oldWebEnrichmentCannotReplaceANewComposeDocumentWithADifferentId() = verifyWeb("compose-post")

    @Test fun queuedNativeRenderCannotReplaceANewComposeDocumentWithTheSameId() = verifyQueuedRender("native-post")

    @Test fun queuedNativeRenderCannotReplaceANewComposeDocumentWithADifferentId() = verifyQueuedRender("compose-post")

    private fun verifyHttp(composeId: String) {
        Fixture().use { f ->
            val old = f.startNative()
            f.handoff(composeId)
            f.publishCompose()

            f.rejectLateCallbacks {
                old.callback.onSuccess(body(f.nativeItem, "stale-http"))
                old.callback.onError("stale HTTP failure")
            }
        }
    }

    private fun verifyWeb(composeId: String) {
        Fixture().use { f ->
            val old = f.startNative()
            old.callback.onSuccess(body(f.nativeItem, "native-http"))
            val web = f.requests.last()
            assertEquals(EndpointProvider.linkWebView(), web.path)
            f.drain()
            assertEquals("native-http", link(f.activity.nativeDetail.body).getString("text"))

            val enrichment = JSONObject().put("result", JSONObject().put("game", JSONArray()
                .put(JSONObject().put("steam_appid", "70").put("name", "Late game"))))
            val enriched = body(f.nativeItem, "native-http")
            assertEquals(0, ArticleGameCards.count(link(enriched)))
            DetailResponseNormalizer.mergeArticleWebContent(enriched, enrichment)
            assertEquals("The stale response must be capable of scheduling a progressive render",
                1, ArticleGameCards.count(link(enriched)))

            f.handoff(composeId)
            f.publishCompose()

            f.rejectLateCallbacks {
                web.callback.onSuccess(enrichment)
                web.callback.onError("stale enrichment failure")
            }
        }
    }

    private fun verifyQueuedRender(composeId: String) {
        Fixture().use { f ->
            val old = f.startNative()
            old.callback.onSuccess(body(f.nativeItem, "queued-native-http"))
            assertTrue(f.activity.nativeDetail.loader.hasPendingRender())

            f.handoff(composeId)

            assertFalse("Runtime activation must discard the native publication queue",
                f.activity.nativeDetail.loader.hasPendingRender())
            f.publishCompose()
            f.rejectLateCallbacks { }
        }
    }

    private class Fixture : Closeable {
        private val previousMotion = Motions.level()
        private val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        // Attach a context, but never run MainActivity's account/startup onCreate.
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        private val session = SessionStore(activity)
        private val api = ApiClient(session)
        private val cache = LocalCache(activity)
        private val cacheWriter = LocalCache::class.java.getDeclaredField("CACHE_EXECUTOR").apply {
            isAccessible = true
        }.get(null) as ExecutorService
        private val tokens = ThemeTokens.of(false, Color.BLACK, Color.GRAY)
        val reading = ReadingTimeTracker(activity)
        val requests = Shadow.extract<DetailLoadCompletionApiShadow>(api).requests
        val nativeItem = item("native-post", article = true, text = "native-preview")
        val host: ComposeAppHost
        private val runtime: ComposeDetailRuntime
        private val parser: ExecutorService
        private lateinit var composeItem: FeedItem
        private var nativeChildren = emptyList<View>()
        private var articleMsAtHandoff = 0L
        private var countAtHandoff = 0

        init {
            Motions.setLevel(MotionLevel.OFF)
            activity.session = session
            activity.api = api
            activity.localCache = cache
            activity.themeTokens = tokens
            activity.readingTimeTracker = reading
            activity.activityResumed = true
            activity.content = FrameLayout(activity)
            activity.shellRoot = LinearLayout(activity)
            activity.shellBar = LinearLayout(activity)
            activity.leading = TextView(activity)
            activity.title = TextView(activity)
            activity.action = TextView(activity)
            MainActivity::class.java.getDeclaredField("legacyUi").apply {
                isAccessible = true
                set(activity, MainActivityLegacyUi(activity))
            }
            activity.postActions = PostActionController(activity, session, null, cache,
                tokens, false, object : PostActionController.Host {
                    override fun currentLinkId() = activity.nativeDetail.linkId
                    override fun currentLinkHsrc() = activity.nativeDetail.hsrc
                    override fun currentDetailItem(): FeedItem? = activity.nativeDetail.item
                    override fun feedItems() = emptyList<FeedItem>()
                    override fun searchItems() = emptyList<FeedItem>()
                    override fun feedChanged() = Unit
                    override fun showToast(message: String) = fail(message)
                })
            val renderer = DetailContentRenderer(activity, session, tokens, false, api) { _, _, _ -> }
            val features = activity.nativeDetail.initialize(renderer)
            activity.commentController = features.commentController
            activity.detailActionBar = features.actionBar
            activity.composeLayer = FrameLayout(activity)
            // Mount the real host as in the lifecycle fixture, without starting screen effects.
            host = ComposeAppHost(owner.get(), activity.composeLayer, session, api,
                cache, activity.handler, reading, null, tokens, false, 1f, 1f,
                RecordingCallbacks(), ComposeToast { fail(it) })
            activity.composeAppHost = host
            activity.composeBridge = ComposeActivityBridge(activity)
            assertTrue(host.isMounted())
            assertEquals(1, activity.composeLayer.childCount)
            runtime = ComposeDetailRuntime(activity)
            parser = ComposeDetailRuntime::class.java.getDeclaredField("parser").apply {
                isAccessible = true
            }.get(runtime) as ExecutorService
            val manager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            shadowOf(manager).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, true))
        }

        fun startNative(): DetailLoadCompletionApiShadow.Request {
            activity.composeLayer.visibility = View.INVISIBLE
            host.setSurfaceActive(false)
            activity.screen = "detail"
            activity.nativeDetail.load(nativeItem)
            val request = requests.single()
            assertEquals(EndpointProvider.linkTree(), request.path)
            drain()
            assertTrue("The fixture must render an actual native preview before handoff",
                activity.nativeDetail.loader.rendered())
            assertNotNull(activity.nativeDetail.pager)
            assertNotNull(activity.nativeDetail.body)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            assertTrue(reading.stats().totalArticleMs >= 1000L)
            return request
        }

        fun handoff(id: String) {
            nativeChildren = children()
            composeItem = item(id, article = false, text = "compose-preview")
            activity.composeLayer.visibility = View.VISIBLE
            activity.content.visibility = View.INVISIBLE
            host.setSurfaceActive(true)

            runtime.open(composeItem)

            assertTrue(host.isMounted())
            assertTrue(host.isRoute("detail"))
            assertEquals(id, host.currentDetailItem()!!.id)
            assertTrue(host.navigation.detail.value!!.loading)
            assertNativeReleased()
            val stats = reading.stats()
            articleMsAtHandoff = stats.totalArticleMs
            countAtHandoff = stats.totalCount
            assertEquals(0L, stats.totalPostMs)
            // While Compose is still loading, its open must have paused the native timer.
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            assertEquals(articleMsAtHandoff, reading.stats().totalArticleMs)
            assertEquals(countAtHandoff, reading.stats().totalCount)
        }

        fun publishCompose() {
            val current = requests.last()
            assertEquals(EndpointProvider.linkTree(), current.path)
            val response = body(composeItem, "compose-current")
            assertEquals("The HTTP fixture must parse as the new body, not its list preview",
                "compose-current", ComposeDetailDocument.parse(response, composeItem, false).content.single().value)
            current.callback.onSuccess(response)
            drain()
            val detail = host.navigation.detail.value!!
            assertEquals(composeItem.id, detail.item.id)
            assertFalse(detail.item.article)
            assertEquals("compose-current", detail.content.single().value)
            assertEquals("compose-current", link(detail.body).getString("text"))
            assertFalse(detail.loading)
            assertFalse(detail.refreshPending)
            assertEquals("compose-current-auth", host.currentDetailContext()!!.authCode)
            assertEquals(articleMsAtHandoff, reading.stats().totalArticleMs)
            assertEquals(countAtHandoff + if (composeItem.id == nativeItem.id) 0 else 1,
                reading.stats().totalCount)
            assertNativeReleased()
        }

        fun rejectLateCallbacks(deliver: () -> Unit) {
            val detail = host.navigation.detail.value!!
            val nativeCache = cache.detail(nativeItem.id)?.toString()
            val composeCache = cache.detail(composeItem.id)!!.toString()
            val requestCount = requests.size
            val postMs = reading.stats().totalPostMs
            val started = SystemClock.elapsedRealtime()

            deliver()
            drain()
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
            reading.pause()

            assertSame("A stale native response must not republish the Compose entry",
                detail, host.navigation.detail.value)
            assertEquals(nativeCache, cache.detail(nativeItem.id)?.toString())
            assertEquals(composeCache, cache.detail(composeItem.id)!!.toString())
            assertEquals("A stale HTTP response must not start another enrichment request",
                requestCount, requests.size)
            assertEquals(articleMsAtHandoff, reading.stats().totalArticleMs)
            assertEquals(postMs + SystemClock.elapsedRealtime() - started, reading.stats().totalPostMs)
            assertEquals(if (composeItem.id == nativeItem.id) 1 else 2, reading.stats().totalCount)
            assertNativeReleased()
        }

        fun drain() {
            val main = shadowOf(Looper.getMainLooper())
            main.idleFor(Duration.ofMillis(24))
            parser.submit { }.get(5, TimeUnit.SECONDS)
            main.idle()
            cacheWriter.submit { }.get(5, TimeUnit.SECONDS)
        }

        private fun assertNativeReleased() {
            val page = activity.nativeDetail
            assertNull(page.item)
            assertNull(page.body)
            assertNull(page.pager)
            assertNull(page.articleScroll)
            assertNull(page.commentScroll)
            assertEquals("", page.linkId)
            assertEquals("", page.authCode)
            assertFalse(page.loader.rendered())
            assertFalse(page.loader.hasPendingRender())
            assertEquals(nativeChildren, children())
            assertEquals(View.INVISIBLE, activity.content.visibility)
            assertEquals(View.VISIBLE, activity.composeLayer.visibility)
        }

        private fun children() = (0 until activity.content.childCount).map(activity.content::getChildAt)

        override fun close() {
            runtime.close()
            parser.awaitTermination(5, TimeUnit.SECONDS)
            cacheWriter.submit { }.get(5, TimeUnit.SECONDS)
            activity.nativeDetail.close()
            reading.pause()
            activity.pageTransitions.finishNow()
            activity.handler.removeCallbacksAndMessages(null)
            activity.detailNavigation.reset()
            activity.screenSnapshots.releaseAll()
            activity.fullScreenSnapshots.releaseAll()
            host.close()
            api.close()
            owner.pause().stop().destroy()
            Motions.setLevel(previousMotion)
        }
    }

    companion object {
        private fun item(id: String, article: Boolean, text: String): FeedItem =
            FeedItem.from(JSONObject().put("linkid", id).put("is_article", if (article) 1 else 0)
                .put("title", text).put("text", text))

        private fun body(item: FeedItem, text: String) = JSONObject().put("result", JSONObject()
            .put("link", item.toJson().put("description", text).put("text", text)
                .put("auth_code", "$text-auth"))
            .put("comments", JSONArray()))

        private fun link(body: JSONObject?) = requireNotNull(body).getJSONObject("result").getJSONObject("link")
    }
}
