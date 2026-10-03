package com.ronan.heyboxlite

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeFavoritesScreen(
    state: ComposeSavedPostsState,
    folders: List<ComposeFavoriteFolder>,
    selectedTab: ComposeFavoriteTab,
    foldersLoading: Boolean,
    foldersError: String,
    services: ComposeServices,
    onTabChange: (ComposeFavoriteTab) -> Unit,
    onOpen: (FeedItem) -> Unit,
    onOpenFolder: (ComposeFavoriteFolder) -> Unit,
    onAction: (FeedItem, FeedAction) -> Unit,
    onRetry: () -> Unit,
    onRetryFolders: () -> Unit,
    onBack: () -> Unit,
    listState: LazyListState,
) {
    val theme = services.theme
    ObserveSavedRotary(listState, services)
    SavedPage("我的收藏", theme, onBack) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(watchDp(10)))
            .background(theme.panel).padding(watchDp(3))) {
            FavoriteTab("帖子", selectedTab == ComposeFavoriteTab.POSTS, Modifier.weight(1f)) {
                onTabChange(ComposeFavoriteTab.POSTS)
            }
            FavoriteTab("收藏夹", selectedTab == ComposeFavoriteTab.FOLDERS, Modifier.weight(1f)) {
                onTabChange(ComposeFavoriteTab.FOLDERS)
            }
        }
        if (selectedTab == ComposeFavoriteTab.POSTS) {
            SavedFavoritePosts(state, services, listState, onOpen, onAction, onRetry)
        } else {
            if (foldersError.isNotBlank()) SavedLoadError(foldersError, folders.isNotEmpty(), onRetryFolders)
            when {
                folders.isEmpty() && foldersLoading -> FeedLoading(theme)
                folders.isEmpty() -> SavedEmpty(if (foldersError.isBlank()) "暂无收藏夹" else "加载失败", theme)
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = watchListEndPadding()),
                    verticalArrangement = Arrangement.spacedBy(watchDp(6))) {
                    items(folders, key = { it.id.ifEmpty { it.name } }) { folder ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(watchDp(10)))
                            .background(theme.panel).clickable { onOpenFolder(folder) }
                            .padding(horizontal = watchDp(12), vertical = watchDp(11)),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(androidx.compose.ui.res.painterResource(R.drawable.il_bookmark),
                                null, tint = theme.muted, modifier = Modifier.size(watchDp(18)))
                            Text(folder.name.ifBlank { "默认收藏夹" }, color = theme.text,
                                fontSize = watchSp(13f), fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(start = watchDp(9)),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (folder.count > 0) Text(folder.count.toString(),
                                color = theme.muted, fontSize = watchSp(11f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposeFavoriteFolderScreen(
    title: String,
    state: ComposeSavedPostsState,
    services: ComposeServices,
    onOpen: (FeedItem) -> Unit,
    onAction: (FeedItem, FeedAction) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    listState: LazyListState,
) {
    ObserveSavedRotary(listState, services)
    SavedPage(title.ifBlank { "收藏夹" }, services.theme, onBack) {
        SavedFavoritePosts(state, services, listState, onOpen, onAction, onRetry)
    }
}

@Composable
private fun FavoriteTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    val fraction by animateFloatAsState(if (selected) 1f else 0f,
        tween(if (Motions.off()) 0 else 160), label = "favorite-tab")
    TextButton(onClick, modifier.height(watchDp(34)).clip(RoundedCornerShape(watchDp(8)))
        .background(androidx.compose.ui.graphics.lerp(theme.panel, theme.panelElevated, fraction)),
        contentPadding = PaddingValues(0.dp)) {
        Text(label, color = if (selected) theme.text else theme.muted,
            fontSize = watchSp(12f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.SavedFavoritePosts(
    state: ComposeSavedPostsState,
    services: ComposeServices,
    listState: LazyListState,
    onOpen: (FeedItem) -> Unit,
    onAction: (FeedItem, FeedAction) -> Unit,
    onRetry: () -> Unit,
) {
    if (state.error.isNotBlank()) SavedLoadError(state.error, state.items.isNotEmpty(), onRetry)
    when {
        state.items.isEmpty() && state.loading -> FeedLoading(services.theme)
        state.items.isEmpty() -> SavedEmpty(if (state.error.isBlank()) "暂无内容" else "加载失败", services.theme)
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = watchDp(7), bottom = watchListEndPadding()),
            verticalArrangement = Arrangement.spacedBy(watchDp(7))) {
            items(state.items, key = { it.id }) { item ->
                ComposeFeedCard(item, services.theme, services.session.noImage(),
                    services.session.userId(), onOpen, onAction, favorite = item.favorited,
                    gameCardNoImage = services.session.gameCardNoImage())
            }
        }
    }
}
