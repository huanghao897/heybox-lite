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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
internal fun Modifier.composeCommentGestures(
    context: Context,
    doubleTapReplyEnabled: Boolean,
    copyText: String,
    onReply: () -> Unit,
    onCopy: ((String) -> Unit)? = null,
): Modifier {
    val currentReply by rememberUpdatedState(onReply)
    val currentCopy by rememberUpdatedState(onCopy)
    val currentContext by rememberUpdatedState(context)
    val plain = remember(copyText) { RichInlineRenderer.plainText(copyText).trim() }
    val copy = {
        if (plain.isNotEmpty()) {
            currentCopy?.invoke(plain) ?: copyComposeComment(currentContext, plain)
            true
        } else false
    }
    return semantics {
        customActions = buildList {
            add(CustomAccessibilityAction("回复评论") { currentReply(); true })
            if (plain.isNotEmpty()) add(CustomAccessibilityAction("复制评论", copy))
        }
        if (plain.isNotEmpty()) onLongClick("复制评论", copy)
    }.pointerInput(doubleTapReplyEnabled, plain) {
        detectTapGestures(
            onLongPress = { copy() },
            onDoubleTap = if (doubleTapReplyEnabled) { { _ -> currentReply() } } else null,
        )
    }
}

internal fun copyComposeComment(context: Context, source: String) {
    val value = RichInlineRenderer.plainText(source).trim()
    if (value.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("评论", value))
    Toast.makeText(context, "评论已复制", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposeCommentName(
    author: String,
    postAuthor: Boolean,
    level: Int,
    modifier: Modifier = Modifier,
) {
    val theme = LocalHeyboxTheme.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp * theme.uiScale),
            verticalArrangement = Arrangement.spacedBy(2.dp * theme.uiScale),
            modifier = Modifier.fillMaxWidth()) {
            Text(author.ifBlank { "匿名用户" }, modifier = Modifier.widthIn(max = availableWidth),
                color = theme.text, fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp * theme.textScale, lineHeight = 16.sp * theme.textScale,
                letterSpacing = 0.sp)
            if (postAuthor) ComposeCommentAuthorBadge()
            if (level > 0) ComposeCommentLevelBadge(level)
        }
    }
}

@Composable
internal fun ComposeCommentAuthorBadge() {
    val theme = LocalHeyboxTheme.current
    Box(Modifier.clip(RoundedCornerShape(3.dp))
        .background(theme.panelElevated)
        .padding(horizontal = 4.dp * theme.uiScale), contentAlignment = Alignment.Center) {
        Text("作者", color = theme.text, fontSize = 8.sp * theme.textScale,
            lineHeight = 12.sp * theme.textScale, maxLines = 1, overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp)
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
            lineHeight = 12.sp * theme.textScale, maxLines = 1, overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp)
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
    postAuthor: Boolean = false,
) {
    val theme = LocalHeyboxTheme.current
    val prefix = remember(author, target, postAuthor, theme) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = theme.text, fontWeight = FontWeight.SemiBold)) {
                append(author.ifBlank { "匿名用户" })
            }
            if (postAuthor) withStyle(SpanStyle(color = theme.text, background = theme.panelElevated,
                fontSize = 8.sp * theme.textScale)) { append(" 作者") }
            withStyle(SpanStyle(color = theme.muted)) {
                if (target.isNotBlank()) append(" 回复 $target")
                append("：")
            }
        }
    }
    val suffix = remember(meta, theme) {
        buildAnnotatedString {
            if (meta.isNotBlank()) withStyle(SpanStyle(color = theme.muted,
                fontSize = 9.sp * theme.textScale)) { append("  $meta") }
        }
    }
    ComposeInlineText(text, theme.dark, theme.text, theme.link,
        modifier = modifier.fillMaxWidth(), fontSize = 12.5.sp * theme.textScale,
        lineHeight = 18.sp * theme.textScale, cy = cy, prefix = prefix, suffix = suffix)
}

@Composable
internal fun ComposeCommentImageGrid(
    images: List<CommentData.CommentImage>,
    media: ComposeMediaSettings,
    reply: Boolean,
    onOpenImage: (String) -> Unit,
    requests: ComposeMediaImageRequests = ExistingComposeMediaImageRequests,
    onOpenPreview: ((String, android.widget.ImageView) -> Unit)? = null,
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
                    rowImages.forEach { image ->
                        ComposeCommentThumbnail(image, media, size, onOpenImage, requests, onOpenPreview)
                    }
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
    onOpenPreview: ((String, android.widget.ImageView) -> Unit)?,
) {
    val theme = LocalHeyboxTheme.current
    val previewUrl = image.previewUrl.ifBlank { image.originalUrl }
    val originalUrl = image.originalUrl.ifBlank { previewUrl }
    var failed by remember(previewUrl, originalUrl) { mutableStateOf(false) }
    var retry by remember(previewUrl, originalUrl) { mutableStateOf(0) }
    Box(Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(theme.panelElevated),
        contentAlignment = Alignment.Center) {
        if (previewUrl.isBlank()) {
            Text("图片", color = theme.muted, fontSize = 9.sp, letterSpacing = 0.sp)
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
                onClick = { onOpenImage(originalUrl) },
                onPreviewClick = onOpenPreview?.let { open -> { view -> open(originalUrl, view) } },
            )
            if (failed) Box(Modifier.fillMaxSize().clickable { failed = false; retry++ },
                contentAlignment = Alignment.Center) {
                Text("重试", color = theme.accent, fontSize = 9.sp, letterSpacing = 0.sp)
            }
            if (image.animated) Text("GIF", color = theme.text,
                fontSize = 7.sp, lineHeight = 9.sp, letterSpacing = 0.sp,
                modifier = Modifier.align(Alignment.BottomEnd).background(theme.panel)
                    .padding(horizontal = 3.dp, vertical = 1.dp))
        }
    }
}
