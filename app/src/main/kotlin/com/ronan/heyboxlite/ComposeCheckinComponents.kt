package com.ronan.heyboxlite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun CheckinPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val shape = RoundedCornerShape(watchDp(12))
    Column(modifier.fillMaxWidth().clip(shape).background(theme.panel)
        .border(BorderStroke(0.5.dp, theme.hairline), shape), content = content)
}

@Composable
internal fun CheckinDivider() {
    Spacer(Modifier.fillMaxWidth().padding(horizontal = watchDp(10))
        .height(0.5.dp).background(LocalHeyboxTheme.current.hairline))
}

@Composable
internal fun CheckinMenuRow(
    title: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val theme = LocalHeyboxTheme.current
    val color = if (!enabled) theme.subtle else if (danger) Color(0xFFDD7777) else theme.text
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = watchDp(38)).padding(horizontal = watchDp(12), vertical = watchDp(8)),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(watchDp(18)))
        Spacer(Modifier.width(watchDp(9)))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontSize = watchSp(12f), fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) Text(subtitle, color = theme.muted, fontSize = watchSp(10f),
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = watchDp(2)))
        }
        Spacer(Modifier.width(watchDp(5)))
        Icon(painterResource(R.drawable.il_chevron), null, tint = theme.subtle,
            modifier = Modifier.size(watchDp(14)))
    }
}

@Composable
internal fun CheckinActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    val theme = LocalHeyboxTheme.current
    Button(onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = watchDp(42)), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = theme.onAccent,
            disabledContainerColor = theme.panelElevated, disabledContentColor = theme.subtle),
        contentPadding = PaddingValues(horizontal = watchDp(12), vertical = watchDp(7))) {
        if (icon != null) {
            Icon(painterResource(icon), null, modifier = Modifier.size(watchDp(15)))
            Spacer(Modifier.width(watchDp(7)))
        }
        Text(text, fontSize = watchSp(13f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun CheckinResultIcon(kind: CheckinResultKind, modifier: Modifier = Modifier) {
    val theme = LocalHeyboxTheme.current
    val fill = when (kind) {
        CheckinResultKind.SUCCESS -> theme.accent
        CheckinResultKind.FAILURE -> Color(0xFFCA6565)
        CheckinResultKind.UNKNOWN -> theme.panelElevated
    }
    Box(modifier.size(watchDp(27)).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Icon(painterResource(when (kind) {
            CheckinResultKind.SUCCESS -> R.drawable.ic_check
            CheckinResultKind.FAILURE -> R.drawable.ic_close
            CheckinResultKind.UNKNOWN -> R.drawable.il_history
        }), null, tint = if (kind == CheckinResultKind.SUCCESS) theme.onAccent else Color.White,
            modifier = Modifier.size(watchDp(17)))
    }
}

@Composable
internal fun CheckinBadge(text: String) {
    val theme = LocalHeyboxTheme.current
    Text(text, color = theme.text, fontSize = watchSp(9f), fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(watchDp(6)))
            .background(theme.panelElevated).padding(horizontal = watchDp(7), vertical = watchDp(4)),
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}
