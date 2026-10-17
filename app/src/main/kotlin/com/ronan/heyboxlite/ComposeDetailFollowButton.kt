package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeDetailFollowButton(following: Boolean, pending: Boolean, onFollow: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    val shape = RoundedCornerShape(3.dp * theme.uiScale)
    val color = if (following) theme.muted else theme.background
    Row(
        modifier = Modifier.testTag("detail-follow").clip(shape)
            .background(if (following) Color.Transparent else theme.text)
            .border(0.5.dp, if (following) theme.hairline else Color.Transparent, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = if (following) "取消关注" else "关注作者"
                stateDescription = if (pending) "正在更新" else if (following) "已关注" else "未关注"
            }.clickable(enabled = !pending, role = Role.Button, onClick = onFollow)
            .heightIn(min = (26.dp * theme.uiScale).coerceAtLeast(24.dp))
            .padding(start = 4.dp * theme.uiScale, end = 5.dp * theme.uiScale,
                top = 3.dp * theme.uiScale, bottom = 3.dp * theme.uiScale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp * theme.uiScale),
    ) {
        if (!following) Icon(painterResource(R.drawable.il_plus), null, tint = color,
            modifier = Modifier.size(11.dp * theme.uiScale))
        Text(if (following) "已关注" else "关注", color = color,
            fontSize = 11.sp * theme.textScale, lineHeight = 15.sp * theme.textScale,
            fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    }
}
