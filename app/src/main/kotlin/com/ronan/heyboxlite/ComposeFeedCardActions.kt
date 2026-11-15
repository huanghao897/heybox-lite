package com.ronan.heyboxlite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun composeFeedActionRows(widths: List<Float>, availableWidth: Float): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    var rowWidth = 0f
    widths.forEachIndexed { index, width ->
        if (rows.isEmpty() || (rowWidth + width > availableWidth && rows.last().isNotEmpty())) {
            rows.add(mutableListOf())
            rowWidth = 0f
        }
        rows.last() += index
        rowWidth += width
    }
    return rows
}

/** Section identity yields a separate row before it can squeeze the action targets. */
@Composable
internal fun ComposeFeedCardActions(
    item: FeedItem,
    state: ComposeFeedActionState,
    favorite: Boolean,
    cached: Boolean,
    theme: ComposeThemeState,
    noImage: Boolean,
    imageLoader: ComposeImageLoader,
    showSecondaryActions: Boolean,
    onAction: ((FeedItem, FeedAction) -> Unit)?,
) {
    val commands = buildList {
        add(FeedCardCommand(R.drawable.official_comment_like_filled,
            Format.commentLikeCount(state.likes), if (state.liked) "取消点赞" else "点赞",
            state.liked, FeedAction.LIKE))
        if (showSecondaryActions) {
            add(FeedCardCommand(if (favorite) R.drawable.official_favorite_filled
                else R.drawable.official_favorite_line, "", if (favorite) "取消收藏" else "收藏",
                favorite, FeedAction.FAVORITE))
            add(FeedCardCommand(R.drawable.ic_download, "", if (cached) "取消稍后看" else "稍后看",
                cached, FeedAction.CACHE))
        }
        add(FeedCardCommand(R.drawable.official_detail_comment,
            Format.commentLikeCount(item.comments), "评论", false, FeedAction.COMMENT))
    }
    val scale = theme.uiScale
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val fontSize = (WatchType.CAPTION * theme.textScale).sp
    val style = TextStyle(fontSize = fontSize, letterSpacing = 0.sp)
    val commandWidths = commands.map { command ->
        val labelWidth = with(density) {
            measurer.measure(AnnotatedString(command.label), style = style).size.width.toDp()
        }
        maxOf(WatchTouchTarget, (26 * scale).dp + labelWidth)
    }
    val actionsWidth = commandWidths.fold(0.dp) { total, width -> total + width }
    val textWidth = with(density) { fontSize.toDp() } * 2
    val chipPadding = maxOf(6 * scale, 3 * theme.textScale * density.fontScale).dp * 2
    val chipIcon = if (noImage) 0.dp else (12 * scale).dp.coerceIn(10.dp, 14.dp) + (3 * scale).dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = (2 * scale).dp)) {
        val separateSection = item.topicName.isNotBlank() &&
            maxWidth < actionsWidth + textWidth + chipPadding + chipIcon + (4 * scale).dp
        val rows = composeFeedActionRows(commandWidths.map { it.value }, maxWidth.value)
        Column(Modifier.fillMaxWidth()) {
            if (separateSection) ComposeSectionChip(item.topicName, item.topicIcon, theme, noImage,
                imageLoader = imageLoader)
            rows.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (rowIndex == 0 && item.topicName.isNotBlank() && !separateSection) {
                        Box(Modifier.weight(1f).padding(end = (4 * scale).dp)) {
                            ComposeSectionChip(item.topicName, item.topicIcon, theme, noImage,
                                imageLoader = imageLoader)
                        }
                    } else Spacer(Modifier.weight(1f))
                    row.forEach { index ->
                        val command = commands[index]
                        FeedCardAction(command, theme) { onAction?.invoke(item, command.action) }
                    }
                }
            }
        }
    }
}

private data class FeedCardCommand(
    val icon: Int,
    val label: String,
    val description: String,
    val active: Boolean,
    val action: FeedAction,
)

@Composable
private fun FeedCardAction(command: FeedCardCommand, theme: ComposeThemeState, onClick: () -> Unit) {
    val color = if (command.active) theme.accent else theme.muted
    Row(Modifier.heightIn(min = WatchTouchTarget).widthIn(min = WatchTouchTarget)
        .clip(RoundedCornerShape((WatchRadius.MEDIUM * theme.uiScale).dp))
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = command.description }
        .padding(horizontal = (4 * theme.uiScale).dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(command.icon), null, tint = color,
            modifier = Modifier.size((WatchIcon.MEDIUM * theme.uiScale).dp))
        if (command.label.isNotEmpty()) Text(command.label, color = color,
            fontSize = (WatchType.CAPTION * theme.textScale).sp,
            lineHeight = (15 * theme.textScale).sp, letterSpacing = 0.sp, maxLines = 1,
            modifier = Modifier.padding(start = (2 * theme.uiScale).dp))
    }
}
