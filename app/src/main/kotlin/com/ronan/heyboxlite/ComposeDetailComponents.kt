package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeDetailBlock(
    block: RichContent.Block,
    media: ComposeMediaSettings,
    services: ComposeServices?,
    onOpenImage: (String) -> Unit,
) {
    val display = remember(block) { ArticleText.stripMarkdownEmphasis(block.value) }
    when (block.kind) {
        RichContent.Block.IMAGE -> ComposeDetailImage(block.value, media, onOpenImage)
        RichContent.Block.GAME_CARD -> ComposeDetailGameCard(block, media, services)
        RichContent.Block.QUOTE -> Row(Modifier.fillMaxWidth()) {
            val theme = LocalHeyboxTheme.current
            Box(Modifier.width(3.dp).height(40.dp).background(theme.hairline))
            SelectionContainer {
                ComposeInlineText(display, theme.dark, theme.muted, theme.link,
                    Modifier.padding(start = 8.dp), fontSize = 12.sp * theme.textScale,
                    lineHeight = 18.sp * theme.textScale)
            }
        }
        else -> {
            val theme = LocalHeyboxTheme.current
            val bodyScale = theme.textScale * (services?.session?.bodyTextScale() ?: 100) / 100f
            val fontSize = when (block.kind) {
                RichContent.Block.HEADING -> 15.sp * bodyScale
                RichContent.Block.CAPTION -> 10.sp * bodyScale
                else -> 13.sp * bodyScale
            }
            val paragraphs = remember(display, block.kind) {
                if (block.kind == RichContent.Block.TEXT) ArticleText.articleParagraphs(display) else listOf(display)
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(
                    ((services?.session?.bodyParagraphSpacing() ?: 9) * theme.uiScale).dp)) {
                    paragraphs.forEach { paragraph ->
                        ComposeInlineText(paragraph, theme.dark,
                            if (block.kind == RichContent.Block.CAPTION) theme.muted else theme.text, theme.link,
                            fontSize = fontSize,
                            fontWeight = if (block.kind == RichContent.Block.HEADING) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = if (block.kind == RichContent.Block.TEXT)
                                (fontSize.value * maxOf(1.18f,
                                    (services?.session?.bodyLineSpacing() ?: 122) / 100f) + 1f).sp
                                else (if (block.kind == RichContent.Block.CAPTION) 15.sp else 20.sp) * bodyScale,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeDetailGameCard(
    block: RichContent.Block,
    media: ComposeMediaSettings,
    services: ComposeServices?,
) {
    val theme = LocalHeyboxTheme.current
    var data by remember(block.value, block.gameObject) {
        mutableStateOf(GameCardData.fromEmbedded(block.gameObject, block.value))
    }
    var active by remember(block.value, block.gameObject) { mutableStateOf(true) }
    DisposableEffect(block.value, block.gameObject) {
        onDispose { active = false }
    }
    LaunchedEffect(block.value, block.gameObject) {
        val appId = block.value.trim()
        if (appId.isEmpty() || data?.hasVisibleDetails() == true) return@LaunchedEffect
        val client = services?.gameDetails ?: return@LaunchedEffect
        client.loadBatch(
            listOf(RichLinkClassifier.GameLinkInfo(appId, "pc", "", "")),
            object : GameDetailClient.BatchCallback {
                override fun onComplete(value: Map<String, GameCardData>) {
                    if (active) data = value[appId] ?: data
                }
            },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(theme.panelElevated).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (media.enabled && !media.gameCardNoImage && !data?.coverUrl.isNullOrBlank()) {
            ComposeRemoteImage(
                data?.coverUrl.orEmpty(), theme, false, 44.dp,
                RoundedCornerShape(6.dp), "游戏封面", Modifier.size(44.dp),
            )
        } else {
            Icon(painterResource(R.drawable.ic_game_link), null,
                tint = theme.accent, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(data?.name?.ifBlank { "游戏" } ?: "游戏内容", fontSize = 12.sp,
                color = theme.text, maxLines = 2,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val meta = listOf(data?.platforms, data?.score, data?.currentPrice)
                .filter { !it.isNullOrBlank() }.joinToString(" · ")
            Text(meta.ifBlank { "正在获取游戏信息" },
                fontSize = 10.sp, color = theme.muted)
        }
    }
}

@Composable
internal fun ComposeDetailSortTab(text: String, selected: Boolean, onSelect: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    Text(text, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) theme.background else theme.muted,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onSelect)
            .background(if (selected) theme.text else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = watchDp(7), vertical = watchDp(5)))
}

@Composable
internal fun ComposeDetailDock(
    item: FeedItem,
    liked: Boolean,
    likes: Int,
    inset: androidx.compose.ui.unit.Dp,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onFavorite: () -> Unit = {},
    onCache: () -> Unit = {},
    actionRevision: Int = 0,
) {
    val labelSize = watchSp(10f)
    val density = LocalDensity.current
    val gap = watchDp(5)
    val minimumWidth = watchDp(24) + with(density) { labelSize.toDp() } * 2.6f
    val commands = listOf(
        DetailCommand(if (liked) R.drawable.official_comment_like_filled else R.drawable.official_comment_like_line,
            Format.commentLikeCount(likes.coerceAtLeast(0)), "点赞帖子", onLike),
        DetailCommand(if (item.favorited) R.drawable.official_favorite_filled else R.drawable.official_favorite_line,
            "", if (item.favorited) "取消收藏" else "收藏", onFavorite),
        DetailCommand(R.drawable.ic_history, "缓存", "缓存帖子", onCache),
        DetailCommand(R.drawable.official_detail_comment, Format.commentLikeCount(item.comments.coerceAtLeast(0)),
            "评论", onComment),
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 3.dp)
        .testTag("detail-actions")) {
        val columns = if (maxWidth >= minimumWidth * 4 + gap * 3) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            commands.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { command ->
                        DetailCountAction(command.icon, command.label, command.description,
                            command.onClick, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class DetailCommand(val icon: Int, val label: String, val description: String, val onClick: () -> Unit)

@Composable
internal fun ComposeDetailButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                                 enabled: Boolean = true, primary: Boolean = true, icon: Int? = null) {
    val theme = LocalHeyboxTheme.current
    Button(onClick, modifier.fillMaxWidth().heightIn(min = watchDp(36)), enabled = enabled,
        shape = RoundedCornerShape(watchDp(8)),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) theme.accent else theme.panel,
            contentColor = if (primary) theme.onAccent else theme.text,
            disabledContainerColor = theme.panelElevated, disabledContentColor = theme.subtle),
        contentPadding = PaddingValues(horizontal = watchDp(8), vertical = watchDp(7))) {
        if (icon != null) {
            Icon(painterResource(icon), null, modifier = Modifier.size(watchDp(13)))
            Spacer(Modifier.width(watchDp(4)))
        }
        Text(text, fontSize = watchSp(12f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailCountAction(icon: Int, label: String, description: String, onClick: () -> Unit,
                              modifier: Modifier) {
    val theme = LocalHeyboxTheme.current
    val shape = RoundedCornerShape(watchDp(7))
    Row(modifier.clip(shape).background(theme.panel).border(0.5.dp, theme.hairline, shape)
        .clickable(onClick = onClick).heightIn(min = watchDp(36).coerceAtLeast(28.dp))
        .padding(horizontal = watchDp(3), vertical = watchDp(5)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), description, tint = theme.text,
            modifier = Modifier.size(watchDp(15)))
        if (label.isNotBlank()) {
            Spacer(Modifier.width(watchDp(3)))
            Text(label, fontSize = watchSp(10f), color = theme.text, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
