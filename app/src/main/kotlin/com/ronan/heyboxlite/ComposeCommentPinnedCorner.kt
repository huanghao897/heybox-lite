package com.ronan.heyboxlite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeCommentPinnedCorner(modifier: Modifier = Modifier) {
    val scale = LocalHeyboxTheme.current.uiScale
    // The APK's usable drawable is a gradient vector; its legacy PNG fallback is transparent.
    Box(modifier.size(28.dp * scale).testTag("comment-pinned-corner")
        .semantics { contentDescription = "置顶评论" }) {
        Canvas(Modifier.size(28.dp * scale)) {
            val triangle = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(triangle, Brush.linearGradient(
                listOf(Color(0xFF464B50), Color(0xFF14191E)),
                start = Offset(0f, size.height), end = Offset(size.width, 0f)))
        }
        Text("置顶", color = Color.White, fontSize = 7.5.sp * scale,
            lineHeight = 10.sp * scale, fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.offset(x = (-2).dp * scale, y = 3.dp * scale)
                .width(26.dp * scale).rotate(-45f))
    }
}
