package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeDetailFollowButton(following: Boolean, pending: Boolean, onFollow: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    val shape = RoundedCornerShape(50)
    val color = if (following) theme.muted else theme.background
    // The visible pill stays compact; the surrounding target is a full finger in size.
    Box(
        modifier = Modifier.testTag("detail-follow").clip(shape)
            .semantics(mergeDescendants = true) {
                contentDescription = if (following) "取消关注" else "关注作者"
                stateDescription = if (pending) "正在更新" else if (following) "已关注" else "未关注"
            }.clickable(enabled = !pending, role = Role.Button, onClick = onFollow)
            .heightIn(min = WatchTouchTarget).widthIn(min = WatchTouchTarget),
        contentAlignment = Alignment.Center,
    ) { Row(
        modifier = Modifier.clip(shape)
            .background(if (following) Color.Transparent else theme.text)
            .border(0.5.dp, if (following) theme.hairline else Color.Transparent, shape)
            .heightIn(min = (24.dp * theme.uiScale).coerceAtLeast(20.dp))
            .padding(start = 7.dp * theme.uiScale, end = 9.dp * theme.uiScale,
                top = 1.dp * theme.uiScale, bottom = 1.dp * theme.uiScale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp * theme.uiScale),
    ) {
        if (!following) Icon(painterResource(R.drawable.il_plus), null, tint = color,
            modifier = Modifier.size(watchDp(WatchIcon.SMALL)))
        Text(if (following) "已关注" else "关注", color = color,
            fontSize = WatchType.CAPTION.sp * theme.textScale, lineHeight = 15.sp * theme.textScale,
            fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
    } }
}
