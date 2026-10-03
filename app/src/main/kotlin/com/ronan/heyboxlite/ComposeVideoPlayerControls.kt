@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ronan.heyboxlite

import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeVideoPlayerHeader(state: ComposeVideoPlayerUiState, onBack: () -> Unit,
    inlineBuffering: Boolean, enabled: Boolean, modifier: Modifier) {
    val theme = LocalHeyboxTheme.current
    Row(modifier.clip(RoundedCornerShape(8.dp)).background(theme.panel.copy(alpha = 0.90f))
        .padding(horizontal = watchDp(2)).testTag("video-header"),
        horizontalArrangement = Arrangement.spacedBy(watchDp(5)),
        verticalAlignment = Alignment.CenterVertically) {
        VideoPlayerIcon(R.drawable.ic_arrow_back, "返回播放器", onBack,
            Modifier.testTag("video-back"), enabled)
        if (state.fastForwarding) {
            Text("2×", color = theme.text, fontSize = watchSp(16f),
                modifier = Modifier.weight(1f).testTag("video-fast-forward")
                    .semantics { contentDescription = "2倍速播放" })
        } else {
            ComposeRichText(state.title, theme.dark, theme.text, theme.text,
                modifier = Modifier.weight(1f).testTag("video-title"), fontSize = watchSp(13f),
                lineHeight = watchSp(18f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1, ellipsize = TextUtils.TruncateAt.END)
        }
        if (inlineBuffering && state.buffering && !state.fastForwarding && state.error.isEmpty())
            CircularProgressIndicator(color = theme.accent, strokeWidth = watchDp(2),
                modifier = Modifier.size(watchDp(16)).testTag("video-buffering")
                    .semantics { contentDescription = "视频正在缓冲" })
    }
}

@Composable
internal fun ComposeVideoPlayerTimeline(state: ComposeVideoPlayerUiState, compact: Boolean,
    onToggle: () -> Unit, onPreview: (Float) -> Unit, onCommit: () -> Unit,
    enabled: Boolean, modifier: Modifier) {
    val theme = LocalHeyboxTheme.current
    val timeStyle = LocalTextStyle.current.merge(TextStyle(fontSize = watchSp(9.5f),
        lineHeight = watchSp(12f), letterSpacing = 0.sp))
    val current = videoPlayerTime(state.displayedPosition)
    val total = videoPlayerTime(state.durationMs)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widestTime = with(density) {
        maxOf(measurer.measure(AnnotatedString(current), style = timeStyle).size.width,
            measurer.measure(AnnotatedString(total), style = timeStyle).size.width).toDp()
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(8.dp)).background(theme.panel.copy(alpha = 0.90f))
        .padding(horizontal = watchDp(6), vertical = watchDp(4)).testTag("video-timeline")) {
        val stackedTimes = maxWidth < watchDp(36) + widestTime * 2 + watchDp(12)
        val separatePlay = compact && maxWidth < watchDp(40) + widestTime
        val icon = when {
            state.completed -> R.drawable.ic_replay
            state.playing -> R.drawable.ic_pause
            else -> R.drawable.ic_play
        }
        val label = when {
            state.completed -> "重新播放"
            state.playing -> "暂停"
            else -> "播放"
        }
        Column {
            if (separatePlay) {
                VideoPlayerIcon(icon, label, onToggle,
                    Modifier.align(Alignment.CenterHorizontally).testTag("video-play"), enabled)
                VideoPlayerTime(current, timeStyle, "video-current-time", Modifier.fillMaxWidth())
                VideoPlayerTime(total, timeStyle, "video-total-time", Modifier.fillMaxWidth())
            } else Row(Modifier.fillMaxWidth().heightIn(min = watchDp(36)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(watchDp(4))) {
                VideoPlayerIcon(icon, label, onToggle, Modifier.testTag("video-play"), enabled)
                if (compact && stackedTimes) {
                    Column(Modifier.weight(1f)) {
                        VideoPlayerTime(current, timeStyle, "video-current-time", Modifier.fillMaxWidth())
                        VideoPlayerTime(total, timeStyle, "video-total-time", Modifier.fillMaxWidth())
                    }
                } else if (compact) {
                    VideoPlayerTime(current, timeStyle, "video-current-time", Modifier.weight(1f))
                    VideoPlayerTime(total, timeStyle, "video-total-time", Modifier.weight(1f))
                } else {
                    VideoPlayerTime(current, timeStyle, "video-current-time", Modifier)
                    VideoPlayerSeek(state, onPreview, onCommit, enabled,
                        Modifier.weight(1f).height(watchDp(36)))
                    VideoPlayerTime(total, timeStyle, "video-total-time", Modifier)
                }
            }
            if (compact) VideoPlayerSeek(state, onPreview, onCommit, enabled,
                Modifier.fillMaxWidth().height(watchDp(28)))
        }
    }
}

@Composable
private fun VideoPlayerTime(value: String, style: TextStyle, tag: String, modifier: Modifier) {
    Text(value, color = LocalHeyboxTheme.current.text, style = style, textAlign = TextAlign.Start,
        softWrap = false, maxLines = 1,
        modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally).testTag(tag))
}

@Composable
private fun VideoPlayerSeek(state: ComposeVideoPlayerUiState, onPreview: (Float) -> Unit,
    onCommit: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val theme = LocalHeyboxTheme.current
    val limit = state.durationMs.coerceAtLeast(1).toFloat()
    val seekEnabled = enabled && state.durationMs > 0
    val colors = SliderDefaults.colors(thumbColor = theme.text, activeTrackColor = theme.accent,
        inactiveTrackColor = theme.hairline, disabledThumbColor = theme.subtle,
        disabledActiveTrackColor = theme.subtle, disabledInactiveTrackColor = theme.hairline)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Slider(value = state.displayedPosition.toFloat().coerceIn(0f, limit), onValueChange = onPreview,
            onValueChangeFinished = onCommit, enabled = seekEnabled,
            valueRange = 0f..limit,
            colors = colors,
            thumb = {
                Box(Modifier.size(watchDp(12)).clip(CircleShape)
                    .background(if (seekEnabled) theme.text else theme.subtle))
            },
            track = { slider ->
                SliderDefaults.Track(sliderState = slider, enabled = seekEnabled, colors = colors,
                    modifier = Modifier.height(watchDp(3)), thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp, drawStopIndicator = null)
            },
            modifier = modifier.testTag("video-seek").semantics {
                contentDescription = "播放进度"
                stateDescription = "${videoPlayerTime(state.displayedPosition)} / ${videoPlayerTime(state.durationMs)}"
            })
    }
}

@Composable
internal fun ComposeVideoPlayerError(message: String, external: Boolean, onRetry: () -> Unit,
    onExternal: () -> Unit, modifier: Modifier) {
    val theme = LocalHeyboxTheme.current
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(theme.panel.copy(alpha = 0.95f))
        .verticalScroll(rememberScrollState()).padding(watchDp(6)).testTag("video-error"),
        verticalArrangement = Arrangement.spacedBy(watchDp(6)),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = theme.text, fontSize = watchSp(12f), lineHeight = watchSp(17f),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        VideoPlayerCommand("重试", R.drawable.il_refresh, onRetry, "video-retry")
        if (external) VideoPlayerCommand("凉腕播放器", R.drawable.ic_play, onExternal, "video-external")
    }
}

@Composable
private fun VideoPlayerCommand(label: String, icon: Int, onClick: () -> Unit, tag: String) {
    val theme = LocalHeyboxTheme.current
    Row(Modifier.fillMaxWidth().heightIn(min = watchDp(36)).clip(RoundedCornerShape(8.dp))
        .background(theme.panelElevated).clickable(role = Role.Button, onClick = onClick)
        .testTag(tag).padding(horizontal = watchDp(6), vertical = watchDp(5)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(watchDp(5))) {
        Icon(painterResource(icon), null, tint = theme.text, modifier = Modifier.size(watchDp(16)))
        Text(label, color = theme.text, fontSize = watchSp(11f), lineHeight = watchSp(16f),
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VideoPlayerIcon(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier,
    enabled: Boolean = true) {
    val theme = LocalHeyboxTheme.current
    Box(modifier.size(watchDp(36)).clip(CircleShape).background(theme.panelElevated)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label },
        contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = theme.text, modifier = Modifier.size(watchDp(18)))
    }
}
