package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ComposeDetailPagerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var api: ApiClient? = null
    private lateinit var body: LazyListState
    private lateinit var comments: LazyListState
    private lateinit var pager: PagerState
    private var pageNavigation: ComposeDetailPagerNavigation? = null
    private val rotary = ComposeFeedRotaryInput()
    private var active by mutableStateOf(true)
    private var entry by mutableStateOf(1L)
    private var routeBacks = 0
    private var routeSwipeStarts = 0
    private var retries = 0
    private val actions = mutableListOf<ComposeDetailAction>()

    @After fun close() { api?.close() }

    @Test fun leftSwipeOpensADedicatedCommentsPageAndRightSwipeReturnsToTheBody() {
        showDetail()
        left()
        compose.onNodeWithTag("compose-detail-comments-list").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertEquals(0, body.firstVisibleItemIndex)
            assertFalse(requireNotNull(pageNavigation).canSwipeBack)
        }
        right()
        compose.onNodeWithTag("compose-detail-list").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, pager.settledPage)
            assertTrue(requireNotNull(pageNavigation).canSwipeBack)
            assertEquals(0, routeSwipeStarts)
        }
    }

    @Test fun pageContentMovesUnderTheFingerBeforeRelease() {
        showDetail()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("compose-detail-pager").performTouchInput {
            down(Offset(210f, 160f))
            moveTo(Offset(170f, 160f), 32)
            moveTo(Offset(95f, 160f), 80)
        }
        compose.runOnIdle {
            val article = compose.onNodeWithTag("compose-detail-list").fetchSemanticsNode().positionInRoot.x
            val discussion = compose.onNodeWithTag("compose-detail-comments-list").fetchSemanticsNode().positionInRoot.x
            assertTrue("The body must track the held finger", article < -30f)
            assertTrue("The separate comments page must already be visible", discussion in 1f..239f)
            assertEquals("There must be no gap between the two live pages", 240f, discussion - article, 1f)
            assertEquals(0, routeSwipeStarts)
        }
        compose.onNodeWithTag("compose-detail-pager").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test fun commentsOwnTheRightEdgeSwipeButBodyRetainsTheRouteBackGesture() {
        showDetail()
        left()
        compose.onNodeWithTag("compose-detail-pager").performTouchInput {
            down(Offset(8f, 150f))
            moveTo(Offset(70f, 150f), 64)
            moveTo(Offset(210f, 150f), 160)
            up()
        }
        compose.runOnIdle {
            assertEquals(0, pager.settledPage)
            assertEquals(0, routeSwipeStarts)
            assertEquals(0, routeBacks)
        }
        compose.onNodeWithTag("detail-shell").performTouchInput {
            down(Offset(8f, 150f))
            moveTo(Offset(80f, 150f), 80)
            moveTo(Offset(180f, 150f), 160)
            up()
        }
        compose.runOnIdle {
            assertEquals(1, routeSwipeStarts)
            assertEquals(1, routeBacks)
        }
    }

    @Test fun hardwareBackFromCommentsReturnsToBodyBeforePoppingTheRoute() {
        showDetail()
        left()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, pager.settledPage)
            assertEquals(0, routeBacks)
            compose.activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(1, routeBacks)
        }
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundCommentsHaveNoReturnToolbarAndSystemBackStillReturnsToBody() {
        showDetail()
        left()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, pager.settledPage)
            assertEquals(0, routeBacks)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun minimumUiScaleRemovesTheToolbarSpaceAndKeepsTheTitleInsideTheCircle() {
        showDetail(uiScale = 0.4f)
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        val title = compose.onNode(hasText("Detail 1")).fetchSemanticsNode().boundsInRoot
        assertEquals("Only circular heading space remains above the first line", 192f * 0.12f + 8f * 0.4f,
            title.top, 2f)
        listOf(title.topLeft, title.topRight, title.bottomLeft, title.bottomRight).forEach {
            assertTrue("Title must fit the upper circular chord: $title",
                kotlin.math.hypot(it.x - 96f, it.y - 96f) <= 96f)
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, routeBacks) }
    }

    @Test fun rotaryScrollsOnlyTheVisiblePageAndNeverOverwritesBodyProgress() {
        showDetail()
        compose.runOnIdle { rotary.offer(180) }
        val bodyPosition = compose.runOnIdle { ComposeDetailPosition.from(body) }
        assertTrue(bodyPosition.hasProgress)
        left()
        compose.runOnIdle { rotary.offer(190) }
        compose.runOnIdle {
            assertTrue(ComposeDetailPosition.from(comments).hasProgress)
            assertEquals(bodyPosition, ComposeDetailPosition.from(body))
        }
        val commentPosition = compose.runOnIdle { ComposeDetailPosition.from(comments) }
        compose.runOnIdle { active = false }
        compose.runOnIdle { rotary.offer(140) }
        compose.runOnIdle {
            assertEquals(bodyPosition, ComposeDetailPosition.from(body))
            assertEquals(commentPosition, ComposeDetailPosition.from(comments))
        }
    }

    @Test fun returningFromAnotherDetailRetainsThePageAndBothScrollPositions() {
        showDetail()
        compose.onNodeWithTag("compose-detail-list").performScrollToIndex(5)
        val bodyPosition = compose.runOnIdle { ComposeDetailPosition.from(body) }
        left()
        compose.onNodeWithTag("compose-detail-comments-list").performScrollToIndex(4)
        val commentPosition = compose.runOnIdle { ComposeDetailPosition.from(comments) }
        compose.runOnIdle { entry = 2 }
        compose.runOnIdle { assertEquals(0, pager.settledPage) }
        compose.runOnIdle { entry = 1 }
        compose.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertEquals(bodyPosition, ComposeDetailPosition.from(body))
            assertEquals(commentPosition, ComposeDetailPosition.from(comments))
        }
    }

    @Test fun savedInstanceStateRestoresTheCommentsPageAndItsOwnScrollPosition() {
        val restore = StateRestorationTester(compose)
        showDetail(restore)
        left()
        compose.onNodeWithTag("compose-detail-comments-list").performScrollToIndex(5)
        val position = compose.runOnIdle { ComposeDetailPosition.from(comments) }
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertEquals(position, ComposeDetailPosition.from(comments))
            assertFalse(requireNotNull(pageNavigation).canSwipeBack)
        }
    }

    @Test fun dedicatedCommentsRetainSortLikeAndWriteCommentActions() {
        showDetail()
        left()
        compose.onNode(hasText("最新") and hasAnyAncestor(hasTestTag("compose-detail-comments-list")))
            .performClick()
        compose.runOnIdle {
            assertEquals("comment-root-13", comments.layoutInfo.visibleItemsInfo.first { it.index == 1 }.key)
        }
        compose.onNode(hasContentDescription("点赞评论") and
            hasAnyAncestor(hasTestTag("compose-detail-comments-list")) and
            hasAnyAncestor(hasTestTag("comment-root-13"))).performClick()
        compose.runOnIdle {
            assertEquals("root-13", (actions.single() as ComposeDetailAction.LikeComment)
                .comment.getString("commentid"))
        }
        compose.onNodeWithTag("compose-detail-comments-list").performScrollToIndex(15)
        compose.onNode(hasText("写评论") and hasAnyAncestor(hasTestTag("compose-detail-comments-list")))
            .performClick()
        compose.runOnIdle { assertTrue(actions.last() is ComposeDetailAction.WriteComment) }
    }

    @Test fun dedicatedCommentsKeepTheExistingRetryCallback() {
        showDetail(error = "评论暂时无法加载")
        left()
        compose.onNodeWithTag("compose-detail-comments-list").performScrollToIndex(15)
        compose.onNode(hasText("重试") and hasAnyAncestor(hasTestTag("compose-detail-comments-list")))
            .performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun nestedImagePagerKeepsItsOwnHorizontalGestureIncludingAtItsEnd() {
        lateinit var gallery: PagerState
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                pager = rememberPagerState { 2 }
                gallery = rememberPagerState { 2 }
                ComposeDetailPager(pager, true, true, {}, {},
                    body = { _, _ ->
                        HorizontalPager(gallery, Modifier.fillMaxSize().testTag("gallery")) { page ->
                            Text("Image $page")
                        }
                    }, comments = { _, _ -> Text("Separate comments") })
            }
        }
        compose.onNodeWithTag("gallery").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(1, gallery.settledPage)
            assertEquals(0, pager.settledPage)
        }
        compose.onNodeWithTag("gallery").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(1, gallery.settledPage)
            assertEquals(0, pager.settledPage)
        }
    }

    private fun showDetail(restore: StateRestorationTester? = null, error: String = "", uiScale: Float? = null) {
        val activity = compose.activity
        val session = SessionStore(activity).apply { setNoImage(true); setRememberDetailScroll(false) }
        val client = ApiClient(session).also { api = it }
        val theme = composePreviewTheme(activity.resources.configuration.isScreenRound).let {
            if (uiScale == null) it else it.copy(uiScale = uiScale)
        }
        val services = ComposeServices(activity, session, client, GameDetailClient(client), LocalCache(activity),
            Handler(Looper.getMainLooper()), ReadingTimeTracker(activity), null, theme, ComposeToast {})
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pageNavigation?.returnToBody() != true) routeBacks++
            }
        })
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val savedPages = rememberSaveableStateHolder()
            Box(Modifier.fillMaxSize().testTag("detail-shell").composeBackSwipe(
                route = "detail", enabled = true, edgePx = 52f, thresholdPx = 44f,
                touchSlopPx = 8f, maxDragPx = 240f,
                canStart = { it > 0 && pageNavigation?.canSwipeBack != false },
                onStart = { routeSwipeStarts++; true }, onProgress = {}, onCancel = {},
                onComplete = { routeBacks++ },
            )) {
                savedPages.SaveableStateProvider(entry) {
                    body = rememberLazyListState()
                    comments = rememberLazyListState()
                    pager = rememberPagerState { 2 }
                    val item = remember(entry) { FeedItem.from(JSONObject().put("linkid", "post-$entry")
                        .put("title", "Detail $entry").put("comment_num", 14)) }
                    val blocks = remember { (0..20).map { RichContent.Block.text("Body paragraph $it") } }
                    val groups = remember { (0..13).map(::comment) }
                    ComposeDetailScreen(item, blocks, comments = groups, loading = false, services = services,
                        onBack = { routeBacks++ }, onOpenImage = {}, onOpenVideo = {},
                        onCommentAction = actions::add, listState = body, commentsListState = comments,
                        pagerState = pager,
                        onPageNavigation = { pageNavigation = it },
                        error = error, onRetry = { retries++ },
                        rotaryInput = rotary, active = active, entryKey = entry)
                }
            }
        }
        if (restore == null) compose.setContent(content) else restore.setContent(content)
    }

    private fun left() {
        compose.onNodeWithTag("compose-detail-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
    }

    private fun right() {
        compose.onNodeWithTag("compose-detail-pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
    }

    private fun comment(index: Int) = JSONObject().put("comment", JSONArray().put(
        JSONObject().put("commentid", "root-$index").put("text", "Comment $index")
            .put("create_at", 1_600_000_000L + index).put("comment_award_num", 14 - index)
            .put("user", JSONObject().put("username", "User $index").put("userid", "user-$index"))))
}
