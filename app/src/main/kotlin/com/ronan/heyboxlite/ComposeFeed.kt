package com.ronan.heyboxlite

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    presentations: Map<FeedItem, ComposeFeedPresentation> = emptyMap(),
    actionRevision: Int = 0,
) {
    val presentationCache = remember { ComposeFeedPresentationCache() }
    HeyboxComposeTheme(services.theme) {
        val theme = LocalHeyboxTheme.current
        val noImage = services.session.noImage()
        val currentUserId = services.session.userId()
        val gameCardNoImage = services.session.gameCardNoImage()
        val rotary = services.theme.rotaryRequest
        LaunchedEffect(rotary?.serial) {
            if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
        }
        BoxWithConstraints(
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
                .background(theme.background),
        ) {
            val horizontal = feedHorizontalPadding(maxWidth, theme)
            if (observeLoadMore) {
                ObserveFeedLoadMore(listState, items.size, loading, noMore, onLoadMore)
            }
            OfficialPullRefreshBox(
                refreshing = refreshing,
                enabled = interactive && !loading && !refreshing,
                active = interactive,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(horizontal = horizontal),
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = interactive,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = (8 * theme.uiScale).dp,
                        bottom = (14 * theme.uiScale).dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        (7 * theme.uiScale).dp,
                    ),
                ) {
                    item(key = "feed-search", contentType = "feed-search") {
                        FeedToolbar(
                            theme = theme,
                            onSearch = onSearch,
                        )
                    }
                    if (loading && items.isEmpty()) {
                        item(key = "feed-loading") {
                            FeedLoading(theme)
                        }
                    } else if (items.isEmpty()) {
                        item(key = "feed-empty") {
                            FeedEmpty("暂无内容", "刷新", theme, onRefresh)
                        }
                    } else {
                        itemsIndexed(
                            items = items,
                            key = { index, item -> item.id.ifEmpty { "feed-$index" } },
                            contentType = { _, _ -> "feed-card" },
                        ) { _, item ->
                            ComposeFeedCard(
                                item = item,
                                theme = theme,
                                noImage = noImage,
                                currentUserId = currentUserId,
                                onOpen = onOpen,
                                onAction = onAction,
                                showSecondaryActions = false,
                                gameCardNoImage = gameCardNoImage,
                                presentationCache = presentationCache,
                                precomputedPresentation = presentations[item],
                                actionRevision = actionRevision,
                            )
                        }
                        item(key = "feed-footer", contentType = "feed-footer") {
                            FeedFooter(loading, noMore, theme, onLoadMore)
                        }
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
