package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

internal enum class ComposeFavoriteTab {
    POSTS,
    FOLDERS;

    companion object {
        val posts: ComposeFavoriteTab = POSTS
        val folders: ComposeFavoriteTab = FOLDERS
    }
}

/** Mirrors SavedPostParser.favoriteFolderId/name/count without passing JSON into UI. */
internal data class ComposeFavoriteFolder(
    val id: String,
    val name: String,
    val count: Int,
)

/** Mirrors ReadingCenterLoader.Snapshot plus the host-provided reading summary. */
internal data class ComposeReadingCenterState(
    val recent: FeedItem?,
    val watchLaterCount: Int,
    val offlineBytes: Long,
    val readingSummary: String,
    val hasSavedPosition: Boolean = false,
) {
    companion object {
        fun from(
            snapshot: ReadingCenterLoader.Snapshot,
            readingSummary: String,
            hasSavedPosition: Boolean = false,
        ) = ComposeReadingCenterState(
            recent = snapshot.recent,
            watchLaterCount = snapshot.watchLaterCount,
            offlineBytes = snapshot.offlineBytes,
            readingSummary = readingSummary,
            hasSavedPosition = hasSavedPosition,
        )
    }
}

@Composable
internal fun ComposeFavoritesScreen(
    items: List<FeedItem>,
    folders: List<ComposeFavoriteFolder>,
    selectedTab: ComposeFavoriteTab,
    loading: Boolean,
    services: ComposeServices,
    onTabChange: (ComposeFavoriteTab) -> Unit,
    onOpen: (FeedItem) -> Unit,
    onOpenFolder: (ComposeFavoriteFolder) -> Unit,
    onAction: (FeedItem, FeedAction) -> Unit,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    HeyboxComposeTheme(services.theme) {
        val rotary = services.theme.rotaryRequest
        LaunchedEffect(rotary?.serial) {
            if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
        }
        SavedPage("我的收藏", services.theme, onBack) {
            FavoriteTabs(selectedTab, services.theme, onTabChange)
            when {
                loading -> FeedLoading(services.theme)
                selectedTab == ComposeFavoriteTab.POSTS -> {
                    if (items.isEmpty()) {
                        SavedEmpty("暂无内容", services.theme)
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(top = (7 * services.theme.uiScale).dp),
                            verticalArrangement = Arrangement.spacedBy((7 * services.theme.uiScale).dp),
                        ) {
                            items(items, key = { it.id }) { item ->
                                ComposeFeedCard(
                                    item = item,
                                    theme = services.theme,
                                    noImage = services.session.noImage(),
                                    currentUserId = services.session.userId(),
                                    onOpen = onOpen,
                                    onAction = onAction,
                                    favorite = true,
                                    gameCardNoImage = services.session.gameCardNoImage(),
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (folders.isEmpty()) {
                        SavedEmpty("暂无收藏夹", services.theme)
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(top = (7 * services.theme.uiScale).dp),
                            verticalArrangement = Arrangement.spacedBy((6 * services.theme.uiScale).dp),
                        ) {
                            items(folders, key = { it.id.ifEmpty { it.name } }) { folder ->
                                FavoriteFolderRow(folder, services.theme, onOpenFolder)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposeWatchLaterScreen(
    entries: List<LocalCache.OfflineItem>,
    services: ComposeServices,
    onOpen: (FeedItem) -> Unit,
    onRemove: (LocalCache.OfflineItem) -> Unit,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    HeyboxComposeTheme(services.theme) {
        val rotary = services.theme.rotaryRequest
        LaunchedEffect(rotary?.serial) {
            if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
        }
        SavedPage("稍后看", services.theme, onBack) {
            if (entries.isEmpty()) {
                SavedEmpty("还没有稍后看的帖子", services.theme)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = (7 * services.theme.uiScale).dp),
                    verticalArrangement = Arrangement.spacedBy((7 * services.theme.uiScale).dp),
                ) {
                    items(entries, key = { it.item.id }) { entry ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ComposeFeedCard(
                                item = entry.item,
                                theme = services.theme,
                                noImage = services.session.noImage(),
                                currentUserId = services.session.userId(),
                                onOpen = onOpen,
                                onAction = null,
                                showActions = false,
                                showFollow = false,
                                gameCardNoImage = services.session.gameCardNoImage(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = (8 * services.theme.uiScale).dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val bytes = entry.detailBytes + ImageLoader.offlineBytes(entry.imageUrls)
                                Text(
                                    text = if (bytes > 0L) {
                                        "${Format.offlineSize(bytes)} · 更新于 ${Format.offlineTime(entry.updatedAt)}"
                                    } else {
                                        "缓存已过期 · 更新于 ${Format.offlineTime(entry.updatedAt)}"
                                    },
                                    color = services.theme.muted,
                                    fontSize = (10 * services.theme.textScale).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onRemove(entry) }) {
                                    Text(text = "移除", color = services.theme.accent,
                                        fontSize = (11 * services.theme.textScale).sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposeReadingCenterScreen(
    state: ComposeReadingCenterState?,
    loading: Boolean,
    services: ComposeServices,
    onOpen: (FeedItem) -> Unit,
    onReadingStats: () -> Unit,
    onWatchLater: () -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit,
) {
    HeyboxComposeTheme(services.theme) {
        SavedPage("阅读中心", services.theme, onBack) {
            when {
                loading -> FeedLoading(services.theme)
                state == null -> SavedEmpty("阅读数据读取失败", services.theme)
                else -> ReadingCenterContent(
                    state,
                    services.theme,
                    services.session.isLoggedIn(),
                    onOpen,
                    onReadingStats,
                    onWatchLater,
                    onHistory,
                )
            }
        }
    }
}

@Composable
internal fun ComposeHistoryScreen(
    items: List<FeedItem>,
    loading: Boolean,
    services: ComposeServices,
    onOpen: (FeedItem) -> Unit,
    onBack: () -> Unit,
) {
    HeyboxComposeTheme(services.theme) {
        val rotary = services.theme.rotaryRequest
        val listState = rememberLazyListState()
        LaunchedEffect(rotary?.serial) {
            if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
        }
        SavedPage("历史记录", services.theme, onBack) {
            when {
                loading -> FeedLoading(services.theme)
                items.isEmpty() -> SavedEmpty("暂无内容", services.theme)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = (7 * services.theme.uiScale).dp),
                    verticalArrangement = Arrangement.spacedBy((7 * services.theme.uiScale).dp),
                ) {
                    items(items, key = { it.id }) { item ->
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
                }
            }
        }
    }
}

@Composable
private fun ReadingCenterContent(
    state: ComposeReadingCenterState,
    theme: ComposeThemeState,
    loggedIn: Boolean,
    onOpen: (FeedItem) -> Unit,
    onReadingStats: () -> Unit,
    onWatchLater: () -> Unit,
    onHistory: () -> Unit,
) {
    if (state.recent != null) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(state.recent) },
            colors = CardDefaults.cardColors(containerColor = theme.panel),
            shape = RoundedCornerShape((10 * theme.uiScale).dp),
        ) {
            Column(modifier = Modifier.padding((12 * theme.uiScale).dp)) {
                Text(text = "继续阅读", color = theme.text, fontSize = (14 * theme.textScale).sp,
                    fontWeight = FontWeight.Bold)
                Text(text = state.recent.title.ifEmpty { "无标题内容" }, color = theme.muted,
                    fontSize = (11 * theme.textScale).sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = (4 * theme.uiScale).dp))
                if (state.hasSavedPosition) {
                    Text(text = "已记录上次阅读位置", color = theme.subtle,
                        fontSize = (10 * theme.textScale).sp,
                        modifier = Modifier.padding(top = (5 * theme.uiScale).dp))
                }
            }
        }
    }
    Text(text = "阅读库", color = theme.muted, fontSize = (11 * theme.textScale).sp,
        modifier = Modifier.padding(start = (3 * theme.uiScale).dp,
            top = (5 * theme.uiScale).dp))
    SavedRow("阅读时长", state.readingSummary, R.drawable.il_reading, theme, onReadingStats)
    SavedRow(
        "稍后看",
        "${state.watchLaterCount} 篇 · ${Format.cacheMb(state.offlineBytes)}",
        R.drawable.il_history,
        theme,
        onWatchLater,
    )
    SavedRow(
        "历史记录",
        if (loggedIn) "小黑盒云端记录" else "登录后查看小黑盒记录",
        R.drawable.il_history,
        theme,
        onHistory,
    )
}

@Composable
private fun SavedPage(
    title: String,
    theme: ComposeThemeState,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = (8 * theme.uiScale).dp,
                end = (8 * theme.uiScale).dp,
                top = (5 * theme.uiScale).dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size((34 * theme.uiScale).dp)) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "返回", tint = theme.text)
            }
            Text(text = title, color = theme.text, fontSize = (17 * theme.textScale).sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = savedHorizontalPadding(theme),
                    vertical = (4 * theme.uiScale).dp),
            content = content,
        )
    }
}

@Composable
private fun FavoriteTabs(
    selected: ComposeFavoriteTab,
    theme: ComposeThemeState,
    onSelect: (ComposeFavoriteTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((10 * theme.uiScale).dp))
            .background(theme.panel).padding((3 * theme.uiScale).dp),
    ) {
        FavoriteTab("帖子", selected == ComposeFavoriteTab.POSTS, theme,
            Modifier.weight(1f)) { onSelect(ComposeFavoriteTab.POSTS) }
        FavoriteTab("收藏夹", selected == ComposeFavoriteTab.FOLDERS, theme,
            Modifier.weight(1f)) { onSelect(ComposeFavoriteTab.FOLDERS) }
    }
}

@Composable
private fun FavoriteTab(text: String, selected: Boolean, theme: ComposeThemeState,
                        modifier: Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height((34 * theme.uiScale).dp)
            .clip(RoundedCornerShape((8 * theme.uiScale).dp))
            .background(if (selected) theme.panelElevated else theme.panel),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text = text, color = if (selected) theme.text else theme.muted,
            fontSize = (12 * theme.textScale).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FavoriteFolderRow(
    folder: ComposeFavoriteFolder,
    theme: ComposeThemeState,
    onOpen: (ComposeFavoriteFolder) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((10 * theme.uiScale).dp))
            .background(theme.panel).clickable { onOpen(folder) }
            .padding(horizontal = (12 * theme.uiScale).dp, vertical = (11 * theme.uiScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "☆", color = theme.accent, fontSize = (20 * theme.textScale).sp)
        Text(text = folder.name.ifEmpty { "默认收藏夹" }, color = theme.text,
            fontSize = (13 * theme.textScale).sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = (9 * theme.uiScale).dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (folder.count > 0) {
            Text(text = folder.count.toString(), color = theme.muted,
                fontSize = (11 * theme.textScale).sp)
        }
    }
}

@Composable
private fun SavedRow(
    title: String,
    value: String,
    icon: Int,
    theme: ComposeThemeState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((10 * theme.uiScale).dp))
            .background(theme.panel).clickable(onClick = onClick)
            .padding(horizontal = (11 * theme.uiScale).dp, vertical = (10 * theme.uiScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(icon),
            contentDescription = null,
            tint = theme.muted,
            modifier = Modifier.size((18 * theme.uiScale).dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = (8 * theme.uiScale).dp)) {
            Text(text = title, color = theme.text, fontSize = (13 * theme.textScale).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value.isNotEmpty()) {
                Text(text = value, color = theme.muted, fontSize = (11 * theme.textScale).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = (2 * theme.uiScale).dp))
            }
        }
        Text(text = "›", color = theme.subtle, fontSize = (18 * theme.textScale).sp)
    }
}

@Composable
private fun ColumnScope.SavedEmpty(message: String, theme: ComposeThemeState) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(text = message, color = theme.muted, fontSize = (13 * theme.textScale).sp)
    }
}

private fun savedHorizontalPadding(theme: ComposeThemeState) =
    if (theme.roundScreen) (16 * theme.uiScale).dp else (12 * theme.uiScale).dp

@Preview(name = "Reading center shell", showBackground = true, widthDp = 360, heightDp = 520)
@Composable
private fun ComposeReadingCenterPreview() {
    val theme = composePreviewTheme(false)
    HeyboxComposeTheme(theme) {
        SavedPage("阅读中心", theme, {}) {
            Text(text = "阅读库", color = theme.muted, fontSize = 11.sp)
            SavedRow("阅读时长", "24 分钟", R.drawable.il_reading, theme, {})
            SavedRow("稍后看", "3 篇 · 12.0 MB", R.drawable.il_history, theme, {})
            SavedRow("历史记录", "登录后查看小黑盒记录", R.drawable.il_history, theme, {})
        }
    }
}
