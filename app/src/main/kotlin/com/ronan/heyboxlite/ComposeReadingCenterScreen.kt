package com.ronan.heyboxlite

import android.text.TextUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun ComposeReadingCenterScreen(
    state: ComposeReadingCenterState?,
    loading: Boolean,
    services: ComposeServices,
    onOpen: (FeedItem) -> Unit,
    onReadingStats: () -> Unit,
    onWatchLater: () -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val theme = services.theme
    WatchPage("阅读中心", onBack) {
        if (state != null) {
            state.recent?.let { recent ->
                WatchCard(Modifier.clickable { onOpen(recent) }) {
                    Row(Modifier.fillMaxWidth().padding(watchDp(10))) {
                        Column(Modifier.weight(1f)) {
                            Text("继续阅读", color = theme.text, fontSize = watchSp(14f),
                                fontWeight = FontWeight.SemiBold)
                            ComposeRichText(
                                source = RichContent.commentText(recent.title).ifBlank { "无标题内容" },
                                darkMode = theme.dark, textColor = theme.muted, linkColor = theme.link,
                                fontSize = watchSp(11f), lineHeight = watchSp(16f),
                                maxLines = 2, ellipsize = TextUtils.TruncateAt.END,
                                modifier = Modifier.padding(top = watchDp(4)),
                            )
                        }
                        ComposePill(if (recent.video) "视频" else if (recent.article) "文章" else "帖子", theme)
                    }
                    if (state.hasSavedPosition) {
                        Text("已记录上次阅读位置", color = theme.subtle, fontSize = watchSp(10f),
                            modifier = Modifier.padding(start = watchDp(10), bottom = watchDp(6)))
                    }
                }
            }
            WatchCard {
                WatchRow("阅读时长", state.readingSummary, R.drawable.il_reading, onClick = onReadingStats)
                WatchRow("稍后看", state.watchLaterCount.toString(), R.drawable.il_history, onClick = onWatchLater)
                WatchRow("历史记录", icon = R.drawable.il_history, onClick = onHistory)
            }
        } else if (loading) {
            FeedLoading(theme)
        } else {
            WatchEmptyState("阅读数据读取失败")
        }
    }
}
