package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun ComposeDetailHeader(
    item: FeedItem,
    media: ComposeMediaSettings,
    onOpenUser: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val theme = LocalHeyboxTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(RichContent.plainText(item.title).ifBlank { "帖子" },
            color = theme.text, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        Row(
            modifier = Modifier.clickable(enabled = item.authorId.isNotBlank()) {
                onOpenUser(item.authorId, item.author, item.authorAvatar)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposeMediaImage(item.authorAvatar, media, Modifier.size(30.dp), CircleShape,
                placeholderLabel = item.author.take(1), ensureTouchTarget = false)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(item.author.ifBlank { "匿名用户" }, fontSize = 12.sp,
                    color = theme.text, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (item.createdAt > 0) Text(Format.relativeTime(item.createdAt), fontSize = 10.sp,
                    color = theme.muted)
            }
        }
        val labels = buildList {
            if (item.topicName.isNotBlank()) add(item.topicName)
            if (item.pinned) add("置顶")
            if (item.article) add("文章")
            if (item.video) add("视频")
        }
        if (labels.isNotEmpty()) Text(labels.joinToString(" · "), fontSize = 11.sp,
            color = theme.accent, lineHeight = 16.sp)
        if (item.topicName.isNotBlank()) Text(
            "分区 · ${item.topicName}    标签 · #${item.topicName}",
            fontSize = 10.sp, color = theme.muted, lineHeight = 15.sp,
        )
    }
}

@Composable
internal fun ComposeDetailBlock(
    block: RichContent.Block,
    media: ComposeMediaSettings,
    services: ComposeServices?,
    onOpenImage: (String) -> Unit,
) {
    val display = RichContent.plainText(block.value).ifBlank { block.value.trim() }
    when (block.kind) {
        RichContent.Block.IMAGE -> if (media.enabled) ComposeMediaImage(
            url = block.value, settings = media,
            modifier = Modifier.fillMaxWidth().height(144.dp),
            contentDescription = "打开正文图片", onClick = { onOpenImage(block.value) },
        )
        RichContent.Block.GAME_CARD -> ComposeDetailGameCard(block, media, services)
        RichContent.Block.QUOTE -> Row(Modifier.fillMaxWidth()) {
            val theme = LocalHeyboxTheme.current
            Box(Modifier.width(3.dp).height(40.dp).background(theme.hairline))
            Text(display, Modifier.padding(start = 8.dp), fontSize = 12.sp,
                color = theme.muted, lineHeight = 18.sp)
        }
        else -> {
            val theme = LocalHeyboxTheme.current
            Text(display,
                fontSize = when (block.kind) {
                    RichContent.Block.HEADING -> 15.sp
                    RichContent.Block.CAPTION -> 10.sp
                    else -> 13.sp
                },
                fontWeight = if (block.kind == RichContent.Block.HEADING) FontWeight.Bold else FontWeight.Normal,
                color = if (block.kind == RichContent.Block.CAPTION) theme.muted else theme.text,
                lineHeight = if (block.kind == RichContent.Block.CAPTION) 15.sp else 20.sp,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ComposeDetailGameCard(
    block: RichContent.Block,
    media: ComposeMediaSettings,
    services: ComposeServices?,
) {
    val theme = LocalHeyboxTheme.current
    var data by remember(block.value, block.gameObject) {
        mutableStateOf(GameCardData.fromEmbedded(block.gameObject, block.value))
    }
    var active by remember(block.value, block.gameObject) { mutableStateOf(true) }
    DisposableEffect(block.value, block.gameObject) {
        onDispose { active = false }
    }
    LaunchedEffect(block.value, block.gameObject) {
        val appId = block.value.trim()
        if (appId.isEmpty() || data?.hasVisibleDetails() == true) return@LaunchedEffect
        val client = services?.gameDetails ?: return@LaunchedEffect
        client.loadBatch(
            listOf(RichLinkClassifier.GameLinkInfo(appId, "pc", "", "")),
            object : GameDetailClient.BatchCallback {
                override fun onComplete(value: Map<String, GameCardData>) {
                    if (active) data = value[appId] ?: data
                }
            },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(theme.panelElevated).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (media.enabled && !media.gameCardNoImage && !data?.coverUrl.isNullOrBlank()) {
            ComposeRemoteImage(
                data?.coverUrl.orEmpty(), theme, false, 44.dp,
                RoundedCornerShape(6.dp), "游戏封面", Modifier.size(44.dp),
            )
        } else {
            Icon(painterResource(R.drawable.ic_game_link), null,
                tint = theme.accent, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(data?.name?.ifBlank { "游戏" } ?: "游戏内容", fontSize = 12.sp,
                color = theme.text, maxLines = 2,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val meta = listOf(data?.platforms, data?.score, data?.currentPrice)
                .filter { !it.isNullOrBlank() }.joinToString(" · ")
            Text(meta.ifBlank { "正在获取游戏信息" },
                fontSize = 10.sp, color = theme.muted)
        }
    }
}

@Composable
internal fun ComposeDetailSortTab(text: String, selected: Boolean, onSelect: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    Text(text, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) theme.accent else theme.muted,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onSelect)
            .heightIn(min = 48.dp).padding(horizontal = 9.dp, vertical = 12.dp))
}

private fun rootComment(group: JSONObject): JSONObject =
    group.optJSONArray("comment")?.optJSONObject(0) ?: group

private const val REPLY_IDLE = 0
private const val REPLY_LOADING = 1
private const val REPLY_FAILED = 2

@Composable
internal fun ComposeDetailThread(
    group: JSONObject,
    item: FeedItem,
    roundScreen: Boolean,
    media: ComposeMediaSettings,
    onOpenImage: (String) -> Unit,
    onAction: ComposeDetailActionCallback,
    onOpenUser: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val theme = LocalHeyboxTheme.current
    val context = LocalContext.current
    val root = rootComment(group)
    val rootId = CommentData.commentId(root)
    val array = group.optJSONArray("comment")
    val replies = buildList {
        if (array != null) for (index in 1 until array.length()) array.optJSONObject(index)?.let { add(it) }
    }.sortedBy { CommentData.commentTime(it) }
    val expected = maxOf(root.optInt("child_num"), group.optInt("child_num"), replies.size)
    var shownCount by remember(rootId) { mutableStateOf(minOf(2, replies.size)) }
    var pendingVisible by remember(rootId) { mutableStateOf(shownCount) }
    var lastReplyCount by remember(rootId) { mutableStateOf(replies.size) }
    var loadState by remember(rootId) { mutableStateOf(REPLY_IDLE) }
    var loadRequest by remember(rootId) { mutableStateOf(0) }

    LaunchedEffect(rootId, replies.size) {
        if (replies.size != lastReplyCount) {
            val appended = replies.size > lastReplyCount
            lastReplyCount = replies.size
            shownCount = minOf(shownCount, replies.size)
            if (appended && loadState == REPLY_LOADING) {
                shownCount = minOf(replies.size, pendingVisible)
                loadState = REPLY_IDLE
            }
        }
    }
    LaunchedEffect(rootId, loadRequest) {
        if (loadRequest == 0) return@LaunchedEffect
        delay(5000L)
        if (loadState == REPLY_LOADING) loadState = REPLY_FAILED
    }

    fun requestMoreReplies() {
        val total = maxOf(expected, replies.size)
        val remoteMore = expected > replies.size
        val next = composeCommentNextVisibleCount(total, shownCount, remoteMore)
        if (next <= shownCount && !remoteMore) return
        pendingVisible = next
        shownCount = minOf(replies.size, next)
        if (next > replies.size) {
            loadState = REPLY_LOADING
            loadRequest += 1
            onAction(ComposeDetailAction.LoadReplies(root))
        } else {
            loadState = REPLY_IDLE
        }
    }

    val totalReplies = maxOf(expected, replies.size)
    val remoteMore = expected > replies.size
    val expansionLabel = composeCommentExpansionLabel(totalReplies, shownCount, remoteMore)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (CommentData.isPinnedThread(group)) Text("置顶", fontSize = 10.sp,
            color = theme.accent, fontWeight = FontWeight.Bold)
        ComposeDetailComment(root, item, "", false, media, onOpenImage, onAction, onOpenUser)
        if (replies.isNotEmpty() || expected > 0) {
            Column(
                modifier = Modifier.padding(start = if (roundScreen) 7.dp else 34.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                replies.take(shownCount).forEach { reply ->
                    ComposeDetailComment(reply, item, rootId, true, media, onOpenImage, onAction,
                        onOpenUser)
                }
                if (loadState == REPLY_LOADING) {
                    Text("加载回复中…", fontSize = 11.sp, color = theme.muted,
                        modifier = Modifier.padding(vertical = 6.dp))
                } else if (loadState == REPLY_FAILED) {
                    Text("回复加载失败，重试", fontSize = 11.sp, color = theme.accent,
                        modifier = Modifier.clickable { requestMoreReplies() }
                            .padding(vertical = 6.dp))
                } else if (expansionLabel.isNotBlank()) {
                    Text(expansionLabel, fontSize = 11.sp, color = theme.accent,
                        modifier = Modifier.clickable { requestMoreReplies() }
                            .padding(vertical = 6.dp))
                }
                if (shownCount > minOf(2, replies.size)) Text("收起回复", fontSize = 11.sp,
                    color = theme.muted,
                    modifier = Modifier.clickable {
                        shownCount = minOf(2, replies.size)
                        pendingVisible = shownCount
                        loadState = REPLY_IDLE
                    }.padding(vertical = 6.dp))
            }
        }
        HorizontalDivider(color = theme.hairline.copy(alpha = 0.5f))
    }
}

@Composable
private fun ComposeDetailComment(
    comment: JSONObject,
    item: FeedItem,
    rootId: String,
    reply: Boolean,
    media: ComposeMediaSettings,
    onOpenImage: (String) -> Unit,
    onAction: ComposeDetailActionCallback,
    onOpenUser: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val theme = LocalHeyboxTheme.current
    val context = LocalContext.current
    val user = comment.optJSONObject("user")
    val author = user?.optString("username", "匿名用户") ?: "匿名用户"
    val userId = composeCommentUserId(user)
    val level = CommentData.userLevel(user)
    val target = CommentData.replyTarget(comment, rootId)
    val value = composeCommentText(comment)
    val cy = CommentData.isCyComment(comment)
    val meta = composeCommentMeta(comment, CommentData.commentTime(comment))
    val isPostAuthor = composeCommentIsPostAuthor(item, user, author)
    val doubleTapReply = composeCommentDoubleTapReplyEnabled(context)
    val gesture = Modifier.composeCommentGestures(
        context = context,
        doubleTapReplyEnabled = doubleTapReply,
        copyText = value,
        onReply = { onAction(ComposeDetailAction.Reply(comment)) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        if (reply) {
            ComposeCommentReplyText(
                author = author,
                postAuthor = isPostAuthor,
                target = target,
                text = value,
                cy = cy,
                meta = meta,
                modifier = Modifier.fillMaxWidth().then(gesture),
            )
        } else {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                ComposeMediaImage(
                    user?.optString("avatar").orEmpty(), media, Modifier.size(26.dp), CircleShape,
                    placeholderLabel = author.take(1), ensureTouchTarget = false,
                    onClick = if (userId.isNotBlank()) {
                        { onOpenUser(userId, author, user?.optString("avatar").orEmpty()) }
                    } else null,
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f).then(gesture)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(author.ifBlank { "匿名用户" },
                            Modifier.weight(1f, fill = false), fontSize = 11.sp,
                            color = theme.text, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isPostAuthor) {
                            Spacer(Modifier.width(2.dp))
                            ComposeCommentAuthorBadge()
                        }
                        if (level > 0) {
                            Spacer(Modifier.width(2.dp))
                            ComposeCommentLevelBadge(level)
                        }
                    }
                    if (meta.isNotBlank()) Text(meta, color = theme.muted, fontSize = 9.sp,
                        lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(4.dp))
                val liked = CommentData.commentLiked(comment)
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable {
                        onAction(ComposeDetailAction.LikeComment(comment))
                    }.height(26.dp).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(if (liked) R.drawable.official_comment_like_filled
                        else R.drawable.official_comment_like_line),
                        "点赞评论",
                        tint = if (liked) theme.accent else theme.muted,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(Format.commentLikeCount(CommentData.commentLikes(comment)
                        .coerceAtLeast(0)), color = if (liked) theme.accent else theme.muted,
                        fontSize = 10.sp, maxLines = 1)
                }
            }
            if (value.isNotBlank()) ComposeRichText(
                source = value,
                darkMode = theme.dark,
                textColor = theme.text,
                linkColor = theme.link,
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp).then(gesture),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                cy = cy,
            )
        }
        ComposeCommentImageGrid(CommentData.commentImages(comment), media, reply, onOpenImage)
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
) {
    val theme = LocalHeyboxTheme.current
    Column(Modifier.background(theme.panel)) {
        HorizontalDivider(color = theme.hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            DetailCountAction(if (liked) R.drawable.official_comment_like_filled
                else R.drawable.official_comment_like_line, likes, "点赞帖子", onLike)
            DetailCountAction(R.drawable.official_detail_comment, item.comments, "评论") {
                onComment()
            }
        }
    }
}

@Composable
private fun DetailCountAction(icon: Int, count: Int, description: String, onClick: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), description, tint = theme.text,
            modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(Format.commentLikeCount(count.coerceAtLeast(0)), fontSize = 11.sp,
            color = theme.text)
    }
}
