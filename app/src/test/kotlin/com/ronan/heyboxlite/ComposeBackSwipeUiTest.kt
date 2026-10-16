package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class ComposeBackSwipeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var starts = 0
    private var completions = 0
    private val offsets = mutableListOf<Float>()
    private var host: ComposeAppHost? = null
    private var api: ApiClient? = null
    private lateinit var feedState: LazyListState
    private lateinit var scope: CoroutineScope
    private val loading = mutableStateOf(false)
    private val posts = mutableStateOf<List<FeedItem>>(emptyList())
    private var feedBecameInactive = false

    @After fun close() {
        host?.close()
        api?.close()
    }

    @Test fun aChildOwnedSubSlopMoveCannotLaterBecomeANavigationSwipe() {
        var childMoves = 0
        compose.setContent {
            Box(Modifier.backSwipe()) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.any { it.pressed }) {
                                childMoves++
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                })
            }
        }
        compose.onNodeWithTag("swipe").performTouchInput {
            down(Offset(120f, 200f))
            moveBy(Offset(-1f, -1f))
            moveBy(Offset(-18f, -4f))
            moveBy(Offset(0f, -50f))
            up()
        }
        compose.runOnIdle {
            assertTrue(childMoves >= 3)
            assertEquals("A child-owned move must lock out parent navigation until release", 0, starts)
            assertEquals(0, completions)
            assertTrue(offsets.isEmpty())
        }
    }

    @Test fun ordinaryVerticalScrollingKeepsOwnershipAfterLateralDrift() {
        val state = LazyListState()
        compose.setContent {
            Box(Modifier.backSwipe()) {
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    items(20) { Box(Modifier.fillMaxWidth().height(80.dp).clickable {}) }
                }
            }
        }
        compose.onNodeWithTag("swipe").performTouchInput {
            down(Offset(160f, 240f))
            moveBy(Offset(0f, -30f))
            moveBy(Offset(-90f, -5f))
            moveBy(Offset(0f, -60f))
        }
        compose.runOnIdle {
            assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 30)
            assertEquals(0, starts)
        }
        compose.onNodeWithTag("swipe").performTouchInput { up() }
    }

    @Test fun deliberateHorizontalNavigationStillWorksOverClickableCards() {
        compose.setContent {
            Box(Modifier.backSwipe()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(20) { Box(Modifier.fillMaxWidth().height(80.dp).clickable {}) }
                }
            }
        }
        compose.onNodeWithTag("swipe").performTouchInput {
            down(Offset(180f, 200f))
            moveBy(Offset(-20f, 0f))
            moveBy(Offset(-50f, 0f))
            up()
        }
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, completions)
            assertEquals(-70f, offsets.last(), 0f)
        }
    }

    @Test fun aLargeFirstMoveCannotDisableAFeedThatAlreadyOwnsTheTouch() {
        showFeed()
        lateinit var priorScroll: Job
        compose.runOnIdle {
            // An active scroll makes LazyColumn catch the next down without waiting for slop.
            priorScroll = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                feedState.scroll { awaitCancellation() }
            }
            assertTrue(feedState.isScrollInProgress)
        }
        try {
            compose.onNode(hasScrollAction()).performTouchInput { down(Offset(160f, 220f)) }
            compose.runOnIdle { assertTrue(feedState.isScrollInProgress) }
            val before = anchor()
            compose.onNode(hasScrollAction()).performTouchInput { moveBy(Offset(-24f, -3f)) }
            compose.runOnIdle { assertFalse("A scrolling feed must remain interactive", feedBecameInactive) }
            compose.onNode(hasScrollAction()).performTouchInput { moveBy(Offset(0f, -55f)) }
            assertMovedForward(before)
            compose.onNode(hasScrollAction()).performTouchInput { up() }
        } finally {
            priorScroll.cancel()
        }
    }

    @Test fun aHeldFeedDragContinuesThroughPaginationUpdatesWithoutResettingItsPosition() {
        showFeed()
        compose.onNode(hasScrollAction()).performTouchInput {
            down(Offset(140f, 240f))
            moveBy(Offset(0f, -30f))
        }
        val before = anchor()
        compose.runOnIdle {
            loading.value = true
            posts.value = posts.value + (30 until 34).map(::post)
        }
        assertEquals(before, anchor())
        compose.onNode(hasScrollAction()).performTouchInput { moveBy(Offset(-2f, -35f)) }
        assertMovedForward(before)
        val next = anchor()
        compose.runOnIdle { loading.value = false }
        compose.onNode(hasScrollAction()).performTouchInput { moveBy(Offset(2f, -35f)) }
        assertMovedForward(next)
        compose.runOnIdle { assertFalse(feedBecameInactive) }
        compose.onNode(hasScrollAction()).performTouchInput { up() }
    }

    private fun Modifier.backSwipe() = fillMaxSize().testTag("swipe").composeBackSwipe(
        route = "feed", enabled = true, edgePx = 0f, thresholdPx = 44f,
        touchSlopPx = 8f, maxDragPx = 240f, canStart = { true },
        onStart = { starts++; true }, onProgress = { offsets.add(it) },
        onCancel = {}, onComplete = { completions++ },
    )

    private fun showFeed() {
        val activity = compose.activity
        val session = SessionStore(activity).apply { setNoImage(true) }
        val client = ApiClient(session).also { api = it }
        val cache = LocalCache(activity)
        val handler = Handler(Looper.getMainLooper())
        val reading = ReadingTimeTracker(activity)
        val services = ComposeServices(activity, session, client, GameDetailClient(client), cache,
            handler, reading, null, composePreviewTheme(false).copy(motionLevel = MotionLevel.OFF), ComposeToast {})
        // Unattached host: exercise the real swipe container without starting route/API effects.
        val owner = ComposeAppHost(activity, FrameLayout(activity), session, client, cache, handler,
            reading, null, ThemeTokens.of(false, Color.BLACK, Color.GRAY), false, 1f, 1f,
            NoOpCallbacks, ComposeToast {}).also { host = it }
        feedState = owner.feedListState
        posts.value = (0 until 30).map(::post)
        compose.setContent {
            scope = rememberCoroutineScope()
            Box(Modifier.size(240.dp, 320.dp)) {
                ComposeSwipeContainer(owner, "feed", services) { page, active ->
                    if (page == "feed") {
                        SideEffect { if (!active) feedBecameInactive = true }
                        ComposeFeedScreen(posts.value, loading.value, false, true, services,
                            {}, {}, {}, {}, { _, _ -> }, listState = feedState,
                            observeLoadMore = false, interactive = active)
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(4)
    }

    private fun anchor(): Pair<Int, Int> = compose.runOnIdle {
        feedState.firstVisibleItemIndex to feedState.firstVisibleItemScrollOffset
    }

    private fun assertMovedForward(before: Pair<Int, Int>) {
        val after = anchor()
        assertTrue("The same finger must keep moving the feed: $before -> $after",
            after.first > before.first || after.first == before.first && after.second > before.second)
    }

    private fun post(index: Int) = FeedItem.from(JSONObject().put("linkid", "gesture-$index")
        .put("title", "Post $index").put("description", "Body $index"))

    private object NoOpCallbacks : ComposeAppCallbacks {
        override fun navigate(route: String) {}
        override fun back() {}
        override fun backTo(route: String) {}
        override fun openDetail(item: FeedItem) {}
        override fun requestImage(url: String) {}
        override fun requestVideo(video: VideoData) {}
        override fun feedAction(item: FeedItem, action: Int) {}
        override fun likeComment(comment: JSONObject) {}
        override fun replyComment(comment: JSONObject) {}
        override fun loadReplies(comment: JSONObject) {}
        override fun writeComment(item: FeedItem) {}
        override fun loginCompleted() {}
        override fun settingsChanged() {}
        override fun runSettingsAction(action: String) {}
    }
}
