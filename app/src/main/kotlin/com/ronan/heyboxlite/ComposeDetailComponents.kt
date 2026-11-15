package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeDetailBlock(
    block: RichContent.Block,
    media: ComposeMediaSettings,
    services: ComposeServices?,
    onOpenImage: (String) -> Unit,
    onOpenPreview: ((String, android.widget.ImageView) -> Unit)? = null,
) {
    val display = remember(block) { ArticleText.stripMarkdownEmphasis(block.value) }
    when (block.kind) {
        RichContent.Block.IMAGE -> ComposeDetailImage(block.value, media, onOpenImage,
            onOpenPreview = onOpenPreview)
        RichContent.Block.GAME_CARD -> ComposeDetailGameCard(block, media, services)
        RichContent.Block.QUOTE -> {
            val theme = LocalHeyboxTheme.current
            val typography = composeDetailBodyTypography(block.kind, services?.session)
            val bar = theme.subtle
            // The bar is drawn behind the quote, so it always spans exactly the quoted lines.
            SelectionContainer(Modifier.fillMaxWidth().testTag("detail-quote").drawBehind {
                drawRoundRect(bar, size = Size(3.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(1.5.dp.toPx()))
            }) {
                ComposeInlineText(display, theme.dark, theme.muted, theme.link,
                    Modifier.padding(start = 3.dp + watchDp(8)), fontSize = typography.fontSize,
                    lineHeight = typography.lineHeight,
                    fontWeight = typography.fontWeight ?: FontWeight.Normal,
                    letterSpacing = typography.letterSpacing)
            }
        }
        else -> {
            val theme = LocalHeyboxTheme.current
            val typography = composeDetailBodyTypography(block.kind, services?.session)
            val paragraphs = remember(display, block.kind) {
                if (block.kind == RichContent.Block.TEXT) ArticleText.articleParagraphs(display) else listOf(display)
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(
                    (theme.reading.paragraphSpacing * theme.uiScale).dp)) {
                    paragraphs.forEach { paragraph ->
                        ComposeInlineText(paragraph, theme.dark,
                            if (block.kind == RichContent.Block.CAPTION) theme.muted else theme.text, theme.link,
                            fontSize = typography.fontSize,
                            fontWeight = typography.fontWeight ?: FontWeight.Normal,
                            lineHeight = typography.lineHeight,
                            letterSpacing = typography.letterSpacing,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposeDetailSortTab(text: String, selected: Boolean, onSelect: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    Box(Modifier.testTag("comment-sort-$text").clip(RoundedCornerShape(50))
        .selectable(selected, role = Role.Tab, onClick = onSelect)
        .heightIn(min = WatchTouchTarget).widthIn(min = WatchTouchTarget)
        .padding(horizontal = watchDp(6)),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = watchSp(WatchType.SECONDARY), lineHeight = watchLineHeight(WatchType.SECONDARY),
            letterSpacing = 0.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) theme.text else theme.subtle)
    }
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
    cached: Boolean = false,
) {
    val labelSize = watchSp(WatchType.CAPTION)
    val density = LocalDensity.current
    val gap = watchDp(5)
    // Same icons, names and active colour as the feed card's actions.
    val commands = listOf(
        DetailCommand(if (liked) R.drawable.official_comment_like_filled else R.drawable.official_comment_like_line,
            Format.commentLikeCount(likes.coerceAtLeast(0)), "点赞帖子", onLike, liked),
        DetailCommand(if (item.favorited) R.drawable.official_favorite_filled else R.drawable.official_favorite_line,
            "", if (item.favorited) "取消收藏" else "收藏", onFavorite, item.favorited),
        DetailCommand(R.drawable.ic_download, "", if (cached) "取消稍后看" else "稍后看", onCache, cached),
        DetailCommand(R.drawable.official_detail_comment, Format.commentLikeCount(item.comments.coerceAtLeast(0)),
            "评论", onComment),
    )
    val measurer = rememberTextMeasurer()
    val widestLabel = commands.maxOf { command ->
        with(density) {
            measurer.measure(AnnotatedString(command.label),
                style = TextStyle(fontSize = labelSize)).size.width.toDp()
        }
    }
    val minimumWidth = maxOf(WatchTouchTarget, watchDp(25) + widestLabel)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 3.dp)
        .testTag("detail-actions")) {
        val columns = if (!LocalHeyboxTheme.current.roundScreen &&
            maxWidth >= minimumWidth * 4 + gap * 3) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            commands.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { command ->
                        DetailCountAction(command.icon, command.label, command.description,
                            command.active, command.onClick, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class DetailCommand(val icon: Int, val label: String, val description: String,
    val onClick: () -> Unit, val active: Boolean = false)

@Composable
internal fun ComposeDetailButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                                 enabled: Boolean = true, primary: Boolean = true, icon: Int? = null) {
    WatchActionButton(text, onClick, modifier.fillMaxWidth(), enabled = enabled, icon = icon,
        primary = primary)
}

/** A tonal pill like the secondary button; the accent marks an active state as in the feed. */
@Composable
private fun DetailCountAction(icon: Int, label: String, description: String, active: Boolean,
                              onClick: () -> Unit, modifier: Modifier) {
    val theme = LocalHeyboxTheme.current
    val shape = RoundedCornerShape(50)
    val color = if (active) theme.accent else theme.text
    Row(modifier.clip(shape).background(theme.panelElevated).border(1.dp, theme.hairline, shape)
        .clickable(onClick = onClick).heightIn(min = watchTouchDp(36))
        .padding(horizontal = watchDp(3), vertical = watchDp(5)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), description, tint = color,
            modifier = Modifier.size(watchDp(WatchIcon.MEDIUM)))
        if (label.isNotBlank()) {
            Spacer(Modifier.width(watchDp(3)))
            Text(label, fontSize = watchSp(WatchType.CAPTION), color = color, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
