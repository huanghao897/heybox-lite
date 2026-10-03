package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeReadingStatsScreen(
    services: ComposeServices,
    onBack: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val stats = services.reading.stats()
    WatchPage("阅读时长", onBack) {
        WatchCard {
            Column(modifier = Modifier.fillMaxWidth().padding(watchDp(12))) {
                Text("今天", color = theme.muted, fontSize = watchSp(11f))
                Text(Format.readingDuration(stats.todayMs()), color = theme.text,
                    fontSize = watchSp(25f), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = watchDp(3)))
                Text("看过 ${stats.todayCount} 篇", color = theme.muted,
                    fontSize = watchSp(10f), modifier = Modifier.padding(top = watchDp(2)))
            }
        }
        WatchSectionTitle("近 7 天")
        WatchCard {
            Column(modifier = Modifier.fillMaxWidth().padding(watchDp(11))) {
                val max = stats.weekMs.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                Row(
                    modifier = Modifier.fillMaxWidth().height(watchDp(72)),
                    horizontalArrangement = Arrangement.spacedBy(watchDp(5)),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    stats.weekMs.forEachIndexed { index, value ->
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                Modifier.fillMaxWidth(0.6f)
                                    .height(watchDp((6 + (value * 48L / max).toInt()).coerceAtLeast(6)))
                                    .background(if (index == 6) theme.accent else theme.hairline),
                            )
                            Text(if (index == 6) "今" else "${index + 1}",
                                color = if (index == 6) theme.accent else theme.subtle,
                                fontSize = watchSp(8f),
                                modifier = Modifier.padding(top = watchDp(3)))
                        }
                    }
                }
                Text("本周 ${Format.readingDuration(stats.weekTotalMs())}",
                    color = theme.muted, fontSize = watchSp(10f),
                    modifier = Modifier.padding(top = watchDp(8)))
            }
        }
        WatchSectionTitle("内容构成")
        WatchCard {
            val total = stats.totalMs().coerceAtLeast(1L)
            ReadingSplitRow("文章", stats.totalArticleMs, total, theme.accent)
            ReadingSplitRow("帖子", stats.totalPostMs, total,
                theme.hairline.copy(alpha = if (theme.dark) 0.9f else 0.7f))
        }
        WatchSectionTitle("常看社区")
        WatchCard {
            if (stats.topics.isEmpty()) {
                WatchEmptyState("多看几篇内容后，这里会显示阅读分布")
            } else {
                stats.topics.take(5).forEach { topic ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = watchDp(11), vertical = watchDp(7)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(topic.name, color = theme.text, fontSize = watchSp(12f),
                            modifier = Modifier.weight(1f), maxLines = 1)
                        Text(Format.readingDuration(topic.ms), color = theme.muted,
                            fontSize = watchSp(10f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingSplitRow(
    label: String,
    milliseconds: Long,
    total: Long,
    color: Color,
) {
    val theme = LocalHeyboxTheme.current
    val percent = (milliseconds * 100L / total).toInt().coerceIn(0, 100)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = watchDp(11), vertical = watchDp(7)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(watchDp(8)).background(color))
        Text(label, color = theme.text, fontSize = watchSp(12f),
            modifier = Modifier.padding(start = watchDp(7)).weight(1f))
        Text("$percent% · ${Format.readingDuration(milliseconds)}",
            color = theme.muted, fontSize = watchSp(10f))
    }
}
