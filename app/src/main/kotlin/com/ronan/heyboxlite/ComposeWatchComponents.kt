@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ronan.heyboxlite

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Modifier.watchHorizontalPadding(): Modifier {
    val state = LocalHeyboxTheme.current
    val base = if (state.roundScreen) 16 else 12
    return padding(horizontal = (base * state.uiScale).dp)
}

@Composable
internal fun watchDp(value: Int): androidx.compose.ui.unit.Dp =
    (value * LocalHeyboxTheme.current.uiScale).dp

@Composable
internal fun watchSp(value: Float): androidx.compose.ui.unit.TextUnit =
    (value * LocalHeyboxTheme.current.textScale).sp

@Composable
internal fun WatchPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = LocalHeyboxTheme.current
    val scrollState = rememberScrollState()
    val rotary = state.rotaryRequest
    LaunchedEffect(rotary?.serial) {
        if (rotary != null) scrollState.scrollBy(rotary.distance.toFloat())
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(state.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = watchDp(if (state.roundScreen) 14 else 10),
                    end = watchDp(8),
                    top = watchDp(4),
                    bottom = watchDp(2),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(watchDp(34)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "返回",
                        tint = state.text,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(watchDp(8)))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = state.text,
                fontSize = watchSp(17f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .watchHorizontalPadding()
                .padding(bottom = watchDp(14)),
            verticalArrangement = Arrangement.spacedBy(watchDp(9)),
            content = content,
        )
    }
}

@Composable
internal fun WatchCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = LocalHeyboxTheme.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(watchDp(14)),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) state.panelElevated else state.panel,
        ),
        border = BorderStroke(watchDp(1).coerceAtLeast(0.5.dp), state.hairline),
        content = content,
    )
}

@Composable
internal fun WatchSectionTitle(text: String) {
    Text(
        text = text,
        color = LocalHeyboxTheme.current.muted,
        fontSize = watchSp(11f),
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = watchDp(3), top = watchDp(2)),
    )
}

@Composable
internal fun WatchRow(
    title: String,
    value: String = "",
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val state = LocalHeyboxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(watchDp(10)))
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = watchDp(11), vertical = watchDp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = state.muted,
                modifier = Modifier.size(watchDp(18)),
            )
            Spacer(modifier = Modifier.width(watchDp(8)))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (enabled) state.text else state.subtle,
            fontSize = watchSp(13f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (value.isNotEmpty()) {
            Text(
                value,
                modifier = Modifier
                    .widthIn(max = watchDp(92))
                    .padding(start = watchDp(6)),
                color = state.muted,
                fontSize = watchSp(11f),
                maxLines = 2,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WatchSwitchRow(
    title: String,
    checked: Boolean,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val state = LocalHeyboxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(watchDp(10)))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .heightIn(min = watchDp(40))
            .padding(horizontal = watchDp(11), vertical = watchDp(7)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = state.muted,
                modifier = Modifier.size(watchDp(18)),
            )
            Spacer(modifier = Modifier.width(watchDp(8)))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (enabled) state.text else state.subtle,
            fontSize = watchSp(13f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val checkedTrack = if (state.dark) Color(0xFF77777D) else Color(0xFFA6A6AB)
        val uncheckedTrack = if (state.dark) Color(0xFF3A3A3E) else Color(0xFFD1D1D6)
        val thumb = if (state.dark) Color(0xFFF5F5F7) else Color.White
        val switchColors = SwitchDefaults.colors(
            checkedThumbColor = thumb,
            checkedTrackColor = checkedTrack,
            checkedBorderColor = checkedTrack,
            checkedIconColor = state.text,
            uncheckedThumbColor = thumb,
            uncheckedTrackColor = uncheckedTrack,
            uncheckedBorderColor = uncheckedTrack,
            uncheckedIconColor = state.text,
            disabledCheckedThumbColor = state.subtle,
            disabledCheckedTrackColor = checkedTrack.copy(alpha = 0.45f),
            disabledCheckedBorderColor = checkedTrack.copy(alpha = 0.45f),
            disabledCheckedIconColor = state.subtle,
            disabledUncheckedThumbColor = state.subtle,
            disabledUncheckedTrackColor = uncheckedTrack.copy(alpha = 0.45f),
            disabledUncheckedBorderColor = uncheckedTrack.copy(alpha = 0.45f),
            disabledUncheckedIconColor = state.subtle,
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = switchColors,
                modifier = Modifier.size(width = watchDp(42), height = watchDp(26)),
            )
        }
    }
}

@Composable
internal fun WatchValueCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val state = LocalHeyboxTheme.current
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(watchDp(12)),
        colors = CardDefaults.cardColors(containerColor = state.panelElevated),
    ) {
        Column(modifier = Modifier.padding(watchDp(11))) {
            Text(label, color = state.subtle, fontSize = watchSp(10f), maxLines = 1)
            Spacer(modifier = Modifier.height(watchDp(3)))
            Text(value, color = state.text, fontSize = watchSp(14f), fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun WatchEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = watchDp(28)), contentAlignment = Alignment.Center) {
        Text(text, color = LocalHeyboxTheme.current.muted, fontSize = watchSp(12f))
    }
}

@Composable
internal fun WatchActionText(text: String, onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.height(watchDp(36)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(text, color = MaterialTheme.colorScheme.primary, fontSize = watchSp(11f))
        }
    }
}
