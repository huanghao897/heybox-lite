package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal data class ComposeDetailHeaderPresentation(
    val title: String,
    val section: String,
    val sectionIcon: String,
    val tags: List<String>,
    val level: Int,
) {
    companion object {
        fun from(item: FeedItem, link: JSONObject? = null): ComposeDetailHeaderPresentation {
            val source = link ?: item.contentPreload
            val metadata = FeedMetadata.section(source)
            val section = metadata.name.ifBlank { item.topicName }
            return ComposeDetailHeaderPresentation(
                composeFeedText(link?.optString("title").orEmpty().ifBlank { item.title })
                    .ifBlank { "\u5e16\u5b50" }, section,
                metadata.icon.ifBlank { if (section == item.topicName) item.topicIcon else "" },
                FeedMetadata.tags(source).filter { it != section },
                CommentData.userLevel(source?.optJSONObject("user")),
            )
        }
    }
}

@Composable
internal fun ComposeDetailHeader(
    item: FeedItem,
    media: ComposeMediaSettings,
    onOpenUser: (String, String, String) -> Unit = { _, _, _ -> },
    link: JSONObject? = null,
) {
    val theme = LocalHeyboxTheme.current
    val content = remember(item, link) { ComposeDetailHeaderPresentation.from(item, link) }
    val user = link?.optJSONObject("user")
    val author = Json.first(user?.optString("username"), user?.optString("nickname"),
        user?.optString("name"), item.author)
    val authorId = Json.first(link?.optString("userid"), link?.optString("user_id"),
        link?.optString("heybox_id"), link?.optString("heyboxid"), link?.optString("uid"),
        link?.optString("account_id"), composeCommentUserId(user), item.authorId)
    val avatar = if (user == null) item.authorAvatar else Json.first(user.optString("avatar"), user.optString("avartar"))
    val createdAt = if (link == null) item.createdAt else link.optLong("create_at",
        link.optLong("create_time", link.optLong("createtime")))
    val published = if (createdAt > 0L) Format.relativeTime(createdAt) else ""
    val signature = Json.first(user?.optString("signature"), user?.optString("desc"))
    val meta = listOf(published, signature).filter { it.isNotBlank() }.joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp * theme.uiScale)) {
        ComposeRichText(content.title, theme.dark, theme.text, theme.link,
            fontSize = 18.sp * theme.textScale, lineHeight = 24.sp * theme.textScale,
            fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clickable(enabled = authorId.isNotBlank()) {
            onOpenUser(authorId, author, avatar)
        }, verticalAlignment = Alignment.CenterVertically) {
            ComposeMediaImage(avatar, media, Modifier.size(30.dp * theme.uiScale), CircleShape,
                placeholderLabel = author.take(1), ensureTouchTarget = false)
            Spacer(Modifier.width(7.dp * theme.uiScale))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(author.ifBlank { "\u533f\u540d\u7528\u6237" },
                        Modifier.weight(1f, fill = false), fontSize = 12.sp * theme.textScale,
                        color = theme.text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    if (content.level > 0) {
                        Spacer(Modifier.width(3.dp * theme.uiScale))
                        ComposeCommentLevelBadge(content.level)
                    }
                }
                if (meta.isNotBlank()) Text(meta,
                    fontSize = 10.sp * theme.textScale, color = theme.muted)
            }
        }
        if (content.section.isNotBlank() || content.tags.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp * theme.uiScale)) {
                if (content.section.isNotBlank()) {
                    Row(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(12.dp * theme.uiScale))
                        .background(theme.panelElevated).padding(horizontal = 6.dp * theme.uiScale,
                            vertical = 5.dp * theme.uiScale), verticalAlignment = Alignment.CenterVertically) {
                        if (content.sectionIcon.isNotBlank()) {
                            ComposeMediaImage(content.sectionIcon, media, Modifier.size(16.dp * theme.uiScale),
                                RoundedCornerShape(4.dp), placeholderLabel = "", ensureTouchTarget = false)
                        } else Icon(painterResource(R.drawable.il_globe), null, tint = theme.muted,
                            modifier = Modifier.size(16.dp * theme.uiScale))
                        Spacer(Modifier.width(4.dp * theme.uiScale))
                        Text(content.section, fontSize = 10.5.sp * theme.textScale, color = theme.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (content.tags.isNotEmpty()) Text(content.tags.joinToString("  ") {
                    if (it.startsWith("#")) it else "#$it"
                }, Modifier.weight(1f), fontSize = 10.5.sp * theme.textScale,
                    lineHeight = 15.sp * theme.textScale, color = theme.muted)
            }
        }
    }
}
