package com.ronan.heyboxlite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal fun composeCommentText(comment: JSONObject): String = RichContent.commentText(
    comment.optString("text"), comment.optString("content"), comment.optString("html"),
    comment.optString("description"), comment.optString("desc_extra"),
    comment.optString("rich_text"), comment.optString("hb_rich_texts"),
)

internal fun composeCommentMeta(comment: JSONObject, created: Long): String {
    val time = if (created > 0L) Format.relativeTime(created) else ""
    val location = CommentData.commentLocation(comment)
    return if (time.isBlank()) location else if (location.isBlank()) time else "$time · $location"
}

internal fun composeCommentUserId(user: JSONObject?): String = Json.first(
    user?.optString("userid"), user?.optString("user_id"), user?.optString("heybox_id"),
    user?.optString("heyboxid"), user?.optString("uid"), user?.optString("account_id"),
    user?.optString("id"),
)

internal fun composeCommentIsPostAuthor(item: FeedItem, user: JSONObject?, author: String): Boolean {
    val userId = composeCommentUserId(user)
    if (item.authorId.isNotBlank() && userId.isNotBlank()) return item.authorId == userId
    return item.author.isNotBlank() && item.author == author
}

internal fun composeCommentExpansionLabel(total: Int, shown: Int, hasMore: Boolean): String =
    CommentReplyPaging.expansionLabel(total, shown, hasMore)

internal fun composeCommentNextVisibleCount(total: Int, shown: Int, hasMore: Boolean): Int =
    shown + CommentReplyPaging.nextCount(total, shown, hasMore)

internal fun composeCommentDoubleTapReplyEnabled(context: Context): Boolean =
    (context as? MainActivity)?.session?.doubleTapCommentReply() ?: true

internal fun Modifier.composeCommentGestures(
    context: Context,
    doubleTapReplyEnabled: Boolean,
    copyText: String,
    onReply: () -> Unit,
): Modifier = pointerInput(doubleTapReplyEnabled, copyText) {
    detectTapGestures(
        onLongPress = { copyComposeComment(context, copyText) },
        onDoubleTap = { if (doubleTapReplyEnabled) onReply() },
    )
}

internal fun copyComposeComment(context: Context, source: String) {
    val value = RichInlineRenderer.plainText(source).trim()
    if (value.isEmpty()) return
    val controller = (context as? MainActivity)?.commentController
    if (controller != null) {
        controller.copy(value)
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("评论", value))
    Toast.makeText(context, "评论已复制", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun ComposeCommentAuthorBadge() {
    val theme = LocalHeyboxTheme.current
    Box(Modifier.clip(RoundedCornerShape(3.dp))
        .background(theme.accent.copy(alpha = if (theme.dark) 0.2f else 0.12f))
        .padding(horizontal = 4.dp * theme.uiScale), contentAlignment = Alignment.Center) {
        Text("作者", color = theme.accent, fontSize = 8.sp * theme.textScale,
            lineHeight = 10.sp * theme.textScale)
    }
}

@Composable
internal fun ComposeCommentLevelBadge(level: Int) {
    if (level <= 0) return
    val theme = LocalHeyboxTheme.current
    val color = CommentData.levelBadgeColor(level).asComposeColor()
    Box(Modifier.clip(RoundedCornerShape(4.dp))
        .background(color.copy(alpha = if (theme.dark) 0.28f else 0.14f))
        .padding(horizontal = 4.dp * theme.uiScale), contentAlignment = Alignment.Center) {
        Text("Lv.$level", color = color, fontSize = 8.sp * theme.textScale,
            lineHeight = 10.sp * theme.textScale)
    }
}

@Composable
internal fun ComposeCommentReplyText(
    author: String,
    target: String,
    text: String,
    cy: Boolean,
    meta: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalHeyboxTheme.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = theme.text)) { append(author.ifBlank { "匿名用户" }) }
            if (target.isNotBlank()) withStyle(SpanStyle(color = theme.muted)) { append(" 回复 $target") }
        }, fontSize = 11.sp * theme.textScale, lineHeight = 16.sp * theme.textScale)
        ComposeRichText(text, theme.dark, theme.text, theme.link,
            fontSize = 11.sp * theme.textScale, lineHeight = 17.sp * theme.textScale, cy = cy)
        if (meta.isNotBlank()) Text(meta, color = theme.muted, fontSize = 9.sp * theme.textScale)
    }
}

@Composable
internal fun ComposeCommentImageGrid(
    images: List<CommentData.CommentImage>,
    media: ComposeMediaSettings,
    reply: Boolean,
    onOpenImage: (String) -> Unit,
    requests: ComposeMediaImageRequests = ExistingComposeMediaImageRequests,
) {
    if (images.isEmpty() || !media.enabled) return
    val theme = LocalHeyboxTheme.current
    val preferredSize = (if (theme.roundScreen) if (reply) 44.dp else 48.dp else if (reply) 48.dp else 54.dp) * theme.uiScale
    val gap = 5.dp * theme.uiScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp * theme.uiScale)) {
        val size = minOf(preferredSize, maxWidth)
        val columns = ((maxWidth + gap) / (size + gap)).toInt().coerceIn(1, if (theme.roundScreen) 2 else 3)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            images.chunked(columns).forEach { rowImages ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowImages.forEach { image -> ComposeCommentThumbnail(image, media, size, onOpenImage, requests) }
                    for (index in rowImages.size until columns) Spacer(Modifier.size(size))
                }
            }
        }
    }
}

@Composable
private fun ComposeCommentThumbnail(
    image: CommentData.CommentImage,
    media: ComposeMediaSettings,
    size: Dp,
    onOpenImage: (String) -> Unit,
    requests: ComposeMediaImageRequests,
) {
    val theme = LocalHeyboxTheme.current
    val previewUrl = image.previewUrl.ifBlank { image.originalUrl }
    val originalUrl = image.originalUrl.ifBlank { previewUrl }
    var failed by remember(previewUrl, originalUrl) { mutableStateOf(false) }
    var retry by remember(previewUrl, originalUrl) { mutableStateOf(0) }
    Box(Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(theme.panelElevated)
        .clickable(enabled = originalUrl.isNotBlank()) { onOpenImage(originalUrl) },
        contentAlignment = Alignment.Center) {
        if (previewUrl.isBlank()) {
            Text("图片", color = theme.muted, fontSize = 9.sp)
        } else {
            ComposeMediaImage(
                url = previewUrl,
                settings = media,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "打开图片",
                animated = image.animated,
                animatedUrl = originalUrl,
                retry = retry,
                ensureTouchTarget = false,
                requests = requests,
                onLoaded = { failed = !it },
            )
            if (failed) Box(Modifier.fillMaxSize().clickable { failed = false; retry++ },
                contentAlignment = Alignment.Center) {
                Text("重试", color = theme.accent, fontSize = 9.sp)
            }
        }
    }
}
