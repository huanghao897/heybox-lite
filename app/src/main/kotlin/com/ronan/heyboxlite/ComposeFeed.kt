package com.ronan.heyboxlite

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Actions are routed to the existing Java controllers by the host. */
internal enum class FeedAction {
    LIKE,
    FAVORITE,
    CACHE,
    FOLLOW,
    COMMENT;

    companion object {
        val like: FeedAction = LIKE
        val favorite: FeedAction = FAVORITE
        val cache: FeedAction = CACHE
        val follow: FeedAction = FOLLOW
        val comment: FeedAction = COMMENT
    }
}

/** Small callback bridge around the Java ImageLoader; it owns all network/cache work. */
internal interface ComposeImageLoader {
    fun load(sourceUrl: String, targetPx: Int, callback: (Bitmap?) -> Unit)
}

internal object ExistingComposeImageLoader : ComposeImageLoader {
    override fun load(sourceUrl: String, targetPx: Int, callback: (Bitmap?) -> Unit) {
        ImageLoader.load(sourceUrl, targetPx, object : ImageLoader.Callback {
            override fun onLoaded(bitmap: Bitmap?) {
                callback(bitmap)
            }
        })
    }
}

@Composable
internal fun ComposeFeedScreen(
    items: List<FeedItem>,
    loading: Boolean,
    refreshing: Boolean,
    noMore: Boolean,
    services: ComposeServices,
    onOpen: (FeedItem) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSearch: () -> Unit,
    onAction: (FeedItem, FeedAction) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    observeLoadMore: Boolean = true,
    interactive: Boolean = true,
) {
    HeyboxComposeTheme(services.theme) {
        val rotary = services.theme.rotaryRequest
        val pullOffset = remember { mutableStateOf(0f) }
        val density = LocalDensity.current
        val refreshThreshold = with(density) { 56.dp.toPx() }
        val refreshHold = with(density) { 30.dp.toPx() }
        LaunchedEffect(rotary?.serial) {
            if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
        }
        LaunchedEffect(refreshing) {
            if (refreshing) {
                pullOffset.value = refreshHold
            } else if (pullOffset.value > 0f) {
                pullOffset.value = 0f
            }
        }
        BoxWithConstraints(
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
                .background(services.theme.background),
        ) {
            val horizontal = feedHorizontalPadding(maxWidth, services.theme)
            if (observeLoadMore) {
                ObserveFeedLoadMore(listState, items.size, loading, noMore, onLoadMore)
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = horizontal),
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = interactive,
                    modifier = Modifier.fillMaxSize()
                        .watchPullToRefresh(
                            listState = listState,
                            enabled = interactive && !loading && !refreshing,
                            pullOffset = pullOffset,
                            thresholdPx = refreshThreshold,
                            holdPx = refreshHold,
                            onRefresh = onRefresh,
                        ),
                    contentPadding = PaddingValues(
                        top = (8 * services.theme.uiScale).dp,
                        bottom = (14 * services.theme.uiScale).dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        (7 * services.theme.uiScale).dp,
                    ),
                ) {
                    item(key = "feed-search") {
                        FeedToolbar(
                            theme = services.theme,
                            onSearch = onSearch,
                        )
                    }
                    if (loading && items.isEmpty()) {
                        item(key = "feed-loading") {
                            FeedLoading(services.theme)
                        }
                    } else if (items.isEmpty()) {
                        item(key = "feed-empty") {
                            FeedEmpty("暂无内容", "刷新", services.theme, onRefresh)
                        }
                    } else {
                        itemsIndexed(
                            items = items,
                            key = { index, item -> item.id.ifEmpty { "feed-$index" } },
                        ) { _, item ->
                            ComposeFeedCard(
                                item = item,
                                theme = services.theme,
                                noImage = services.session.noImage(),
                                currentUserId = services.session.userId(),
                                onOpen = onOpen,
                                onAction = onAction,
                                showSecondaryActions = false,
                                gameCardNoImage = services.session.gameCardNoImage(),
                            )
                        }
                        item { FeedFooter(loading, noMore, services.theme, onLoadMore) }
                    }
                }
                if (refreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                            .height((2 * services.theme.uiScale).dp),
                        color = services.theme.accent,
                        trackColor = Color.Transparent,
                    )
                }
            }
        }
    }
}

internal fun composePreviewTheme(roundScreen: Boolean): ComposeThemeState = ComposeThemeState(
    dark = true,
    background = Color(0xFF0B0B0C),
    panel = Color(0xFF19191B),
    panelElevated = Color(0xFF202023),
    text = Color(0xFFF5F5F7),
    muted = Color(0xFFA5A5AA),
    subtle = Color(0xFF747479),
    hairline = Color(0xFF2B2B2E),
    accent = Color(0xFFC4C6C9),
    link = Color(0xFF59A9EB),
    onAccent = Color.Black,
    uiScale = if (roundScreen) 0.82f else 1f,
    textScale = 1f,
    roundScreen = roundScreen,
)

private fun previewFeedItem(): FeedItem = FeedItem.from(
    org.json.JSONObject()
        .put("linkid", "compose-preview")
        .put("title", "Compose 信息流卡片预览")
        .put("description", "文章、视频和帖子都沿用 FeedItem 的统一字段，动作交回 Java 宿主。")
        .put("comment_num", 36)
        .put("link_award_num", 128)
        .put("is_article", 1)
        .put("topic_name", "社区")
        .put("user", org.json.JSONObject().put("username", "Ronan").put("userid", "preview")),
)

private fun Modifier.watchPullToRefresh(
    listState: LazyListState,
    enabled: Boolean,
    pullOffset: MutableState<Float>,
    thresholdPx: Float,
    holdPx: Float,
    onRefresh: () -> Unit,
): Modifier = graphicsLayer { translationY = pullOffset.value }.pointerInput(enabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var atTop = listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0
        var lastY = down.position.y
        var totalX = 0f
        var totalY = 0f
        var distance = 0f
        var tracking = false
        var decided = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: break
            val currentY = change.position.y
            val delta = currentY - lastY
            totalX = change.position.x - down.position.x
            totalY = currentY - down.position.y
            atTop = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
            if (!decided && maxOf(kotlin.math.abs(totalX), kotlin.math.abs(totalY))
                >= viewConfiguration.touchSlop) {
                decided = true
                if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY) || totalY <= 0f) break
            }
            if (enabled && atTop && decided && (delta > 0f || tracking)) {
                tracking = true
                distance = (distance + delta).coerceAtLeast(0f)
                pullOffset.value = (distance * 0.52f).coerceAtMost(holdPx * 2.4f)
                change.consume()
            }
            lastY = currentY
            if (!change.pressed) break
        }
        if (enabled && tracking && atTop && distance >= thresholdPx) {
            pullOffset.value = holdPx
            onRefresh()
        } else if (tracking) {
            pullOffset.value = 0f
        }
    }
}

@Preview(name = "Feed card square", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
private fun ComposeFeedCardSquarePreview() {
    val theme = composePreviewTheme(roundScreen = false)
    HeyboxComposeTheme(theme) {
        ComposeFeedCard(previewFeedItem(), theme, true, "", {}, { _, _ -> })
    }
}

@Preview(name = "Feed card round", showBackground = true, widthDp = 192, heightDp = 260)
@Composable
private fun ComposeFeedCardRoundPreview() {
    val theme = composePreviewTheme(roundScreen = true)
    HeyboxComposeTheme(theme) {
        ComposeFeedCard(previewFeedItem(), theme, true, "", {}, { _, _ -> })
    }
}
