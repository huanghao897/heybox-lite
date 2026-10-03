package com.ronan.heyboxlite

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun ComposeVideoPlayerScreen(
    state: ComposeVideoPlayerUiState,
    crop: Boolean,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: () -> Unit,
    onRetry: () -> Unit,
    onExternalPlayer: () -> Unit,
    onHideControls: () -> Unit,
    modifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier,
    videoSurface: @Composable (Modifier) -> Unit = {},
    coverContent: @Composable (String, Modifier) -> Unit = { url, bounds ->
        ComposeMediaImage(url, ComposeMediaSettings(imageTargetPx = 900), bounds,
            shape = RectangleShape, contentScale = if (crop) androidx.compose.ui.layout.ContentScale.Crop
                else androidx.compose.ui.layout.ContentScale.Fit,
            contentDescription = "视频封面", placeholderLabel = "", ensureTouchTarget = false)
    },
) {
    val theme = LocalHeyboxTheme.current
    val showControls = state.controlsVisible || state.error.isNotEmpty()
    val animatedAlpha by animateFloatAsState(if (showControls) 1f else 0f,
        animationSpec = if (theme.motionLevel == MotionLevel.OFF) snap()
            else tween(MotionSpec.PRESS_OUT_MS.toInt()), label = "video-controls-fade")
    val controlsAlpha = if (theme.motionLevel == MotionLevel.OFF)
        if (showControls) 1f else 0f else animatedAlpha
    LaunchedEffect(state.playing, state.controlsVisible, state.fastForwarding,
        state.seekingMs != null, state.error) {
        if (state.playing && state.controlsVisible && !state.fastForwarding &&
            state.seekingMs == null && state.error.isEmpty()) {
            delay(3500)
            onHideControls()
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black).testTag("video-player")) {
        val width = maxWidth
        val height = maxHeight
        val inset = if (theme.roundScreen) minOf(width, height) * 0.12f else watchDp(8)
        val headerHeight = maxOf(watchDp(40), with(LocalDensity.current) {
            watchSp(18f).toDp()
        } + watchDp(8))
        val compact = theme.roundScreen || width < watchDp(260) * theme.textScale *
            LocalDensity.current.fontScale
        val footerHeight = minOf(watchDp(if (compact) 72 else 52),
            (height - inset * 2 - headerHeight - watchDp(4)).coerceAtLeast(watchDp(36)))
        val headerInset = if (theme.roundScreen) videoPlayerCircleInset(width.value,
            height.value, inset.value, (inset + headerHeight).value).dp else inset
        val footerTop = height - inset - footerHeight
        val footerInset = if (theme.roundScreen) videoPlayerCircleInset(width.value,
            height.value, footerTop.value, (height - inset).value).dp else inset
        val gap = footerTop - inset - headerHeight
        val inlineBuffering = gap < watchDp(28)
        val (mediaWidth, mediaHeight) = videoPlayerFit(width.value, height.value,
            state.videoWidth, state.videoHeight, crop)

        Box(Modifier.fillMaxSize().then(gestureModifier)
            .testTag("video-gesture-surface").semantics { contentDescription = "视频播放画面" }) {
            val bounds = Modifier.align(Alignment.Center).size(mediaWidth.dp, mediaHeight.dp)
            videoSurface(bounds)
            if (state.coverVisible && state.coverUrl.isNotBlank())
                coverContent(state.coverUrl, bounds.testTag("video-cover"))
        }
        if (showControls || controlsAlpha > 0f) {
            val chrome = Modifier.graphicsLayer { alpha = controlsAlpha }.then(
                if (showControls) Modifier else Modifier.clearAndSetSemantics {})
            ComposeVideoPlayerHeader(state, onBack, inlineBuffering, showControls,
                Modifier.align(Alignment.TopCenter).padding(top = inset)
                    .width((width - headerInset * 2).coerceAtLeast(0.dp)).height(headerHeight).then(chrome))
            if (state.error.isEmpty()) {
                ComposeVideoPlayerTimeline(state, compact, onTogglePlayback, onSeekPreview, onSeekCommit,
                    showControls,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = inset)
                        .width((width - footerInset * 2).coerceAtLeast(0.dp)).height(footerHeight)
                        .then(chrome).verticalScroll(rememberScrollState(), enabled = showControls))
            }
        }
        if (state.buffering && state.error.isEmpty() && !state.fastForwarding &&
            (!inlineBuffering || !state.controlsVisible)) {
            CircularProgressIndicator(color = theme.accent, strokeWidth = watchDp(2),
                modifier = Modifier.align(Alignment.Center).size(watchDp(28))
                    .testTag("video-buffering").semantics { contentDescription = "视频正在缓冲" })
        }
        if (state.error.isNotEmpty()) {
            val top = inset + headerHeight + watchDp(6)
            val errorInset = if (theme.roundScreen) videoPlayerCircleInset(width.value,
                height.value, top.value, (height - inset).value).dp else inset
            ComposeVideoPlayerError(state.error, state.externalAvailable, onRetry, onExternalPlayer,
                Modifier.fillMaxSize().padding(start = errorInset, end = errorInset,
                    top = top, bottom = inset))
        }
    }
}
