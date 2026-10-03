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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    data class LikeComment(val comment: JSONObject) : ComposeDetailAction()
    data class Reply(val comment: JSONObject) : ComposeDetailAction()
    data class LoadReplies(val root: JSONObject) : ComposeDetailAction()
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
        val rotary = services.theme.rotaryRequest
        LaunchedEffect(rotary?.serial) {
            if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
        }
        ComposeDetailLayout(item, content, videos, comments, loading, settings, onBack, onOpenImage,
            onOpenVideo, onCommentAction, listState, onOpenUser, services, link)
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
) {
    val theme = LocalHeyboxTheme.current
    val roundScreen = theme.roundScreen
    var latest by remember(item.id) { mutableStateOf(false) }
    val ordered = remember(comments, latest) { composeOrderedComments(comments, latest) }
    val commentKeys = remember(ordered) { composeCommentKeys(ordered) }
    val inset = (if (roundScreen) 18.dp else 12.dp) * theme.uiScale
    var postLiked by remember(item.id) { mutableStateOf(item.liked) }
    var postLikes by remember(item.id) { mutableStateOf(item.likes) }
    Column(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), "返回", tint = theme.text)
            }
            Text("帖子详情", color = theme.text,
                fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = inset, end = inset, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            state = listState,
        ) {
            item(key = "header") { ComposeDetailHeader(item, media, onOpenUser, link) }
            if (loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            itemsIndexed(content, key = { index, _ -> "body-$index" }) { _, block ->
                ComposeDetailBlock(block, media, services, onOpenImage)
            }
            itemsIndexed(videos, key = { index, _ -> "video-$index" }) { _, video ->
                ComposeVideoCard(video, media, onOpenVideo = onOpenVideo)
            }
            if (!loading && content.isEmpty()) item(key = "fallback-body") {
                val fallback = composeDetailFallbackText(item.description)
                val bodyScale = theme.textScale * (services?.session?.bodyTextScale() ?: 100) / 100f
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (fallback.isNotBlank()) ComposeRichText(fallback, theme.dark, theme.text, theme.link,
                        fontSize = 13.sp * bodyScale, lineHeight = 20.sp * bodyScale)
                    if (media.enabled) item.images.forEach { url ->
                        ComposeMediaImage(url, media, Modifier.fillMaxWidth().height(144.dp),
                            contentDescription = "打开正文图片", onClick = { onOpenImage(url) })
                    }
                }
            }
            if (!loading && content.isEmpty() && videos.isEmpty()
                && item.description.isBlank() && item.images.isEmpty()) item(key = "empty-body") {
                Text("正文为空", color = theme.muted, fontSize = 12.sp)
            }
            item(key = "comment-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = theme.hairline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("评论 ${ordered.size}", Modifier.weight(1f), fontSize = 13.sp,
                            color = theme.text, fontWeight = FontWeight.Bold)
                        ComposeDetailCommentSortTab("热门", !latest) { latest = false }
                        ComposeDetailCommentSortTab("最新", latest) { latest = true }
                    }
                }
            }
            itemsIndexed(ordered, key = { index, _ ->
                commentKeys[index]
            }) { _, group -> ComposeDetailThread(
                group, item, roundScreen, media, onOpenImage, onCommentAction, onOpenUser,
            ) }
            if (!loading && ordered.isEmpty()) item(key = "empty-comments") {
                Text("暂无评论", color = theme.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        ComposeDetailDock(
            item = item,
            liked = postLiked,
            likes = postLikes,
            inset = inset,
            onLike = {
                postLiked = !postLiked
                postLikes = (postLikes + if (postLiked) 1 else -1).coerceAtLeast(0)
                onCommentAction(ComposeDetailAction.LikePost(item))
            },
            onComment = { onCommentAction(ComposeDetailAction.WriteComment(item)) },
        )
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
                ComposeMediaSettings(enabled = false), {}, {})
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
