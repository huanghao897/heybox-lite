package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class ComposeFeedScrollUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var api: ApiClient? = null

    @Before fun prepareRenderer() { EmojiRenderer.clear() }
    @After fun cleanUp() {
        api?.close()
        EmojiRenderer.clear()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun realFeedRoundTripsReuseViewsAndParsedContentWithoutMovingTheAnchor() {
        val preloads = (0 until 32).map { preload(it) }
        val posts = preloads.mapIndexed { index, preload -> post(index, preload, rich = true) }
        val state = LazyListState()
        val services = services()
        val theme = mutableStateOf(services.theme)
        var requests = 0
        var refreshes = 0
        compose.setContent {
            Box(Modifier.size(240.dp, 320.dp)) {
                ComposeFeedScreen(posts, false, false, true, services.copy(theme = theme.value),
                    {}, { refreshes++ }, { requests++ }, {}, { _, _ -> }, listState = state)
            }
        }
        val seen = mutableMapOf<ComposeRichTextView, MutableSet<String>>()
        fun visit(index: Int) {
            compose.onNode(hasScrollAction()).performScrollToIndex(index + 1)
            compose.runOnIdle {
                val views = richViews(compose.activity.window.decorView)
                assertEquals("Title $index [inline-test]",
                    views.single { it.tag == "Title $index [inline-test]" }.text.toString())
                assertEquals("Summary $index [inline-test]",
                    views.single { it.tag == "Summary $index [inline-test]" }.text.toString())
                for (view in views) {
                    val source = view.tag as? String ?: continue
                    assertEquals(source, view.text.toString())
                    seen.getOrPut(view) { mutableSetOf() }.add(source)
                }
            }
        }
        for (index in 0..25) visit(index)
        val reads = preloads.map { it.reads }
        assertTrue(preloads.take(26).all { it.reads > 0 })
        for (index in 24 downTo 0) visit(index)
        compose.runOnIdle {
            assertEquals("Scrolling back must not rescan game preloads", reads.take(26),
                preloads.take(26).map { it.reads })
            assertTrue("The Feed must reuse its actual rich-text Views", seen.values.any { it.size > 2 })
            assertEquals(0, requests)
            assertEquals(0, refreshes)
        }
        visit(12)
        val anchor = compose.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        compose.runOnIdle {
            posts[12].likes = 42
            posts[12].liked = true
            theme.value = theme.value.copy(panel = Color(0xFF242628))
        }
        compose.runOnIdle {
            assertEquals(anchor, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
            assertEquals(reads[12], preloads[12].reads)
            val root = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("The Feed must draw text after repeated reuse", pixels.any {
                it == android.graphics.Color.rgb(245, 245, 247)
            })
            bitmap.recycle()
        }
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.runOnIdle {
            assertTrue(state.firstVisibleItemIndex >= anchor.first)
            assertEquals(0, refreshes)
            assertEquals(0, requests)
        }
    }

    @Test fun realFeedRequestsOnePageWhileBusyAndAppendKeepsTheScrollAnchor() {
        val posts = mutableStateOf((0 until 30).map { post(it, preload(it)) })
        val loading = mutableStateOf(false)
        val state = LazyListState()
        val services = services()
        var requests = 0
        compose.setContent {
            Box(Modifier.size(240.dp, 320.dp)) {
                ComposeFeedScreen(posts.value, loading.value, false, false, services,
                    {}, {}, { requests++; loading.value = true }, {}, { _, _ -> }, listState = state)
            }
        }
        for (index in 20..28) compose.onNode(hasScrollAction()).performScrollToIndex(index)
        compose.runOnIdle { assertEquals(1, requests) }
        val anchor = compose.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        compose.runOnIdle {
            posts.value = posts.value + (30 until 60).map { post(it, preload(it)) }
            loading.value = false
        }
        compose.runOnIdle {
            assertEquals(anchor, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
            assertEquals(1, requests)
        }
        for (index in 50..58) compose.onNode(hasScrollAction()).performScrollToIndex(index)
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun loadingCompletionAtTheSamePositionRequestsOnceAndDeclinedLoadsDoNotLoop() {
        val posts = (0 until 30).map { post(it, preload(it)) }
        val loading = mutableStateOf(true)
        val state = LazyListState()
        val services = services()
        var requests = 0
        compose.setContent {
            Box(Modifier.size(240.dp, 320.dp)) {
                ComposeFeedScreen(posts, loading.value, false, false, services,
                    {}, {}, { requests++ }, {}, { _, _ -> }, listState = state)
            }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(28)
        compose.runOnIdle { assertEquals(0, requests) }
        val anchor = compose.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        compose.runOnIdle { loading.value = false }
        compose.runOnIdle {
            assertEquals("Finishing loading must recheck a stationary viewport", 1, requests)
            assertEquals(anchor, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
        }
        repeat(3) {
            compose.onNode(hasScrollAction()).performScrollToIndex(27)
            compose.onNode(hasScrollAction()).performScrollToIndex(29)
            compose.runOnIdle { assertEquals(1, requests) }
        }
        compose.runOnIdle { loading.value = true }
        compose.runOnIdle { loading.value = false }
        compose.runOnIdle { assertEquals("A failed request must not auto-retry in a loop", 1, requests) }
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNode(hasScrollAction()).performScrollToIndex(28)
        compose.runOnIdle { assertEquals("Leaving and revisiting the end permits a retry", 2, requests) }
    }

    @Test fun actionInvalidationUpdatesTheSamePostWithoutAThemeOrListChange() {
        val post = post(0, preload(0))
        val posts = listOf(post)
        val services = services()
        val revision = mutableStateOf(0)
        compose.setContent {
            Box(Modifier.size(240.dp, 320.dp)) {
                ComposeFeedScreen(posts, false, false, true, services,
                    {}, {}, {}, {}, { _, _ -> }, actionRevision = revision.value)
            }
        }
        compose.onNodeWithContentDescription("点赞").assertExists()
        compose.onNodeWithContentDescription("关注").assertExists()
        compose.runOnIdle {
            post.likes = 42
            post.liked = true
            post.following = true
            revision.value++
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("取消点赞").assertExists()
        compose.onNodeWithContentDescription("取消关注").assertExists()
        compose.onNodeWithText("42").assertExists()
        compose.runOnIdle {
            post.likes = 41
            post.liked = false
            post.following = false
            revision.value++
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("点赞").assertExists()
        compose.onNodeWithContentDescription("关注").assertExists()
        compose.onNodeWithText("41").assertExists()
        compose.onNodeWithText("42").assertDoesNotExist()
    }

    @Test fun precomputedFeedCardsDoNotParsePreloadsOnFirstDisplayOrLaterScrolls() {
        val preloads = (0 until 24).map { preload(it) }
        val posts = preloads.mapIndexed { index, preload -> post(index, preload) }
        val presentations = posts.associateWith(::composeFeedPresentation)
        val preparedReads = preloads.map { it.reads }
        val services = services()
        val listState = LazyListState()
        compose.setContent {
            Box(Modifier.size(240.dp, 320.dp)) {
                ComposeFeedScreen(posts, false, false, true, services,
                    {}, {}, {}, {}, { _, _ -> }, listState = listState,
                    presentations = presentations)
            }
        }
        for (index in posts.indices) {
            compose.onNode(hasScrollAction()).performScrollToIndex(index + 1)
        }
        for (index in posts.indices.reversed()) {
            compose.onNode(hasScrollAction()).performScrollToIndex(index + 1)
        }
        compose.runOnIdle {
            assertEquals("Display must use the worker's prepared content even on first bind",
                preparedReads, preloads.map { it.reads })
            assertTrue("Plain feed text must not allocate native rich-text Views",
                richViews(compose.activity.window.decorView).isEmpty())
        }
        compose.onNodeWithText("Title 0").assertExists()
    }

    private fun services(): ComposeServices {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val session = SessionStore(context).apply { setNoImage(true) }
        val client = ApiClient(session)
        api = client
        return ComposeServices(compose.activity, session, client, GameDetailClient(client),
            LocalCache(context), Handler(Looper.getMainLooper()), ReadingTimeTracker(context), null,
            composePreviewTheme(false).copy(motionLevel = MotionLevel.OFF), ComposeToast {})
    }

    private fun post(index: Int, preload: JSONObject, rich: Boolean = false) = FeedItem.from(
        JSONObject().put("linkid", "post-$index").put("title", "<p>Title $index${if (rich) " [inline-test]" else ""}</p>")
            .put("description", "[{\"type\":\"text\",\"text\":\"Summary $index${if (rich) " [inline-test]" else ""}\"}]")
            .put("user", JSONObject().put("username", "Author").put("userid", "author"))
            .put("communityPostPreload", preload),
    )

    private fun preload(index: Int) = CountingPreload().apply {
        put("game", JSONObject().put("appid", "$index").put("name", "Game $index"))
    }

    private fun richViews(root: View): List<ComposeRichTextView> = when (root) {
        is ComposeRichTextView -> listOf(root)
        is ViewGroup -> (0 until root.childCount).flatMap { richViews(root.getChildAt(it)) }
        else -> emptyList()
    }

    private class CountingPreload : JSONObject() {
        var reads = 0
        override fun opt(key: String): Any? {
            reads++
            return super.opt(key)
        }
    }
}
