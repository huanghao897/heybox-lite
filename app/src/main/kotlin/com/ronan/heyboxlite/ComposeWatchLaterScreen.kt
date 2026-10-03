package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun ComposeWatchLaterScreen(
    state: ComposeWatchLaterState,
    services: ComposeServices,
    onOpen: (FeedItem) -> Unit,
    onRemove: (ComposeOfflineEntry) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    listState: LazyListState,
) {
    val theme = services.theme
    ObserveSavedRotary(listState, services)
    SavedPage("稍后看", theme, onBack) {
        if (state.error.isNotBlank()) SavedLoadError(state.error, state.entries.isNotEmpty(), onRetry)
        when {
            state.entries.isEmpty() && state.loading -> FeedLoading(theme)
            state.entries.isEmpty() -> SavedEmpty(if (state.error.isBlank()) "暂无内容" else "读取失败", theme)
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = watchListEndPadding()),
                verticalArrangement = Arrangement.spacedBy(watchDp(7))) {
                items(state.entries, key = { it.item.id }) { entry ->
                    Column(Modifier.fillMaxWidth()) {
                        ComposeFeedCard(entry.item, theme, services.session.noImage(),
                            services.session.userId(), onOpen, null,
                            showActions = false, showFollow = false,
                            gameCardNoImage = services.session.gameCardNoImage())
                        Row(Modifier.fillMaxWidth().padding(horizontal = watchDp(8)),
                            verticalAlignment = Alignment.CenterVertically) {
                            val size = if (entry.bytes > 0) Format.offlineSize(entry.bytes) else "缓存已过期"
                            Text("$size · 更新于 ${Format.offlineTime(entry.updatedAt)}",
                                color = theme.muted, fontSize = watchSp(10f), maxLines = 2,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onRemove(entry) }, enabled = !state.loading) {
                                Text("移除", color = theme.accent, fontSize = watchSp(11f))
                            }
                        }
                    }
                }
            }
        }
    }
}
