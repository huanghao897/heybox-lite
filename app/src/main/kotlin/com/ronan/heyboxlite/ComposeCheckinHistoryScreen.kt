package com.ronan.heyboxlite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeCheckinHistoryScreen(
    state: ComposeCheckinUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (CheckinHistory.Entry) -> Unit,
) {
    WatchPage("签到记录", onBack) {
        if (state.historyError.isNotEmpty()) {
            CheckinPanel { CheckinMenuRow("重新读取", R.drawable.il_refresh, onRetry,
                subtitle = state.historyError) }
        }
        val entries = state.history?.entries.orEmpty()
        when {
            entries.isNotEmpty() -> entries.forEach { entry ->
                CheckinPanel(Modifier.clickable { onOpen(entry) }) {
                    CheckinRecordSummary(entry, showChevron = true)
                }
            }
            state.historyLoading -> WatchEmptyState("正在同步签到记录")
            state.historyError.isEmpty() -> WatchEmptyState("暂无签到记录")
        }
    }
}

@Composable
internal fun ComposeCheckinHistoryDetailScreen(entry: CheckinHistory.Entry?, onBack: () -> Unit) {
    WatchPage("签到详情", onBack) {
        if (entry == null) {
            WatchEmptyState("记录暂不可用")
        } else {
            CheckinPanel { CheckinRecordSummary(entry, showChevron = false) }
            WatchSectionTitle("任务完成情况")
            val tasks = remember(entry) { checkinTasks(entry) }
            CheckinPanel {
                tasks.forEachIndexed { index, task ->
                    if (index > 0) CheckinDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = watchDp(12), vertical = watchDp(10)),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(task.icon), null, tint = LocalHeyboxTheme.current.muted,
                            modifier = Modifier.size(watchDp(18)))
                        Spacer(Modifier.width(watchDp(9)))
                        Column(Modifier.weight(1f)) {
                            Text(task.title, color = LocalHeyboxTheme.current.text, fontSize = watchSp(12f),
                                fontWeight = FontWeight.Medium)
                            if (task.kind == CheckinResultKind.UNKNOWN) Text(task.value,
                                color = LocalHeyboxTheme.current.subtle, fontSize = watchSp(10f))
                        }
                        CheckinResultIcon(task.kind, Modifier.size(watchDp(18)))
                    }
                }
            }
            if (entry.summary.isNotBlank()) {
                WatchSectionTitle("执行摘要")
                Text(entry.summary, color = LocalHeyboxTheme.current.muted, fontSize = watchSp(11f),
                    modifier = Modifier.padding(horizontal = watchDp(5)))
            }
            val otherDetails = remember(entry) { entry.details.filterNot { field ->
                field.startsWith("分享帖子 · ") || field.startsWith("分享游戏详情 · ") ||
                    field.startsWith("分享游戏评价 · ")
            } }
            if (otherDetails.isNotEmpty()) {
                WatchSectionTitle("执行信息")
                CheckinPanel {
                    Column(Modifier.padding(watchDp(12)),
                        verticalArrangement = Arrangement.spacedBy(watchDp(5))) {
                        otherDetails.forEach { Text(it, color = LocalHeyboxTheme.current.muted,
                            fontSize = watchSp(10f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckinRecordSummary(entry: CheckinHistory.Entry, showChevron: Boolean) {
    val theme = LocalHeyboxTheme.current
    val density = LocalDensity.current
    val tasks = remember(entry) { checkinTasks(entry) }
    val completed = tasks.count { it.kind == CheckinResultKind.SUCCESS }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(watchDp(10))) {
        if (maxWidth < (130 * theme.textScale * density.fontScale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(watchDp(3))) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CheckinResultIcon(checkinResultKind(entry))
                    Spacer(Modifier.width(watchDp(8)))
                    Text(checkinResultTitle(entry), color = theme.text, fontSize = watchSp(12f),
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (showChevron) CheckinRecordChevron()
                }
                CheckinRecordDetails(entry, completed)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckinResultIcon(checkinResultKind(entry))
                Spacer(Modifier.width(watchDp(8)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(watchDp(3))) {
                    Text(checkinResultTitle(entry), color = theme.text, fontSize = watchSp(12f),
                        fontWeight = FontWeight.SemiBold)
                    CheckinRecordDetails(entry, completed)
                }
                if (showChevron) CheckinRecordChevron()
            }
        }
    }
}

@Composable
private fun CheckinRecordDetails(entry: CheckinHistory.Entry, completed: Int) {
    val theme = LocalHeyboxTheme.current
    Text(entry.displayTime(), color = theme.muted, fontSize = watchSp(10f))
    val reward = entry.rewardLabel()
    if (reward.isNotEmpty()) Text(reward, color = theme.link, fontSize = watchSp(10f))
    if (completed > 0) Text("完成 $completed 项任务", color = theme.subtle, fontSize = watchSp(9f))
}

@Composable
private fun CheckinRecordChevron() {
    Icon(painterResource(R.drawable.il_chevron), null, tint = LocalHeyboxTheme.current.subtle,
        modifier = Modifier.size(watchDp(13)))
}
