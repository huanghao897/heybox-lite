package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun ComposeDetailCommentsPage(
    item: FeedItem,
    comments: List<JSONObject>,
    loading: Boolean,
    media: ComposeMediaSettings,
    services: ComposeServices?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    active: Boolean,
    replyStates: Map<String, ComposeReplyLoadState>,
    error: String,
    onRetry: () -> Unit,
    onCommentAction: ComposeDetailActionCallback,
    onOpenUser: (String, String, String) -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    var latest by rememberSaveable(item.id) { mutableStateOf(false) }
    val ordered = remember(comments, latest) { composeOrderedComments(comments, latest) }
    val commentKeys = remember(ordered) { composeCommentKeys(ordered) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("compose-detail-comments-list"),
        state = listState,
        userScrollEnabled = active,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(watchDp(10)),
    ) {
        composeDetailCommentItems(item, ordered, commentKeys, latest, { latest = it }, loading,
            theme, media, services, replyStates, onCommentAction, onOpenUser, showDivider = false)
        if (error.isNotBlank()) item(key = "comments-error") {
            Column(verticalArrangement = Arrangement.spacedBy(watchDp(6))) {
                Text(error, color = theme.muted, fontSize = watchSp(11f))
                ComposeDetailButton("重试", onRetry, icon = R.drawable.il_refresh)
            }
        }
        item(key = "write-comment", contentType = "actions") {
            ComposeDetailButton("写评论", { onCommentAction(ComposeDetailAction.WriteComment(item)) },
                icon = R.drawable.official_detail_comment)
        }
    }
}

/** Body and dedicated comments retain the same ordering, callbacks and reply renderer. */
internal fun LazyListScope.composeDetailCommentItems(
    item: FeedItem,
    ordered: List<JSONObject>,
    commentKeys: List<String>,
    latest: Boolean,
    onLatest: (Boolean) -> Unit,
    loading: Boolean,
    theme: ComposeThemeState,
    media: ComposeMediaSettings,
    services: ComposeServices?,
    replyStates: Map<String, ComposeReplyLoadState>,
    onCommentAction: ComposeDetailActionCallback,
    onOpenUser: (String, String, String) -> Unit,
    showDivider: Boolean,
) {
    item(key = "comment-heading", contentType = "comment-heading") {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val inset = if (showDivider) 0.dp else watchLeadingHeadingHorizontal(maxWidth, theme)
            Column(Modifier.padding(horizontal = inset), verticalArrangement = Arrangement.spacedBy(watchDp(8))) {
                if (showDivider) HorizontalDivider(color = theme.hairline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("评论 ${maxOf(item.comments, ordered.size)}", Modifier.weight(1f), fontSize = watchSp(13f),
                        color = theme.text, fontWeight = FontWeight.Bold)
                    Row(Modifier.testTag("comment-sort-tabs"), verticalAlignment = Alignment.CenterVertically) {
                        ComposeDetailSortTab("热门", !latest) { onLatest(false) }
                        Box(Modifier.width(0.5.dp).height(watchDp(9)).background(theme.hairline))
                        ComposeDetailSortTab("最新", latest) { onLatest(true) }
                    }
                }
            }
        }
    }
    if (loading && ordered.isEmpty()) item(key = "comments-loading") {
        Box(Modifier.fillMaxWidth().padding(vertical = watchDp(12)), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(watchDp(22)), strokeWidth = 2.dp)
        }
    }
    itemsIndexed(ordered, key = { index, _ -> commentKeys[index] },
        contentType = { _, _ -> "comment-thread" }) { _, group ->
        ComposeDetailThread(group, item, theme.roundScreen, media, onCommentAction, onOpenUser,
            replyState = replyStates[ComposeCommentThreadData.from(group).rootId],
            doubleTapReplyEnabled = services?.session?.doubleTapCommentReply() ?: true)
    }
    if (!loading && ordered.isEmpty()) item(key = "empty-comments") {
        Text("暂无评论", color = theme.muted, fontSize = watchSp(12f),
            modifier = Modifier.padding(vertical = watchDp(8)))
    }
}
