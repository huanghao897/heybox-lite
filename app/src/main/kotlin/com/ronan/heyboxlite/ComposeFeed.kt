package com.ronan.heyboxlite

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
) {
    HeyboxComposeTheme(services.theme) {
        val rotary = services.theme.rotaryRequest
        LaunchedEffect(rotary?.serial) {
            if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
        }
        BoxWithConstraints(
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
                .background(services.theme.background),
        ) {
            val horizontal = feedHorizontalPadding(maxWidth, services.theme)
            ObserveFeedLoadMore(listState, items.size, loading, noMore, onLoadMore)
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = horizontal)) {
                FeedToolbar(
                    title = "信息流",
                    theme = services.theme,
                    loading = loading,
                    refreshing = refreshing,
                    onBack = null,
                    onRefresh = onRefresh,
                    onSearch = onSearch,
                )
                if (refreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                            .height((2 * services.theme.uiScale).dp),
                        color = services.theme.accent,
                        trackColor = services.theme.panelElevated,
                    )
                }
                when {
                    loading && items.isEmpty() -> FeedLoading(services.theme)
                    items.isEmpty() -> FeedEmpty("暂无内容", "刷新", services.theme, onRefresh)
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .watchPullToRefresh(
                                listState = listState,
                                enabled = !loading && !refreshing,
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
                                favorite = item.favorited,
                                cached = services.cache.isWatchLater(item.id),
                                gameCardNoImage = services.session.gameCardNoImage(),
                            )
                        }
                        item { FeedFooter(loading, noMore, services.theme, onLoadMore) }
                    }
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
    onRefresh: () -> Unit,
): Modifier = pointerInput(enabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val atTop = listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0
        var lastY = down.position.y
        var distance = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            val currentY = change.position.y
            if (atTop && currentY > lastY) distance += currentY - lastY
            lastY = currentY
            if (!change.pressed) break
        }
        if (enabled && atTop && distance >= 56f) onRefresh()
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
