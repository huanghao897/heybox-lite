package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val shape = RoundedCornerShape(watchDp(WatchRadius.LARGE))
    Column(modifier.fillMaxWidth().clip(shape).background(theme.panel), content = content)
}

@Composable
internal fun CheckinDivider() {
    Spacer(Modifier.fillMaxWidth().padding(horizontal = watchDp(10))
        .height(0.5.dp).background(LocalHeyboxTheme.current.hairline))
}

/** A WatchRow with a trailing chevron, so check-in menus match the settings pages. */
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
    WatchRow(title, icon = icon, enabled = enabled, subtitle = subtitle, danger = danger,
        chevron = true, modifier = modifier, onClick = onClick)
}

@Composable
internal fun CheckinDangerRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalHeyboxTheme.current
    val danger = theme.danger
    val shape = RoundedCornerShape(watchDp(WatchRadius.MEDIUM))
    val foreground = if (enabled) danger else theme.subtle
    val background = if (enabled) danger.copy(alpha = if (theme.dark) 0.14f else 0.10f)
        else theme.panelElevated
    Row(
        modifier.fillMaxWidth().clip(shape).background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = watchTouchDp(40))
            .padding(horizontal = watchDp(11), vertical = watchDp(7)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_logout), null, tint = foreground,
            modifier = Modifier.size(watchDp(18)))
        Spacer(Modifier.width(watchDp(9)))
        Text(title, color = foreground, fontSize = watchSp(WatchType.BODY),
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f))
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
    WatchActionButton(text, onClick, modifier.fillMaxWidth(), enabled = enabled, icon = icon,
        primary = true)
}

@Composable
internal fun CheckinResultIcon(kind: CheckinResultKind, modifier: Modifier = Modifier) {
    val theme = LocalHeyboxTheme.current
    val (fill, tint) = when (kind) {
        CheckinResultKind.SUCCESS -> theme.success to theme.onSuccess
        CheckinResultKind.FAILURE -> theme.danger to theme.onDanger
        CheckinResultKind.UNKNOWN -> theme.panelElevated to theme.muted
    }
    Box(modifier.size(watchDp(27)).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Icon(painterResource(when (kind) {
            CheckinResultKind.SUCCESS -> R.drawable.ic_check
            CheckinResultKind.FAILURE -> R.drawable.ic_close
            CheckinResultKind.UNKNOWN -> R.drawable.il_history
        }), null, tint = tint,
            modifier = Modifier.size(watchDp(17)))
    }
}

@Composable
internal fun CheckinBadge(text: String) {
    val theme = LocalHeyboxTheme.current
    Text(text, color = theme.text, fontSize = watchSp(WatchType.CAPTION), fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(watchDp(WatchRadius.SMALL)))
            .background(theme.panelElevated).padding(horizontal = watchDp(7), vertical = watchDp(4)),
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}
