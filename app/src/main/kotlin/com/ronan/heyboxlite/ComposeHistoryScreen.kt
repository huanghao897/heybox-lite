package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import java.util.Locale
import kotlinx.coroutines.launch

internal fun filterReadingHistory(items: List<FeedItem>, query: String): List<FeedItem> {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return items
    return items.filter { item ->
        (item.title + "\n" + item.description + "\n" + item.author)
            .lowercase(Locale.ROOT).contains(needle)
    }
}

@Composable
internal fun ComposeHistoryScreen(
    state: ComposeSavedPostsState,
    query: String,
    onQueryChange: (String) -> Unit,
    services: ComposeServices,
    onOpen: (FeedItem) -> Unit,
    onAction: (FeedItem, FeedAction) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    listState: LazyListState,
) {
    val scope = rememberCoroutineScope()
    val filtered = remember(state.items, query) { filterReadingHistory(state.items, query) }
    ObserveSavedRotary(listState, services)
    SavedPage("历史记录", services.theme, onBack) {
        ComposeSearchInput(query, "搜索历史") { value ->
            onQueryChange(value)
            if (filterReadingHistory(state.items, value).isNotEmpty()) {
                scope.launch { listState.scrollToItem(0) }
            }
        }
        if (state.error.isNotBlank()) SavedLoadError(state.error, state.items.isNotEmpty(), onRetry)
        when {
            state.items.isEmpty() && state.loading -> FeedLoading(services.theme)
            filtered.isEmpty() -> SavedEmpty(if (query.isBlank()) {
                if (state.error.isBlank()) "暂无内容" else "加载失败"
            } else "没有找到相关历史记录", services.theme)
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(top = watchDp(7), bottom = watchListEndPadding()),
                verticalArrangement = Arrangement.spacedBy(watchDp(7))) {
                items(filtered, key = { it.id }) { item ->
                    ComposeFeedCard(item, services.theme, services.session.noImage(),
                        services.session.userId(), onOpen, onAction,
                        favorite = item.favorited, showFollow = false,
                        gameCardNoImage = services.session.gameCardNoImage())
                }
            }
        }
    }
}
