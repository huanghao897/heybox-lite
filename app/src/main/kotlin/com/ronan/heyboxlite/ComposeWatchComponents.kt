@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ronan.heyboxlite

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Modifier.watchHorizontalPadding(): Modifier {
    val state = LocalHeyboxTheme.current
    return padding(horizontal = watchPageHorizontal(LocalConfiguration.current.screenWidthDp.dp, state))
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
    withBottomNavigation: Boolean = false,
    showPrimaryHeader: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = LocalHeyboxTheme.current
    val scrollState = rememberScrollState()
    val rotary = LocalHeyboxRotaryRequest.current
    LaunchedEffect(rotary?.serial) {
        if (rotary != null) scrollState.scrollBy(rotary.distance.toFloat())
    }
    if (state.roundScreen) {
        // Round safety space belongs to the scrolling content, never to a fixed toolbar.
        val contentTop = if (showPrimaryHeader) 0.dp else
            watchHeaderTop(LocalConfiguration.current.screenWidthDp.dp, state) + watchDp(8)
        Box(modifier.fillMaxSize().background(state.background)) {
            Column(Modifier.fillMaxSize().testTag("watch-page-content").verticalScroll(scrollState)) {
                if (showPrimaryHeader) ComposePageHeader(title, onBack)
                Column(Modifier.fillMaxWidth().watchHorizontalPadding()
                    .padding(top = contentTop, bottom = watchListEndPadding(withBottomNavigation)),
                    verticalArrangement = Arrangement.spacedBy(watchDp(9)),
                    horizontalAlignment = if (showPrimaryHeader) Alignment.Start else Alignment.CenterHorizontally,
                    content = content)
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(state.background),
    ) {
        if (showPrimaryHeader) ComposePageHeader(title, onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("watch-page-content")
                .verticalScroll(scrollState)
                .watchHorizontalPadding()
                .padding(bottom = watchListEndPadding(withBottomNavigation)),
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
        shape = RoundedCornerShape(watchDp(WatchRadius.LARGE)),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) state.panelElevated else state.panel,
        ),
        content = {
            Column(
                modifier = Modifier.padding(
                    horizontal = watchDp(5),
                    vertical = watchDp(4),
                ),
                content = content,
            )
        },
    )
}

@Composable
internal fun WatchSectionTitle(text: String) {
    Text(
        text = text,
        color = LocalHeyboxTheme.current.muted,
        fontSize = watchSp(WatchType.CAPTION),
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = watchDp(3), top = watchDp(2)),
    )
}

/**
 * The only list row: settings, profile and the check-in pages share it, so heights,
 * insets and type match everywhere. [danger] tints a destructive entry.
 */
@Composable
internal fun WatchRow(
    title: String,
    value: String = "",
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    subtitle: String = "",
    danger: Boolean = false,
    chevron: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val state = LocalHeyboxTheme.current
    val tone = if (!enabled) state.subtle else if (danger) state.danger else state.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(watchDp(WatchRadius.MEDIUM)))
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = watchTouchDp(40))
            .padding(horizontal = watchDp(11), vertical = watchDp(7)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (danger && enabled) state.danger else state.muted,
                modifier = Modifier.size(watchDp(WatchIcon.LARGE)),
            )
            Spacer(modifier = Modifier.width(watchDp(8)))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = tone,
                fontSize = watchSp(WatchType.BODY),
                lineHeight = watchLineHeight(WatchType.BODY),
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = state.muted, fontSize = watchSp(WatchType.CAPTION),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = watchDp(2)))
            }
        }
        if (value.isNotEmpty()) {
            Text(
                value,
                modifier = Modifier
                    .widthIn(max = watchDp(92))
                    .padding(start = watchDp(6)),
                color = state.muted,
                fontSize = watchSp(WatchType.SECONDARY),
                maxLines = 2,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (chevron) {
            Spacer(Modifier.width(watchDp(5)))
            Icon(painterResource(R.drawable.il_chevron), null, tint = state.subtle,
                modifier = Modifier.size(watchDp(WatchIcon.SMALL)))
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
            .clip(RoundedCornerShape(watchDp(WatchRadius.MEDIUM)))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch,
                onValueChange = onCheckedChange)
            .heightIn(min = watchTouchDp(40))
            .padding(horizontal = watchDp(11), vertical = watchDp(7)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = state.muted,
                modifier = Modifier.size(watchDp(WatchIcon.LARGE)),
            )
            Spacer(modifier = Modifier.width(watchDp(8)))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (enabled) state.text else state.subtle,
            fontSize = watchSp(WatchType.BODY),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        WatchSwitch(checked, enabled)
    }
}

/** The only switch drawing. Rows own the toggle semantics and the touch target. */
@Composable
internal fun WatchSwitch(checked: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val state = LocalHeyboxTheme.current
    val thumbPosition by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(if (Motions.off()) 0 else 160),
        label = "setting-switch",
    )
    val track = if (checked) state.accent else state.switchTrack
    Canvas(modifier = modifier.size(width = watchDp(36), height = watchDp(22))
        .clip(RoundedCornerShape(50))
        .background(if (enabled) track else track.copy(alpha = 0.45f))) {
        val inset = size.height / 2f
        drawCircle(
            color = if (enabled) state.switchThumb else state.subtle,
            radius = size.height * 9f / 22f,
            center = Offset(inset + (size.width - 2f * inset) * thumbPosition, inset),
        )
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
        shape = RoundedCornerShape(watchDp(WatchRadius.MEDIUM)),
        colors = CardDefaults.cardColors(containerColor = state.panelElevated),
    ) {
        Column(modifier = Modifier.padding(watchDp(11))) {
            Text(label, color = state.muted, fontSize = watchSp(WatchType.CAPTION), maxLines = 1)
            Spacer(modifier = Modifier.height(watchDp(3)))
            Text(value, color = state.text, fontSize = watchSp(WatchType.SUBTITLE),
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A labelled count such as followers, laid out in rows of three on account cards. */
@Composable
internal fun WatchStat(label: String, value: Int, modifier: Modifier = Modifier) {
    val state = LocalHeyboxTheme.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Format.commentLikeCount(value.coerceAtLeast(0)), color = state.text,
            fontSize = watchSp(WatchType.SUBTITLE), lineHeight = watchLineHeight(WatchType.SUBTITLE),
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(label, color = state.muted, fontSize = watchSp(WatchType.CAPTION),
            lineHeight = watchLineHeight(WatchType.CAPTION), textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = watchDp(1)))
    }
}

@Composable
internal fun WatchActionText(text: String, onClick: () -> Unit) {
    WatchActionButton(text, onClick)
}
