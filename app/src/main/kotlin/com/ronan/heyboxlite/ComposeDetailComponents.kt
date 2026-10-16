package com.ronan.heyboxlite

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
        RichContent.Block.QUOTE -> Row(Modifier.fillMaxWidth()) {
            val theme = LocalHeyboxTheme.current
            val typography = composeDetailBodyTypography(block.kind, services?.session)
            Box(Modifier.width(3.dp).height(40.dp).background(theme.hairline))
            SelectionContainer(Modifier.weight(1f)) {
                ComposeInlineText(display, theme.dark, theme.muted, theme.link,
                    Modifier.padding(start = watchDp(8)), fontSize = typography.fontSize,
                    lineHeight = typography.lineHeight)
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
                    ((services?.session?.bodyParagraphSpacing() ?: 9) * theme.uiScale).dp)) {
                    paragraphs.forEach { paragraph ->
                        ComposeInlineText(paragraph, theme.dark,
                            if (block.kind == RichContent.Block.CAPTION) theme.muted else theme.text, theme.link,
                            fontSize = typography.fontSize,
                            fontWeight = typography.fontWeight ?: FontWeight.Normal,
                            lineHeight = typography.lineHeight,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
internal fun composeDetailBodyTypography(kind: Int, session: SessionStore?): TextStyle {
    val theme = LocalHeyboxTheme.current
    val size = when (kind) {
        RichContent.Block.HEADING -> if (theme.roundScreen) 15f else 16f
        RichContent.Block.CAPTION -> 11f
        RichContent.Block.QUOTE -> if (theme.roundScreen) 12.5f else 13.5f
        else -> if (theme.roundScreen) 13f else 14.5f
    }
    val fontSize = (size * theme.textScale * (session?.bodyTextScale() ?: 100) / 100f).sp
    val spacing = (session?.bodyLineSpacing() ?: 122) / 100f
    val multiplier = when (kind) {
        RichContent.Block.HEADING -> 1.16f
        RichContent.Block.CAPTION -> 1.3f
        RichContent.Block.QUOTE -> maxOf(1.14f, spacing)
        else -> maxOf(1.18f, spacing)
    }
    val density = LocalDensity.current
    val extra = with(density) { watchDp(1).roundToPx() }
    val lineHeight = remember(fontSize, density, extra, multiplier) {
        // TextView's old renderer applies line spacing to font metrics, not the nominal sp size.
        val paint = Paint().apply {
            typeface = Typeface.DEFAULT
            textSize = with(density) { fontSize.toPx() }
        }
        with(density) { (paint.getFontMetricsInt(null) * multiplier + extra).roundToInt().toSp() }
    }
    return TextStyle(fontSize = fontSize, lineHeight = lineHeight,
        fontWeight = if (kind == RichContent.Block.HEADING) FontWeight.Bold else FontWeight.Normal)
}

@Composable
internal fun ComposeDetailSortTab(text: String, selected: Boolean, onSelect: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    Box(Modifier.testTag("comment-sort-$text").clip(RoundedCornerShape(3.dp))
        .selectable(selected, role = Role.Tab, onClick = onSelect)
        .heightIn(min = 24.dp).padding(horizontal = watchDp(4), vertical = watchDp(3)),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = watchSp(10.5f), lineHeight = watchSp(14f), letterSpacing = 0.sp,
            maxLines = 1, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
