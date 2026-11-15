package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeSearchScreen(
    query: String,
    items: List<FeedItem>,
    loading: Boolean,
    noMore: Boolean,
    services: ComposeServices,
    onQueryChange: (String) -> Unit,
    onOpen: (FeedItem) -> Unit,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    observeLoadMore: Boolean = true,
    history: List<String> = emptyList(),
    onClearHistory: () -> Unit = {},
    error: String = "",
    onRetry: () -> Unit = onLoadMore,
) {
    HeyboxComposeTheme(services.theme) {
        val rotary = services.theme.rotaryRequest
        LaunchedEffect(rotary?.serial) {
            if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(services.theme.background),
        ) {
            val horizontal = feedHorizontalPadding(maxWidth, services.theme)
            if (observeLoadMore) {
                ObserveFeedLoadMore(listState, items.size, loading, noMore || error.isNotBlank(), onLoadMore)
            }
            Column(modifier = Modifier.fillMaxSize().testTag("search-page-content")) {
                Box(Modifier.fillMaxWidth().padding(horizontal = horizontal)) {
                    ComposeLeadingSearchInput(query, "搜索帖子、作者或关键词", onQueryChange)
                }
                when {
                    query.isBlank() && history.isNotEmpty() -> Box(
                        Modifier.weight(1f).padding(horizontal = horizontal),
                    ) {
                        ComposeSearchHistory(history, onQueryChange, onClearHistory, listState)
                    }
                    loading && items.isEmpty() -> FeedLoading(services.theme)
                    error.isNotBlank() && items.isEmpty() -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = horizontal),
                        verticalArrangement = Arrangement.Center,
                        contentPadding = PaddingValues(
                            top = (8 * services.theme.uiScale).dp,
                            bottom = watchListEndPadding(),
                        ),
                    ) {
                        item {
                            WatchErrorState(error, onRetry,
                                theme = services.theme)
                        }
                    }
                    items.isEmpty() && query.isNotBlank() && !noMore -> WatchEmptyState(
                        "暂时没找到相关帖子", action = "继续查找", onAction = onLoadMore,
                        fill = true, theme = services.theme,
                    )
                    items.isEmpty() -> WatchEmptyState(
                        if (query.isBlank()) "输入关键词开始搜索" else "没有找到相关帖子",
                        fill = true, theme = services.theme,
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = horizontal),
                        contentPadding = PaddingValues(
                            top = (8 * services.theme.uiScale).dp,
                            bottom = watchListEndPadding(),
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            (7 * services.theme.uiScale).dp,
                        ),
                    ) {
                        itemsIndexed(
                            items = items,
                            key = { index, item -> item.id.ifEmpty { "search-$index" } },
                        ) { _, item ->
                            ComposeFeedCard(
                                item = item,
                                theme = services.theme,
                                noImage = services.session.noImage(),
                                onOpen = onOpen,
                                onAction = null,
                                showActions = false,
                                gameCardNoImage = services.session.gameCardNoImage(),
                            )
                        }
                        item {
                            if (error.isNotBlank()) WatchErrorState(error, onRetry, theme = services.theme)
                            else FeedFooter(loading, noMore, services.theme, onLoadMore)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Search field", showBackground = true, widthDp = 360, heightDp = 120)
@Composable
private fun ComposeSearchFieldPreview() {
    val theme = composePreviewTheme(roundScreen = false)
    HeyboxComposeTheme(theme) {
        ComposeSearchInput("", "搜索帖子、作者或关键词", {})
    }
}
