package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
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
    data class OpenCommentImage(val comment: JSONObject, val url: String,
        val source: android.widget.ImageView? = null) : ComposeDetailAction()
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
    onOpenImagePreview: ((String, android.widget.ImageView) -> Unit)? = null,
    commentsListState: LazyListState = rememberLazyListState(),
    pagerState: PagerState = rememberPagerState { 2 },
    onPageNavigation: (ComposeDetailPagerNavigation?) -> Unit = {},
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
        if (active && rotaryInput != null && !pagerState.isScrollInProgress) key(pagerState.settledPage) {
            ObserveFeedRotaryInput(rotaryInput, if (pagerState.settledPage == 0) listState else commentsListState)
        }
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
            useImagePager = useImagePager, onVideoSettings = { videoSettings = true },
            onOpenImagePreview = onOpenImagePreview, commentsListState = commentsListState,
            pagerState = pagerState, onPageNavigation = onPageNavigation)
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
    onOpenImagePreview: ((String, android.widget.ImageView) -> Unit)? = null,
    commentsListState: LazyListState = rememberLazyListState(),
    pagerState: PagerState = rememberPagerState { 2 },
    onPageNavigation: (ComposeDetailPagerNavigation?) -> Unit = {},
) {
    ComposeDetailPager(pagerState, active, visible, onBack, onPageNavigation,
        body = { padding, pageActive ->
            ComposeDetailBody(item, content, videos, comments, loading, media, services, listState,
                padding, pageActive, onOpenImage, onOpenVideo, onCommentAction, onOpenUser,
                link, replyStates, error, onRetry, actionRevision, notice, useImagePager,
                onVideoSettings, onOpenImagePreview)
        },
        comments = { padding, pageActive ->
            ComposeDetailCommentsPage(item, comments, loading, media, services, commentsListState,
                padding, pageActive, replyStates, error, onRetry, onCommentAction, onOpenUser)
        })
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
