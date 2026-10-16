package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun ComposeDetailBody(
    item: FeedItem,
    content: List<RichContent.Block>,
    videos: List<VideoData>,
    comments: List<JSONObject>,
    loading: Boolean,
    media: ComposeMediaSettings,
    services: ComposeServices?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    active: Boolean,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (VideoData) -> Unit,
    onCommentAction: ComposeDetailActionCallback,
    onOpenUser: (String, String, String) -> Unit,
    link: JSONObject?,
    replyStates: Map<String, ComposeReplyLoadState>,
    error: String,
    onRetry: () -> Unit,
    actionRevision: Int,
    notice: String,
    useImagePager: Boolean,
    onVideoSettings: () -> Unit,
    onOpenImagePreview: ((String, android.widget.ImageView) -> Unit)?,
) {
    val theme = LocalHeyboxTheme.current
    var latest by rememberSaveable(item.id) { mutableStateOf(false) }
    val ordered = remember(comments, latest) { composeOrderedComments(comments, latest) }
    val commentKeys = remember(ordered) { composeCommentKeys(ordered) }
    val bodyRows = remember(content, useImagePager) { composeDetailBodyRows(content, useImagePager) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("compose-detail-list"),
        userScrollEnabled = active,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(watchDp(10)),
        state = listState,
    ) {
        item(key = "header", contentType = "header") {
            ComposeDetailHeader(item, media, onOpenUser, link,
                onFollow = { onCommentAction(ComposeDetailAction.FollowAuthor(item)) },
                showFollow = services?.session?.userId() != item.authorId,
                actionRevision = actionRevision)
        }
        if (notice.isNotBlank()) item(key = "detail-notice") {
            Text(notice, color = theme.muted, fontSize = watchSp(11f), lineHeight = watchSp(16f))
        }
        if (loading) item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        itemsIndexed(bodyRows, key = { _, row -> "body-${row.index}" },
            contentType = { _, row -> row.blocks.first().kind }) { _, row ->
            if (row.blocks.size > 1) ComposeDetailImagePager(row.blocks.map { it.value }, media,
                onOpenImage, onOpenPreview = onOpenImagePreview)
            else ComposeDetailBlock(row.blocks.first(), media, services, onOpenImage, onOpenImagePreview)
        }
        itemsIndexed(videos, key = { index, _ -> "video-$index" }) { _, video ->
            ComposeVideoCard(video, media, onVideoSettings = onVideoSettings, onOpenVideo = onOpenVideo)
        }
        if (!loading && content.isEmpty()) item(key = "fallback-body") {
            val fallback = composeDetailFallbackText(item.description)
            val typography = composeDetailBodyTypography(RichContent.Block.TEXT, services?.session)
            Column(verticalArrangement = Arrangement.spacedBy(watchDp(9))) {
                if (fallback.isNotBlank()) ComposeInlineText(fallback, theme.dark, theme.text, theme.link,
                    fontSize = typography.fontSize, lineHeight = typography.lineHeight)
                if (media.enabled) item.images.forEach { url ->
                    ComposeDetailImage(url, media, onOpenImage, onOpenPreview = onOpenImagePreview)
                }
            }
        }
        if (!loading && content.isEmpty() && videos.isEmpty()
            && item.description.isBlank() && item.images.isEmpty()) item(key = "empty-body") {
            Text("正文为空", color = theme.muted, fontSize = watchSp(12f))
        }
        if (error.isNotBlank()) item(key = "detail-error") {
            Column(verticalArrangement = Arrangement.spacedBy(watchDp(6))) {
                Text(error, color = theme.muted, fontSize = watchSp(11f))
                ComposeDetailButton("重试", onRetry, icon = R.drawable.il_refresh)
            }
        }
        item(key = "post-actions", contentType = "actions") {
            ComposeDetailDock(item, item.liked, item.likes, 0.dp,
                onLike = { onCommentAction(ComposeDetailAction.LikePost(item)) },
                onComment = { onCommentAction(ComposeDetailAction.WriteComment(item)) },
                onFavorite = { onCommentAction(ComposeDetailAction.FavoritePost(item)) },
                onCache = { onCommentAction(ComposeDetailAction.CachePost(item)) },
                actionRevision = actionRevision)
        }
        composeDetailCommentItems(item, ordered, commentKeys, latest, { latest = it }, loading,
            theme, media, services, replyStates, onCommentAction, onOpenUser, showDivider = true)
    }
}
