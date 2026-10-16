package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject

internal typealias ComposeDetailActionCallback = (ComposeDetailAction) -> Unit

internal fun composeDetailFallbackText(source: String): String = RichContent.parse(source, null)
    .filter { RichContentSupport.isReadableBlock(it) }
    .joinToString("\n") { ArticleText.stripMarkdownEmphasis(it.value) }

/** Host routes these events through its existing Java controllers. */
internal sealed class ComposeDetailAction {
    data class LikePost(val item: FeedItem) : ComposeDetailAction()
    data class WriteComment(val item: FeedItem) : ComposeDetailAction()
    data class FavoritePost(val item: FeedItem) : ComposeDetailAction()
    data class CachePost(val item: FeedItem) : ComposeDetailAction()
    data class FollowAuthor(val item: FeedItem) : ComposeDetailAction()
    data class LikeComment(val comment: JSONObject) : ComposeDetailAction()
    data class Reply(val comment: JSONObject, val rootId: String = "") : ComposeDetailAction()
    data class LoadReplies(val root: JSONObject) : ComposeDetailAction()
    data class OpenCommentImage(val comment: JSONObject, val url: String) : ComposeDetailAction()
}

@Composable
internal fun ComposeDetailScreen(
    item: FeedItem,
    content: List<RichContent.Block>,
    videos: List<VideoData> = item.videos,
    comments: List<JSONObject>,
    loading: Boolean,
    services: ComposeServices,
    onBack: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (VideoData) -> Unit,
    onCommentAction: ComposeDetailActionCallback,
    onOpenUser: (String, String, String) -> Unit = { _, _, _ -> },
    listState: LazyListState = rememberLazyListState(),
    link: JSONObject? = null,
    active: Boolean = true,
    replyStates: Map<String, ComposeReplyLoadState> = emptyMap(),
    error: String = "",
    onRetry: () -> Unit = {},
    entryKey: Long = 0,
    actionRevision: Int = 0,
    rotaryInput: ComposeFeedRotaryInput? = null,
    notice: String = "",
    useImagePager: Boolean = false,
    onSettingsChanged: () -> Unit = {},
) {
    HeyboxComposeTheme(services.theme) {
        val preview = LocalInspectionMode.current
        val theme = LocalHeyboxTheme.current
        val settings = if (preview) ComposeMediaSettings(enabled = false) else ComposeMediaSettings(
            enabled = !services.session.noImage(),
            playGif = services.session.playGif(),
            imageTargetPx = if (theme.roundScreen) 720 else 900,
            gameCardNoImage = services.session.gameCardNoImage(),
        )
        if (active && rotaryInput != null) ObserveFeedRotaryInput(rotaryInput, listState)
        var restored by remember(entryKey) { mutableStateOf(false) }
        var videoSettings by remember(entryKey) { mutableStateOf(false) }
        LaunchedEffect(entryKey, loading, active) {
            if (restored) return@LaunchedEffect
            if (!active) { restored = true; return@LaunchedEffect }
            if (loading) return@LaunchedEffect
            if (services.session.rememberDetailScroll() && listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0) {
                val saved = ComposeDetailPosition.restore(services.activity, services.session.userId(), item.id)
                if (saved.index > 0 || saved.offset > 0) listState.scrollToItem(saved.index, saved.offset)
            }
            restored = true
        }
        ComposeDetailLayout(item, content, videos, comments, loading, settings, onBack, onOpenImage,
            onOpenVideo, onCommentAction, listState, onOpenUser, services, link, active, replyStates,
            error, onRetry, actionRevision, visible = !active || loading || restored, notice = notice,
            useImagePager = useImagePager, onVideoSettings = { videoSettings = true })
        if (active && videoSettings) Dialog(onDismissRequest = { videoSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            ComposeVideoSettingsScreen(services, { videoSettings = false }, onSettingsChanged)
        }
    }
}

@Composable
private fun ComposeDetailLayout(
    item: FeedItem,
    content: List<RichContent.Block>,
    videos: List<VideoData>,
    comments: List<JSONObject>,
    loading: Boolean,
    media: ComposeMediaSettings,
    onBack: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (VideoData) -> Unit,
    onCommentAction: ComposeDetailActionCallback,
    listState: LazyListState = rememberLazyListState(),
    onOpenUser: (String, String, String) -> Unit = { _, _, _ -> },
    services: ComposeServices? = null,
    link: JSONObject? = null,
    active: Boolean = true,
    replyStates: Map<String, ComposeReplyLoadState> = emptyMap(),
    error: String = "",
    onRetry: () -> Unit = {},
    actionRevision: Int = 0,
    visible: Boolean = true,
    notice: String = "",
    useImagePager: Boolean = false,
    onVideoSettings: () -> Unit = {},
) {
    val theme = LocalHeyboxTheme.current
    val roundScreen = theme.roundScreen
    var latest by remember(item.id) { mutableStateOf(false) }
    val ordered = remember(comments, latest) { composeOrderedComments(comments, latest) }
    val commentKeys = remember(ordered) { composeCommentKeys(ordered) }
    val bodyRows = remember(content, useImagePager) { composeDetailBodyRows(content, useImagePager) }
    val inset = 12.dp * theme.uiScale
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(theme.background)) {
        val headerTop = if (roundScreen) maxWidth * 0.08f else watchDp(5)
        val headerStart = if (roundScreen) maxWidth * 0.16f else watchDp(10)
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("compose-detail-list").graphicsLayer { alpha = if (visible) 1f else 0f },
            userScrollEnabled = active,
            contentPadding = PaddingValues(start = inset, end = inset,
                top = headerTop + watchDp(42), bottom = watchListEndPadding()),
            verticalArrangement = Arrangement.spacedBy(watchDp(10)),
            state = listState,
        ) {
            item(key = "header", contentType = "header") {
                Box(Modifier.padding(horizontal = if (roundScreen) watchDp(18) else 0.dp)) {
                    ComposeDetailHeader(item, media, onOpenUser, link,
                        onFollow = { onCommentAction(ComposeDetailAction.FollowAuthor(item)) },
                        showFollow = services?.session?.userId() != item.authorId,
                        actionRevision = actionRevision)
                }
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
                if (row.blocks.size > 1) ComposeDetailImagePager(row.blocks.map { it.value }, media, onOpenImage)
                else ComposeDetailBlock(row.blocks.first(), media, services, onOpenImage)
            }
            itemsIndexed(videos, key = { index, _ -> "video-$index" }) { _, video ->
                ComposeVideoCard(video, media, onVideoSettings = onVideoSettings, onOpenVideo = onOpenVideo)
            }
            if (!loading && content.isEmpty()) item(key = "fallback-body") {
                val fallback = composeDetailFallbackText(item.description)
                val bodyScale = theme.textScale * (services?.session?.bodyTextScale() ?: 100) / 100f
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (fallback.isNotBlank()) ComposeInlineText(fallback, theme.dark, theme.text, theme.link,
                        fontSize = 13.sp * bodyScale, lineHeight = 20.sp * bodyScale)
                    if (media.enabled) item.images.forEach { url ->
                        ComposeDetailImage(url, media, onOpenImage)
                    }
                }
            }
            if (!loading && content.isEmpty() && videos.isEmpty()
                && item.description.isBlank() && item.images.isEmpty()) item(key = "empty-body") {
                Text("正文为空", color = theme.muted, fontSize = 12.sp)
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
            item(key = "comment-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = theme.hairline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("评论 ${maxOf(item.comments, ordered.size)}", Modifier.weight(1f), fontSize = watchSp(13f),
                            color = theme.text, fontWeight = FontWeight.Bold)
                        Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(watchDp(7)))
                            .background(theme.panelElevated).padding(watchDp(2))) {
                            ComposeDetailCommentSortTab("热门", !latest) { latest = false }
                            ComposeDetailCommentSortTab("最新", latest) { latest = true }
                        }
                    }
                }
            }
            itemsIndexed(ordered, key = { index, _ ->
                commentKeys[index]
            }, contentType = { _, _ -> "comment-thread" }) { _, group -> ComposeDetailThread(
                group, item, roundScreen, media, onCommentAction, onOpenUser,
                replyState = replyStates[ComposeCommentThreadData.from(group).rootId],
                doubleTapReplyEnabled = services?.session?.doubleTapCommentReply() ?: true,
            ) }
            if (!loading && ordered.isEmpty()) item(key = "empty-comments") {
                Text("暂无评论", color = theme.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(start = headerStart, top = headerTop)
            .size(watchDp(34)).clip(CircleShape).background(theme.panel.copy(alpha = 0.92f))) {
            Icon(painterResource(R.drawable.ic_arrow_back), "返回", tint = theme.text,
                modifier = Modifier.size(watchDp(18)))
        }
    }
}

@Composable
private fun ComposeDetailCommentSortTab(text: String, selected: Boolean, onSelect: () -> Unit) {
    ComposeDetailSortTab(text, selected, onSelect)
}

@Preview(name = "Detail Square", showBackground = true, widthDp = 240, heightDp = 320)
@Composable
private fun ComposeDetailSquarePreview() { DetailStaticPreview(false) }

@Preview(name = "Detail Round", showBackground = true, widthDp = 227, heightDp = 227)
@Composable
private fun ComposeDetailRoundPreview() { DetailStaticPreview(true) }

@Preview(name = "Detail Comments", showBackground = true, widthDp = 240, heightDp = 320)
@Composable
private fun ComposeDetailCommentsPreview() {
    HeyboxComposeTheme(previewTheme(false)) {
        val theme = LocalHeyboxTheme.current
        Column(Modifier.background(theme.background).padding(14.dp)) {
            Text("评论 2", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            ComposeDetailThread(previewCommentGroup(), previewDetailItem(), false,
                ComposeMediaSettings(enabled = false), {})
        }
    }
}

@Composable
private fun DetailStaticPreview(round: Boolean) {
    HeyboxComposeTheme(previewTheme(round)) {
        val item = previewDetailItem()
        val blocks = RichContent.parse("<h2>手表上的游戏日常</h2><p>通勤路上看完一篇长文，图片与评论一起留在正文里。</p>" +
            "<blockquote>把注意力留给内容。</blockquote><p>周末再接着聊。</p>", null)
        ComposeDetailLayout(item, blocks, item.videos, listOf(previewCommentGroup()), false,
            ComposeMediaSettings(enabled = false), {}, {}, {}, {})
    }
}

private fun previewTheme(round: Boolean) = ComposeThemeState(
    dark = true,
    background = androidx.compose.ui.graphics.Color(0xFF0B0B0C),
    panel = androidx.compose.ui.graphics.Color(0xFF19191B),
    panelElevated = androidx.compose.ui.graphics.Color(0xFF202023),
    text = androidx.compose.ui.graphics.Color(0xFFF5F5F7),
    muted = androidx.compose.ui.graphics.Color(0xFFA5A5AA),
    subtle = androidx.compose.ui.graphics.Color(0xFF747479),
    hairline = androidx.compose.ui.graphics.Color(0xFF2B2B2E),
    accent = androidx.compose.ui.graphics.Color(0xFFC4C6C9),
    link = androidx.compose.ui.graphics.Color(0xFF59A9EB),
    onAccent = androidx.compose.ui.graphics.Color.Black,
    uiScale = if (round) 0.82f else 1f,
    textScale = 1f,
    roundScreen = round,
)

private fun previewDetailItem(): FeedItem = FeedItem.from(JSONObject().apply {
    put("linkid", "preview-detail")
    put("title", "在腕上阅读")
    put("user", JSONObject().put("username", "Ronan"))
    put("comment_num", 2)
    put("link_award_num", 128)
    put("topics", JSONArray().put(JSONObject().put("name", "游戏社区")))
})

private fun previewCommentGroup(): JSONObject = JSONObject().apply {
    put("comment", JSONArray()
        .put(JSONObject().put("commentid", "root").put("text", "这段正文很适合在手表上读。")
            .put("child_num", 1).put("comment_award_num", 8)
            .put("user", JSONObject().put("username", "小盒友")))
        .put(JSONObject().put("commentid", "reply").put("text", "图片入口也很顺手。")
            .put("user", JSONObject().put("username", "Ronan"))))
}
