package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.TimeZone

@Composable
internal fun ComposeReadingStatsScreen(
    services: ComposeServices,
    onBack: () -> Unit,
) {
    val stats = services.reading.stats()
    val state = ComposeReadingStatsMapper.map(
        todayMs = stats.todayMs(),
        todayCount = stats.todayCount,
        totalArticleMs = stats.totalArticleMs,
        totalPostMs = stats.totalPostMs,
        totalCount = stats.totalCount,
        weekMs = stats.weekMs,
        topics = stats.topics.map { ReadingStatsTopicInput(it.name, it.ms) },
        nowMillis = System.currentTimeMillis(),
        timeZone = TimeZone.getDefault(),
    )
    ReadingStatsContent(state, onBack)
}

@Composable
private fun ReadingStatsContent(state: ComposeReadingStatsState, onBack: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    val horizontalPadding = watchDp(if (theme.roundScreen) 7 else 8)
    val postColor = theme.hairline.copy(alpha = if (theme.dark) 0.9f else 0.7f)
    WatchPage("阅读时长", onBack) {
        WatchCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontalPadding, watchDp(7))) {
                Text("今天", color = theme.muted, fontSize = watchSp(11f), letterSpacing = 0.sp)
                Text(Format.readingDuration(state.todayMs), color = theme.text,
                    fontSize = watchSp(25f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = watchDp(3)))
                Text("${state.todayDateLabel} · 看过 ${state.todayCount} 篇", color = theme.muted,
                    fontSize = watchSp(10.5f), lineHeight = watchSp(14f), letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = watchDp(4)))
            }
        }
        WatchSectionTitle("近 7 天")
        WatchCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontalPadding, watchDp(7))) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    state.weekDays.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(watchDp(52)),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(Modifier.width(watchDp(10))
                                    .height((watchDp(52) * day.fraction).coerceAtLeast(watchDp(3)))
                                    .background(if (day.isToday) theme.accent else theme.hairline,
                                        RoundedCornerShape(watchDp(3))))
                            }
                            Text(day.label, color = if (day.isToday) theme.accent else theme.subtle,
                                fontSize = watchSp(8.5f), lineHeight = watchSp(11f), letterSpacing = 0.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = watchDp(4)))
                        }
                    }
                }
                Text("本周 ${Format.readingDuration(state.weekTotalMs)} · 日均 ${Format.readingDuration(state.weekAverageMs)}",
                    color = theme.muted, fontSize = watchSp(10.5f), lineHeight = watchSp(14f), letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = watchDp(9)))
            }
        }
        WatchSectionTitle("内容构成")
        WatchCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontalPadding, watchDp(7))) {
                Text(Format.readingDuration(state.totalMs), color = theme.text,
                    fontSize = watchSp(20f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
                Text("累计 · 看过 ${state.totalCount} 篇", color = theme.muted,
                    fontSize = watchSp(10.5f), lineHeight = watchSp(14f), letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = watchDp(2)))
                Row(modifier = Modifier.padding(top = watchDp(10)).fillMaxWidth().height(watchDp(6))
                    .clip(RoundedCornerShape(watchDp(3))).background(theme.panelElevated)) {
                    if (state.article.fraction > 0f) {
                        Box(Modifier.weight(state.article.fraction).fillMaxHeight()
                            .background(theme.accent, RoundedCornerShape(watchDp(3))))
                    }
                    if (state.post.fraction > 0f) {
                        Box(Modifier.weight(state.post.fraction).fillMaxHeight()
                            .background(postColor, RoundedCornerShape(watchDp(3))))
                    }
                }
                ReadingStatsMetricRow("文章",
                    "${state.article.percent}% · ${Format.readingDuration(state.article.milliseconds)}",
                    marker = theme.accent)
                ReadingStatsMetricRow("帖子",
                    "${state.post.percent}% · ${Format.readingDuration(state.post.milliseconds)}",
                    marker = postColor)
            }
        }
        WatchSectionTitle("常看社区")
        WatchCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontalPadding, watchDp(7))) {
                if (state.topics.isEmpty()) {
                    Text("多看几篇帖子，这里会按社区统计你的阅读分布", color = theme.muted,
                        fontSize = watchSp(10.5f), lineHeight = watchSp(14f), letterSpacing = 0.sp,
                        modifier = Modifier.padding(vertical = watchDp(6)))
                } else {
                    state.topics.forEachIndexed { index, topic ->
                        Column(modifier = Modifier.fillMaxWidth().padding(top = watchDp(if (index == 0) 2 else 12))) {
                            ReadingStatsMetricRow(topic.name, Format.readingDuration(topic.milliseconds),
                                ellipsizeLabel = true)
                            Box(Modifier.padding(top = watchDp(6)).fillMaxWidth().height(watchDp(3))
                                .clip(RoundedCornerShape(watchDp(2))).background(theme.panelElevated)) {
                                if (topic.fraction > 0f) {
                                    Box(Modifier.fillMaxWidth(topic.fraction).fillMaxHeight()
                                        .background(theme.accent, RoundedCornerShape(watchDp(2))))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingStatsMetricRow(
    label: String,
    value: String,
    marker: Color? = null,
    ellipsizeLabel: Boolean = false,
) {
    val theme = LocalHeyboxTheme.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = LocalTextStyle.current.copy(
        fontSize = watchSp(11.5f), lineHeight = watchSp(15f), letterSpacing = 0.sp)
    val valueStyle = LocalTextStyle.current.copy(
        fontSize = watchSp(10.5f), lineHeight = watchSp(14f), letterSpacing = 0.sp)
    val markerWidth = if (marker != null) watchDp(15) else 0.dp
    val gap = watchDp(6)
    val compactLabelWidth = watchDp(48)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().heightIn(min = watchDp(if (marker != null) 30 else 0))) {
        val labelWidth = textMeasurer.measure(label, labelStyle, maxLines = 1).size.width
        val minimumLabelWidth = if (ellipsizeLabel) {
            minOf(labelWidth, with(density) { compactLabelWidth.roundToPx() })
        } else labelWidth
        val valueWidth = textMeasurer.measure(value, valueStyle, maxLines = 1).size.width
        val spacingWidth = with(density) { (markerWidth + gap).roundToPx() }
        val stacked = minimumLabelWidth + valueWidth + spacingWidth > constraints.maxWidth
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
            verticalArrangement = Arrangement.spacedBy(watchDp(2))) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (marker != null) {
                    Box(Modifier.padding(end = watchDp(7)).size(watchDp(8)).background(marker, CircleShape))
                }
                Text(label, color = theme.text, style = labelStyle,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!stacked) {
                    Text(value, color = theme.muted, style = valueStyle, textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = gap))
                }
            }
            if (stacked) {
                Text(value, color = theme.muted, style = valueStyle,
                    modifier = Modifier.fillMaxWidth().padding(start = markerWidth))
            }
        }
    }
}

@Preview(name = "Reading stats - round", widthDp = 192, heightDp = 192)
@Composable
private fun ReadingStatsRoundPreview() = ReadingStatsPreview(roundScreen = true)

@Preview(name = "Reading stats - square", widthDp = 240, heightDp = 240)
@Composable
private fun ReadingStatsSquarePreview() = ReadingStatsPreview(roundScreen = false)

@Composable
private fun ReadingStatsPreview(roundScreen: Boolean) {
    val state = ComposeReadingStatsMapper.map(
        todayMs = 900_000L,
        todayCount = 3,
        totalArticleMs = 7_200_000L,
        totalPostMs = 3_600_000L,
        totalCount = 42,
        weekMs = longArrayOf(0L, 300_000L, 600_000L, 0L, 1_200_000L, 1_800_000L, 900_000L),
        topics = listOf(ReadingStatsTopicInput("Community", 1_200_000L)),
        nowMillis = 1_791_028_800_000L,
        timeZone = TimeZone.getTimeZone("UTC"),
    )
    HeyboxComposeTheme(composeThemeState(ThemeTokens.of(true, 0, 0), 1f, 1f, roundScreen)) {
        ReadingStatsContent(state) {}
    }
}
