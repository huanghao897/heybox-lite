package com.ronan.heyboxlite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeFeedCard(
    item: FeedItem,
    theme: ComposeThemeState,
    noImage: Boolean,
    onOpen: (FeedItem) -> Unit,
    onAction: ((FeedItem, FeedAction) -> Unit)?,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    cached: Boolean = false,
    gameCardNoImage: Boolean = false,
    showActions: Boolean = true,
    showSecondaryActions: Boolean = true,
    presentationCache: ComposeFeedPresentationCache? = null,
    precomputedPresentation: ComposeFeedPresentation? = null,
    imageLoader: ComposeImageLoader = ExistingComposeImageLoader,
    actionRevision: Int = 0,
    dataSaver: Boolean = LocalComposeDataSaver.current,
) {
    val scale = theme.uiScale
    val shape = RoundedCornerShape((WatchRadius.LARGE * scale).dp)
    val content = remember(item, presentationCache, precomputedPresentation) {
        precomputedPresentation ?: presentationCache?.get(item) ?: composeFeedPresentation(item)
    }
    val actions = remember(item, actionRevision, item.likes, item.liked) {
        ComposeFeedActionState.from(item)
    }
    val title = content.title
    val description = content.description
    // Plain posts are the default and carry no label; only the exceptions are tagged.
    val label = composeContentTypeLabel(item.video, item.article, item.pinned)
    // Surfaces are separated by tone (panel over background), not by an outline on
    // every card; borders stay reserved for controls that need their edge visible.
    Column(
        modifier = modifier.fillMaxWidth().clip(shape).background(theme.panel)
            .clickable { onOpen(item) }
            .padding(horizontal = (if (theme.roundScreen) 10 else 12).dp * scale,
                vertical = (if (theme.roundScreen) 9 else 10).dp * scale),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Keep the avatar bounded even before the async image loader has a bitmap.
            // Without an explicit size AndroidView can measure to the source bitmap size.
            ComposeRemoteImage(
                item.authorAvatar,
                theme,
                noImage,
                (28 * scale).dp,
                CircleShape,
                "作者头像",
                modifier = Modifier.size((28 * scale).dp),
                loader = imageLoader,
            )
            Column(modifier = Modifier.weight(1f).padding(start = (8 * scale).dp)) {
                Text(text = item.author.ifEmpty { "小黑盒社区" }, color = theme.text,
                    fontSize = (WatchType.SECONDARY * theme.textScale).sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.createdAt > 0L) {
                    Text(text = Format.relativeTime(item.createdAt), color = theme.subtle,
                        fontSize = (WatchType.CAPTION * theme.textScale).sp, maxLines = 1)
                }
            }
            if (label.isNotEmpty()) ComposePill(label, theme, item.pinned)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = (8 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ComposeFeedText(
                    source = title,
                    rich = content.richTitle,
                    theme = theme,
                    color = theme.text,
                    fontSize = (WatchType.SUBTITLE * theme.textScale).sp,
                    lineHeight = (20 * theme.textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                if (description.isNotEmpty()) {
                    ComposeFeedText(source = description, rich = content.richDescription,
                        theme = theme, color = theme.muted,
                        fontSize = (WatchType.SECONDARY * theme.textScale).sp,
                        lineHeight = (16 * theme.textScale).sp,
                        maxLines = if (theme.roundScreen) 1 else 2,
                        modifier = Modifier.padding(top = (4 * scale).dp))
                }
            }
            if (!noImage && item.image.isNotEmpty()) {
                ComposeRemoteImage(
                    url = item.image,
                    theme = theme,
                    noImage = false,
                    targetDp = (if (theme.roundScreen) 68 else 84).dp * scale,
                    shape = RoundedCornerShape((WatchRadius.SMALL * scale).dp),
                    contentDescription = "内容图片",
                    modifier = Modifier.padding(start = (8 * scale).dp).size(
                        (if (theme.roundScreen) 68 else 84).dp * scale,
                        (if (theme.roundScreen) 52 else 60).dp * scale,
                    ),
                    loader = imageLoader,
                    dataSaver = dataSaver,
                    onClick = { onOpen(item) },
                )
            }
        }
        ComposeGameCard(content.game, theme, noImage || gameCardNoImage, imageLoader, dataSaver) { onOpen(item) }
        if (showActions) {
            ComposeFeedCardActions(item, actions, favorite, cached, theme, noImage, imageLoader,
                showSecondaryActions, onAction)
        }
    }
}

/** "视频"/"文章" and the pinned mark; plain posts are the default and stay unlabeled. */
internal fun composeContentTypeLabel(video: Boolean, article: Boolean, pinned: Boolean): String {
    val type = when {
        video -> "视频"
        article -> "文章"
        else -> ""
    }
    return when {
        pinned && type.isNotEmpty() -> "置顶 · $type"
        pinned -> "置顶"
        else -> type
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
    dataSaver: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val targetPx = with(LocalDensity.current) { targetDp.toPx().toInt().coerceAtLeast(1) }
    val gate = rememberComposeContentImageGate(url, !noImage, dataSaver)
    val canLoad = gate.canLoad
    val bitmap = remember(url, targetPx, canLoad, loader) {
        mutableStateOf(if (canLoad) loader.cached(url, targetPx) else null)
    }
    DisposableEffect(url, targetPx, canLoad, loader) {
        var active = true
        val cancel = if (canLoad && bitmap.value == null) {
            loader.loadCancellable(url, targetPx) { if (active) bitmap.value = it }
        } else null
        onDispose {
            active = false
            cancel?.invoke()
        }
    }
    val image = remember(bitmap.value) { bitmap.value?.asImageBitmap() }
    val clickModifier = if (gate.waiting || onClick != null) {
        Modifier.clickable { gate.tap(onClick) }
    } else Modifier
    Box(
        modifier = modifier.clip(shape)
            .background(theme.panelElevated).then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (gate.waiting) {
            ComposeContentImageLoadIcon(theme)
        } else if (image != null) {
            Image(image, contentDescription, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        }
    }
}

@Composable
internal fun ComposePill(text: String, theme: ComposeThemeState, accent: Boolean = false) {
    Text(text = text, color = if (accent) theme.accent else theme.muted,
        fontSize = (WatchType.CAPTION * theme.textScale).sp, lineHeight = (15 * theme.textScale).sp,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(theme.panelElevated)
            .padding(horizontal = (6 * theme.uiScale).dp, vertical = (1 * theme.uiScale).dp))
}

@Composable
private fun ComposeGameCard(content: ComposeGameCardPresentation?, theme: ComposeThemeState,
                            noImage: Boolean, imageLoader: ComposeImageLoader, dataSaver: Boolean,
                            onOpen: () -> Unit) {
    if (content == null) return
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = (7 * theme.uiScale).dp)
            .clip(RoundedCornerShape((WatchRadius.MEDIUM * theme.uiScale).dp))
            .background(theme.panelElevated)
            .padding((9 * theme.uiScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content.coverUrl.isNotEmpty()) {
            ComposeRemoteImage(content.coverUrl, theme, noImage, (42 * theme.uiScale).dp,
                RoundedCornerShape((WatchRadius.SMALL * theme.uiScale).dp), "游戏封面",
                Modifier.size((42 * theme.uiScale).dp), loader = imageLoader,
                dataSaver = dataSaver, onClick = onOpen)
            Spacer(modifier = Modifier.width((8 * theme.uiScale).dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = content.name.ifEmpty { "游戏内容" }, color = theme.text,
                fontSize = (WatchType.BODY * theme.textScale).sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = content.metadata.ifEmpty { "游戏卡片 · ${content.count} 个" }, color = theme.muted,
                fontSize = (WatchType.CAPTION * theme.textScale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = (2 * theme.uiScale).dp))
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
            .heightIn(min = maxOf((36 * theme.uiScale).dp, WatchTouchTarget))
            .clip(RoundedCornerShape((WatchRadius.MEDIUM * theme.uiScale).dp))
            .background(theme.panel)
            .clickable(onClick = onSearch)
            .padding(horizontal = (9 * theme.uiScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.il_search),
            contentDescription = "搜索", tint = theme.muted,
            modifier = Modifier.size((WatchIcon.MEDIUM * theme.uiScale).dp))
        Text(
            text = "搜索帖子、作者或关键词",
            color = theme.muted,
            fontSize = (WatchType.SECONDARY * theme.textScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = (5 * theme.uiScale).dp),
        )
    }
}

@Composable
internal fun FeedLoading(theme: ComposeThemeState) {
    WatchLoading(fill = true, theme = theme)
}

@Composable
internal fun FeedEmpty(message: String, action: String, theme: ComposeThemeState,
                        onAction: () -> Unit) {
    WatchEmptyState(message, action = action, onAction = onAction, fill = true, theme = theme)
}

@Composable
internal fun FeedFooter(
    loading: Boolean,
    noMore: Boolean,
    theme: ComposeThemeState,
    onLoadMore: () -> Unit,
) {
    WatchActionButton(
        text = when {
            loading -> "正在加载更多…"
            noMore -> "没有更多内容"
            else -> "上滑加载更多"
        },
        onClick = onLoadMore, enabled = !loading && !noMore,
        modifier = Modifier.fillMaxWidth().padding(top = (2 * theme.uiScale).dp),
        theme = theme,
    )
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
        var requestedCount = -1
        snapshotFlow {
            val count = currentCount.value
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            when {
                count <= 0 || last < count - 3 -> 0
                currentLoading.value || currentNoMore.value -> -1
                else -> count
            }
        }.collect { count ->
            // A busy/declined request must not loop while the same page stays visible.
            if (count == 0) requestedCount = -1
            else if (count > 0 && count != requestedCount) {
                requestedCount = count
                latest.value()
            }
        }
    }
}

internal fun feedHorizontalPadding(maxWidth: Dp, theme: ComposeThemeState): Dp =
    if (!theme.roundScreen) (12 * theme.uiScale).dp
    else watchPageHorizontal(maxWidth, theme)
