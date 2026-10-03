package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.text.TextUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeFeedCard(
    item: FeedItem,
    theme: ComposeThemeState,
    noImage: Boolean,
    currentUserId: String,
    onOpen: (FeedItem) -> Unit,
    onAction: ((FeedItem, FeedAction) -> Unit)?,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    cached: Boolean = false,
    gameCardNoImage: Boolean = false,
    showActions: Boolean = true,
    showFollow: Boolean = true,
    showSecondaryActions: Boolean = true,
) {
    val scale = theme.uiScale
    val shape = RoundedCornerShape((if (theme.roundScreen) 9 else 10).dp)
    val title = remember(item.title) {
        composeFeedText(item.title).ifEmpty { "无标题内容" }
    }
    val description = remember(item.description) {
        composeFeedText(item.description)
    }
    val type = when {
        item.video -> "视频"
        item.article -> "文章"
        else -> "帖子"
    }
    Column(
        modifier = modifier.fillMaxWidth().clip(shape).background(theme.panel)
            .border(BorderStroke((1 * scale).dp, theme.hairline), shape)
            .clickable { onOpen(item) }
            .padding((if (theme.roundScreen) 9 else 10).dp * scale),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Keep the avatar bounded even before the async image loader has a bitmap.
            // Without an explicit size AndroidView can measure to the source bitmap size.
            ComposeRemoteImage(
                item.authorAvatar,
                theme,
                noImage,
                (26 * scale).dp,
                CircleShape,
                "作者头像",
                modifier = Modifier.size((26 * scale).dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = (8 * scale).dp)) {
                Text(text = item.author.ifEmpty { "小黑盒社区" }, color = theme.text,
                    fontSize = (11 * theme.textScale).sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.createdAt > 0L) {
                    Text(text = Format.relativeTime(item.createdAt), color = theme.subtle,
                        fontSize = (10 * theme.textScale).sp, maxLines = 1)
                }
            }
            ComposePill(if (item.pinned) "置顶 · $type" else type, theme, item.pinned)
            if (showFollow && item.authorId.isNotEmpty() && item.authorId != currentUserId) {
                Box(
                    modifier = Modifier.size((25 * scale).dp)
                        .clip(CircleShape)
                        .background(theme.panelElevated)
                        .clickable(enabled = !item.followPending) {
                            onAction?.invoke(item, FeedAction.FOLLOW)
                        }
                        .semantics {
                            contentDescription = if (item.following) "取消关注" else "关注"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = if (item.following) "✓" else "+",
                        color = if (item.following) theme.accent else theme.muted,
                        fontSize = (14 * theme.textScale).sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = (8 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ComposeRichText(
                    source = title,
                    darkMode = theme.dark,
                    textColor = theme.text,
                    linkColor = theme.link,
                    fontSize = ((if (theme.roundScreen) 14 else 15) * theme.textScale).sp,
                    lineHeight = ((if (theme.roundScreen) 18 else 19) * theme.textScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    ellipsize = TextUtils.TruncateAt.END,
                )
                if (description.isNotEmpty()) {
                    ComposeRichText(source = description, darkMode = theme.dark,
                        textColor = theme.muted, linkColor = theme.link,
                        fontSize = ((if (theme.roundScreen) 10 else 11) * theme.textScale).sp,
                        lineHeight = (15 * theme.textScale).sp,
                        maxLines = if (theme.roundScreen) 1 else 2,
                        ellipsize = TextUtils.TruncateAt.END,
                        modifier = Modifier.padding(top = (4 * scale).dp))
                }
            }
            if (!noImage && item.image.isNotEmpty()) {
                ComposeRemoteImage(
                    url = item.image,
                    theme = theme,
                    noImage = false,
                    targetDp = (if (theme.roundScreen) 68 else 84).dp,
                    shape = RoundedCornerShape((8 * scale).dp),
                    contentDescription = "内容图片",
                    modifier = Modifier.padding(start = (8 * scale).dp).size(
                        (if (theme.roundScreen) 68 else 84).dp * scale,
                        (if (theme.roundScreen) 52 else 60).dp * scale,
                    ),
                )
            }
        }
        ComposeGameCard(item, theme, noImage || gameCardNoImage)
        if (showActions) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = (5 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.topicName.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart)
                                .widthIn(max = (120 * scale).dp)
                                .clip(RoundedCornerShape((8 * scale).dp))
                                .background(theme.panelElevated)
                                .padding(horizontal = (6 * scale).dp, vertical = (3 * scale).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!noImage && item.topicIcon.isNotEmpty()) {
                                ComposeRemoteImage(
                                    item.topicIcon, theme, false, (13 * scale).dp,
                                    CircleShape, "分区图标",
                                    Modifier.size((13 * scale).dp),
                                )
                                Spacer(Modifier.width((4 * scale).dp))
                            }
                            Text(text = item.topicName, color = theme.muted,
                                fontSize = (9.5f * theme.textScale).sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                ComposeActionButton(R.drawable.official_comment_like_filled,
                    Format.commentLikeCount(item.likes),
                    if (item.liked) "取消点赞" else "点赞", item.liked, theme) {
                    onAction?.invoke(item, FeedAction.LIKE)
                }
                if (showSecondaryActions) {
                    ComposeActionButton(
                        if (favorite) R.drawable.official_favorite_filled else R.drawable.official_favorite_line,
                        "",
                        if (favorite) "取消收藏" else "收藏", favorite, theme) {
                        onAction?.invoke(item, FeedAction.FAVORITE)
                    }
                    ComposeActionButton(R.drawable.ic_download, "",
                        if (cached) "移除缓存" else "稍后看", cached, theme) {
                        onAction?.invoke(item, FeedAction.CACHE)
                    }
                }
                ComposeActionButton(R.drawable.official_detail_comment,
                    Format.commentLikeCount(item.comments), "评论",
                    false, theme) { onAction?.invoke(item, FeedAction.COMMENT) }
            }
        }
    }
}

@Composable
internal fun ComposeRemoteImage(
    url: String,
    theme: ComposeThemeState,
    noImage: Boolean,
    targetDp: Dp,
    shape: Shape,
    contentDescription: String?,
    // targetDp is also the safe fallback layout size; callers can override it
    // for thumbnails or full-width media with an explicit modifier.
    modifier: Modifier = Modifier.size(targetDp),
    loader: ComposeImageLoader = ExistingComposeImageLoader,
) {
    val targetPx = with(LocalDensity.current) { targetDp.toPx().toInt().coerceAtLeast(1) }
    val bitmap by produceState<Bitmap?>(null, url, targetPx, noImage, loader) {
        if (!noImage && url.isNotBlank()) loader.load(url, targetPx) { value = it }
    }
    Box(
        modifier = modifier.clip(shape)
            .background(if (theme.dark) Color(0xFF2A2B2D) else Color(0xFFE8EAEC)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        }
    }
}

@Composable
internal fun ComposePill(text: String, theme: ComposeThemeState, accent: Boolean = false) {
    Text(text = text, color = if (accent) theme.accent else theme.muted,
        fontSize = (9 * theme.textScale).sp,
        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape((8 * theme.uiScale).dp))
            .background(theme.panelElevated)
            .padding(horizontal = (6 * theme.uiScale).dp, vertical = (4 * theme.uiScale).dp))
}

@Composable
private fun ComposeGameCard(item: FeedItem, theme: ComposeThemeState, noImage: Boolean) {
    val content = remember(item.contentPreload) {
        composeGameCardPresentation(item.contentPreload)
    } ?: return
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = (7 * theme.uiScale).dp)
            .clip(RoundedCornerShape((8 * theme.uiScale).dp))
            .background(theme.panelElevated)
            .border(BorderStroke((1 * theme.uiScale).dp, theme.hairline),
                RoundedCornerShape((8 * theme.uiScale).dp))
            .padding((8 * theme.uiScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content.coverUrl.isNotEmpty()) {
            ComposeRemoteImage(content.coverUrl, theme, noImage, (42 * theme.uiScale).dp,
                RoundedCornerShape((6 * theme.uiScale).dp), "游戏封面",
                Modifier.size((42 * theme.uiScale).dp))
            Spacer(modifier = Modifier.width((8 * theme.uiScale).dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = content.name.ifEmpty { "游戏内容" }, color = theme.text,
                fontSize = (12 * theme.textScale).sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = content.metadata.ifEmpty { "游戏卡片 · ${content.count} 个" }, color = theme.muted,
                fontSize = (10 * theme.textScale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = (2 * theme.uiScale).dp))
        }
    }
}

@Composable
private fun ComposeActionButton(
    icon: Int,
    label: String,
    description: String,
    active: Boolean,
    theme: ComposeThemeState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height((29 * theme.uiScale).dp)
            .clip(RoundedCornerShape((7 * theme.uiScale).dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = (3 * theme.uiScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(icon),
            contentDescription = null,
            tint = if (active) theme.accent else theme.muted,
            modifier = Modifier.size((16 * theme.uiScale).dp),
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = if (active) theme.accent else theme.muted,
                fontSize = ((if (theme.roundScreen) 9 else 10) * theme.textScale).sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(start = (2 * theme.uiScale).dp),
            )
        }
    }
}

@Composable
internal fun FeedToolbar(
    theme: ComposeThemeState,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = (5 * theme.uiScale).dp)
            .height((34 * theme.uiScale).dp)
            .clip(RoundedCornerShape((10 * theme.uiScale).dp))
            .background(theme.panel.copy(alpha = if (theme.dark) 0.86f else 0.72f))
            .clickable(onClick = onSearch)
            .padding(horizontal = (9 * theme.uiScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.il_search),
            contentDescription = "搜索", tint = theme.muted,
            modifier = Modifier.size((16 * theme.uiScale).dp))
        Text(
            text = "搜索帖子、作者或关键词",
            color = theme.muted,
            fontSize = (11 * theme.textScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = (5 * theme.uiScale).dp),
        )
    }
}

@Composable
internal fun FeedLoading(theme: ComposeThemeState) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(modifier = Modifier.width((100 * theme.uiScale).dp),
            color = theme.accent, trackColor = theme.panelElevated)
        Text(text = "加载中", color = theme.muted, fontSize = (12 * theme.textScale).sp,
            modifier = Modifier.padding(top = (8 * theme.uiScale).dp))
    }
}

@Composable
internal fun FeedEmpty(message: String, action: String, theme: ComposeThemeState,
                        onAction: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = message, color = theme.muted, fontSize = (13 * theme.textScale).sp)
        TextButton(onClick = onAction) {
            Text(text = action, color = theme.accent, fontSize = (12 * theme.textScale).sp)
        }
    }
}

@Composable
internal fun FeedFooter(
    loading: Boolean,
    noMore: Boolean,
    theme: ComposeThemeState,
    onLoadMore: () -> Unit,
) {
    TextButton(onClick = onLoadMore, enabled = !loading && !noMore,
        modifier = Modifier.fillMaxWidth().padding(top = (2 * theme.uiScale).dp)) {
        Text(
            text = when {
                loading -> "正在加载更多…"
                noMore -> "没有更多内容"
                else -> "上滑加载更多"
            },
            color = theme.muted,
            fontSize = (11 * theme.textScale).sp)
    }
}

@Composable
internal fun ObserveFeedLoadMore(
    listState: LazyListState,
    itemCount: Int,
    loading: Boolean,
    noMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val latest = rememberUpdatedState(onLoadMore)
    val currentCount = rememberUpdatedState(itemCount)
    val currentLoading = rememberUpdatedState(loading)
    val currentNoMore = rememberUpdatedState(noMore)
    LaunchedEffect(listState) {
        var lastSeen = -1
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last != lastSeen) {
                    lastSeen = last
                    if (!currentLoading.value && !currentNoMore.value && currentCount.value > 0
                        && last >= currentCount.value - 3) latest.value()
                }
            }
    }
}

internal fun feedHorizontalPadding(maxWidth: Dp, theme: ComposeThemeState): Dp =
    if (!theme.roundScreen) (12 * theme.uiScale).dp
    else (maxWidth.value * 0.1f).coerceIn(13f, 23f).dp
