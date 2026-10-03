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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                ObserveFeedLoadMore(listState, items.size, loading, noMore, onLoadMore)
            }
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = horizontal)) {
                ComposePageHeader("搜索", onBack)
                ComposeSearchInput(query, "搜索帖子、作者或关键词", onQueryChange)
                when {
                    loading && items.isEmpty() -> FeedLoading(services.theme)
                    items.isEmpty() -> SearchEmpty(
                        message = if (query.isBlank()) "输入关键词开始搜索" else "没有找到相关帖子",
                        theme = services.theme,
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
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
                            key = { index, item -> item.id.ifEmpty { "search-$index" } },
                        ) { _, item ->
                            ComposeFeedCard(
                                item = item,
                                theme = services.theme,
                                noImage = services.session.noImage(),
                                currentUserId = services.session.userId(),
                                onOpen = onOpen,
                            onAction = null,
                            showActions = false,
                            showFollow = false,
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

@Composable
private fun SearchEmpty(message: String, theme: ComposeThemeState) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = (12 * theme.uiScale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, color = theme.muted, fontSize = (13 * theme.textScale).sp)
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
