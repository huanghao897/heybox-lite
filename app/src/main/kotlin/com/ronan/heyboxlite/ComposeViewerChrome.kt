package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeViewerChrome(
    round: Boolean,
    current: Int,
    count: Int,
    originalEnabled: Boolean,
    originalLoaded: Boolean,
    loading: Boolean,
    onOriginal: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    imageAvailable: Boolean = true,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("viewer-chrome")) {
        val shortSide = minOf(maxWidth, maxHeight)
        val button = (shortSide * .16f).coerceIn(32.dp, 44.dp)
        val side = if (round) shortSide * .12f else 12.dp
        val top = if (round) shortSide * .24f else 12.dp
        ViewerIcon(R.drawable.ic_close, "关闭图片", button, onClose,
            Modifier.align(Alignment.TopStart).offset(x = side, y = top))
        if (imageAvailable) ViewerIcon(R.drawable.ic_download, "下载图片", button, onSave,
            Modifier.align(Alignment.BottomStart).offset(x = side, y = -top))
        if (count > 1) {
            Text("${current + 1}/$count", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = if (round) shortSide * .07f else 18.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp).testTag("viewer-counter"))
        }
        if (imageAvailable && originalEnabled && !originalLoaded) {
            Text(if (loading) "加载中" else "查看原图", color = Color.White, fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(if (round) Alignment.CenterEnd else Alignment.BottomCenter)
                    .padding(end = if (round) side else 0.dp, bottom = if (round) 0.dp else 14.dp)
                    .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .6f))
                    .clickable(enabled = !loading, role = Role.Button, onClick = onOriginal)
                    .padding(horizontal = 12.dp, vertical = 10.dp).testTag("viewer-original"))
        }
    }
}

@Composable
private fun ViewerIcon(icon: Int, name: String, size: androidx.compose.ui.unit.Dp,
                       action: () -> Unit, modifier: Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = .6f))
        .clickable(role = Role.Button, onClickLabel = name, onClick = action),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), name, Modifier.size(size * .5f), tint = Color.White)
    }
}
