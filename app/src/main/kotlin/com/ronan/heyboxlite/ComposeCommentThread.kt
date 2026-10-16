package com.ronan.heyboxlite

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
internal fun ComposeDetailThread(
    group: JSONObject,
    item: FeedItem,
    roundScreen: Boolean,
    media: ComposeMediaSettings,
    onAction: ComposeDetailActionCallback,
    onOpenUser: (String, String, String) -> Unit = { _, _, _ -> },
    replyState: ComposeReplyLoadState? = null,
    doubleTapReplyEnabled: Boolean = true,
    onCopyComment: ((String) -> Unit)? = null,
) {
    val theme = LocalHeyboxTheme.current
    val thread = remember(group) { ComposeCommentThreadData.from(group) }
    val rootId = thread.rootId
    val replies = thread.replies
    // Keep only the user's display budget locally. Request progress belongs to the host.
    var visibleLimit by rememberSaveable(rootId) { mutableIntStateOf(thread.initialVisibleCount) }
    val shown = minOf(visibleLimit, replies.size)
    val remoteMore = replyState?.hasMore ?: (thread.expected > replies.size)
    val total = if (remoteMore) maxOf(thread.expected, replies.size) else replies.size
    val loading = replyState?.loading == true
    val error = replyState?.error.orEmpty()
    val expansion = composeCommentExpansionLabel(total, shown, remoteMore)
    val replyKeys = remember(replies) { composeCommentKeys(replies) }
    val pinned = CommentData.isPinnedThread(group)

    fun requestReplies(retry: Boolean = false) {
        if (loading) return
        val next = composeCommentNextVisibleCount(total, shown, remoteMore || retry)
        if (!retry && shown < replies.size) {
            visibleLimit = minOf(next, replies.size)
        } else if (remoteMore || retry) {
            visibleLimit = next
            onAction(ComposeDetailAction.LoadReplies(thread.root))
        }
    }

    Box(Modifier.fillMaxWidth().testTag("comment-thread-$rootId")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp * theme.uiScale,
                vertical = 5.dp * theme.uiScale)
                .padding(top = if (pinned) 20.dp * theme.uiScale else 0.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp * theme.uiScale),
        ) {
            ComposeDetailComment(thread.root, item, rootId, false, roundScreen, media,
                onAction, onOpenUser, doubleTapReplyEnabled, onCopyComment, thread.cy)
            if (replies.isNotEmpty() || remoteMore || loading || error.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("comment-replies-$rootId")
                        .clip(RoundedCornerShape(6.dp)).background(theme.panel)
                        .padding(horizontal = 5.dp * theme.uiScale, vertical = 3.dp * theme.uiScale),
                    verticalArrangement = Arrangement.spacedBy(4.dp * theme.uiScale),
                ) {
                    replies.take(shown).forEachIndexed { index, reply ->
                        key(replyKeys[index]) {
                            ComposeDetailComment(reply, item, rootId, true, roundScreen, media,
                                onAction, onOpenUser, doubleTapReplyEnabled, onCopyComment)
                        }
                    }
                    when {
                        loading -> Text("加载回复中…", fontSize = 11.sp * theme.textScale,
                            color = theme.muted, letterSpacing = 0.sp, modifier = Modifier.padding(vertical = 7.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite })
                        error.isNotBlank() -> {
                            Text(error, fontSize = 9.sp * theme.textScale, color = theme.muted,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            ComposeCommentReplyControl("回复加载失败，重试", R.drawable.ic_expand) {
                                requestReplies(retry = true)
                            }
                        }
                        expansion.isNotBlank() -> ComposeCommentReplyControl(expansion, R.drawable.ic_expand) {
                            requestReplies()
                        }
                    }
                    if (shown > thread.initialVisibleCount) {
                        ComposeCommentReplyControl("收起回复", R.drawable.ic_collapse) {
                            visibleLimit = thread.initialVisibleCount
                        }
                    }
                }
            }
            HorizontalDivider(color = theme.hairline.copy(alpha = 0.5f))
        }
        if (pinned) ComposeCommentPinnedCorner(Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun ComposeDetailComment(
    comment: JSONObject,
    item: FeedItem,
    rootId: String,
    reply: Boolean,
    roundScreen: Boolean,
    media: ComposeMediaSettings,
    onAction: ComposeDetailActionCallback,
    onOpenUser: (String, String, String) -> Unit,
    doubleTapReplyEnabled: Boolean,
    onCopyComment: ((String) -> Unit)?,
    groupCy: Boolean = false,
) {
    val theme = LocalHeyboxTheme.current
    val identity = remember(comment, item.authorId, item.author) { composeCommentIdentity(comment, item) }
    val value = remember(comment) { composeCommentText(comment) }
    val images = remember(comment) { CommentData.commentImages(comment) }
    val cy = groupCy || CommentData.isCyComment(comment)
    val gesture = Modifier.composeCommentGestures(
        context = LocalContext.current,
        doubleTapReplyEnabled = doubleTapReplyEnabled,
        copyText = value,
        onReply = { onAction(ComposeDetailAction.Reply(comment, rootId)) },
        onCopy = onCopyComment,
    )
    val openUser = if (identity.userId.isNotBlank()) {
        { onOpenUser(identity.userId, identity.author, identity.avatar) }
    } else null
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().testTag("comment-${CommentData.commentId(comment)}")
            .then(gesture),
    ) {
        val compactHeader = maxWidth < 180.dp * theme.textScale * LocalDensity.current.fontScale
        val contentInset = if (reply || roundScreen || compactHeader) 0.dp else 32.dp * theme.uiScale
        Column(verticalArrangement = Arrangement.spacedBy(4.dp * theme.uiScale)) {
            if (reply) {
                ComposeCommentReplyText(identity.author, CommentData.replyTarget(comment, rootId),
                    value, cy, identity.meta, Modifier.fillMaxWidth(), identity.postAuthor)
            } else {
                ComposeCommentHeader(identity, comment, media, compactHeader, openUser) {
                    onAction(ComposeDetailAction.LikeComment(comment))
                }
                if (value.isNotBlank() || cy) ComposeInlineText(
                    source = value,
                    darkMode = theme.dark,
                    textColor = theme.text,
                    linkColor = theme.link,
                    modifier = Modifier.fillMaxWidth().padding(start = contentInset, top = 1.dp),
                    fontSize = 12.sp * theme.textScale,
                    lineHeight = 18.sp * theme.textScale,
                    fontWeight = FontWeight.Medium,
                    cy = cy,
                )
            }
            if (images.isNotEmpty() && media.enabled) Column(Modifier.fillMaxWidth().padding(start = contentInset)) {
                ComposeCommentImageGrid(images, media, reply, onOpenImage = { url ->
                    onAction(ComposeDetailAction.OpenCommentImage(comment, url))
                }, onOpenPreview = { url, source ->
                    onAction(ComposeDetailAction.OpenCommentImage(comment, url, source))
                })
            }
        }
    }
}

@Composable
private fun ComposeCommentHeader(
    identity: ComposeCommentIdentity,
    comment: JSONObject,
    media: ComposeMediaSettings,
    compact: Boolean,
    onOpenUser: (() -> Unit)?,
    onLike: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp * theme.uiScale)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            ComposeUserAvatar(identity.avatar, identity.author, media, 26.dp * theme.uiScale, onOpenUser)
            Spacer(Modifier.width(6.dp * theme.uiScale))
            Column(Modifier.weight(1f)) {
                ComposeCommentName(identity.author, identity.postAuthor, identity.level)
                if (!compact) ComposeCommentMetadata(identity.meta)
            }
            if (!compact) {
                Spacer(Modifier.width(3.dp * theme.uiScale))
                ComposeCommentLike(comment, onLike)
            }
        }
        if (compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ComposeCommentMetadata(identity.meta, Modifier.weight(1f))
            Spacer(Modifier.width(3.dp * theme.uiScale))
            ComposeCommentLike(comment, onLike)
        }
    }
}

@Composable
private fun ComposeCommentMetadata(text: String, modifier: Modifier = Modifier) {
    val theme = LocalHeyboxTheme.current
    Text(text, modifier, color = theme.muted, fontSize = 9.sp * theme.textScale,
        lineHeight = 12.sp * theme.textScale, letterSpacing = 0.sp)
}

@Composable
private fun ComposeCommentLike(comment: JSONObject, onClick: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    val liked = CommentData.commentLiked(comment)
    val color = if (liked) theme.text else theme.muted
    Row(
        modifier = Modifier.widthIn(max = 56.dp * theme.uiScale)
            .heightIn(min = 26.dp * theme.uiScale).clip(RoundedCornerShape(5.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "点赞评论"
                stateDescription = if (liked) "已点赞" else "未点赞"
            }.clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp * theme.uiScale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (liked) R.drawable.official_comment_like_filled
            else R.drawable.official_comment_like_line), null,
            tint = color, modifier = Modifier.size(15.dp * theme.uiScale))
        Spacer(Modifier.width(2.dp * theme.uiScale))
        Text(Format.commentLikeCount(CommentData.commentLikes(comment).coerceAtLeast(0)),
            fontSize = 10.sp * theme.textScale, color = color, maxLines = 1,
            overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp)
    }
}

@Composable
private fun ComposeCommentReplyControl(label: String, icon: Int, onClick: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    Row(Modifier.heightIn(min = 26.dp * theme.uiScale).clip(RoundedCornerShape(50))
        .background(theme.panelElevated).testTag("comment-reply-capsule-$label")
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 8.dp * theme.uiScale, vertical = 3.dp * theme.uiScale),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = theme.muted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, color = theme.text, fontSize = 11.sp * theme.textScale,
            lineHeight = 15.sp * theme.textScale, letterSpacing = 0.sp)
    }
}
