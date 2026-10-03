package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DisplayPreviewScreen(state: ComposeThemeState) {
    val scale = state.uiScale
    val textScale = state.textScale
    val horizontal = if (state.roundScreen) 16.dp else 12.dp
    val cardShape = RoundedCornerShape((12 * scale).dp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = state.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal, vertical = (10 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
        ) {
            Text(
                text = "Compose 界面预览",
                color = state.text,
                fontSize = (16 * textScale).sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "当前页面已经使用 Compose 渲染，主题和屏幕适配沿用 heybox Lite 设置。",
                color = state.muted,
                fontSize = (11 * textScale).sp,
                lineHeight = (15 * textScale).sp,
            )
            PreviewCard(
                state = state,
                shape = cardShape,
                title = "社区帖子",
                body = "方屏、圆屏和窄屏都会保持稳定的内容边距，文字不会被圆角区域截断。",
                badge = "帖子",
            )
            PreviewCard(
                state = state,
                shape = cardShape,
                title = "评论层级",
                body = "一级评论保留主信息密度，二级评论通过缩进和轻背景建立关系。",
                badge = "评论",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
            ) {
                PreviewMetric(state, "圆屏", if (state.roundScreen) "已适配" else "方屏布局", Modifier.weight(1f))
                PreviewMetric(state, "缩放", "${(scale * 100).toInt()}%", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height((8 * scale).dp))
        }
    }
}

@Composable
private fun PreviewCard(
    state: ComposeThemeState,
    shape: RoundedCornerShape,
    title: String,
    body: String,
    badge: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = state.panel),
        border = BorderStroke(1.dp, state.hairline),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(state.accent),
                )
                Text(
                    text = badge,
                    color = state.accent,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = title,
                color = state.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = body,
                color = state.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

@Composable
private fun PreviewMetric(
    state: ComposeThemeState,
    label: String,
    value: String,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = state.panelElevated),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = state.subtle, fontSize = 9.sp)
            Text(value, color = state.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, widthDp = 192, heightDp = 192)
@Composable
private fun DisplayPreviewRoundPreview() {
    val state = ComposeThemeState(
        dark = true,
        background = Color(0xFF0B0B0C),
        panel = Color(0xFF19191B),
        panelElevated = Color(0xFF202023),
        text = Color(0xFFF5F5F7),
        muted = Color(0xFFA5A5AA),
        subtle = Color(0xFF747479),
        hairline = Color(0xFF2B2B2E),
        accent = Color(0xFFC4C6C9),
        link = Color(0xFF59A9EB),
        onAccent = Color.Black,
        uiScale = 0.82f,
        textScale = 1f,
        roundScreen = true,
    )
    HeyboxComposeTheme(state) { DisplayPreviewScreen(state) }
}
