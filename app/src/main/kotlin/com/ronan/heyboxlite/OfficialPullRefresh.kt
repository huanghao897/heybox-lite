@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ronan.heyboxlite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/** The gesture is Material's nested-scroll implementation; the header uses the official asset. */
@Composable
internal fun OfficialPullRefreshBox(
    refreshing: Boolean,
    enabled: Boolean,
    active: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val state = remember { OfficialPullRefreshState() }
    var requestPending by remember { mutableStateOf(false) }
    var gestureCancelled by remember { mutableStateOf(false) }
    val showingRefresh = refreshing || requestPending
    state.animateTransitions = theme.motionLevel != MotionLevel.OFF
    LaunchedEffect(requestPending) {
        if (requestPending) {
            // Material must observe a refresh cycle even when the request finishes synchronously
            // or is declined because a page is already loading.
            withFrameNanos { }
            requestPending = false
        }
    }
    LaunchedEffect(theme.motionLevel) {
        if (theme.motionLevel == MotionLevel.OFF) state.snapTo(if (showingRefresh) 1f else 0f)
    }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val headerHeight = minOf(watchDp(70), maxHeight * 0.28f)
        val headerPx = with(LocalDensity.current) { headerHeight.toPx() }
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            // Compose translates ACTION_CANCEL into consumed up changes. Observe
            // the initial pass without consuming or calculating any drag offsets.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                gestureCancelled = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.none { it.pressed }) {
                        gestureCancelled = event.changes.any { it.previousPressed && it.isConsumed }
                    }
                } while (event.changes.any { it.pressed })
            }
        }.pullToRefresh(
            isRefreshing = showingRefresh,
            state = state,
            enabled = enabled,
            threshold = headerHeight,
            onRefresh = {
                requestPending = true
                if (enabled && active && !gestureCancelled) onRefresh()
            },
        )) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationY = state.distanceFraction * headerPx
            }, content = content)
            OfficialRefreshHeader(
                state = state,
                refreshing = showingRefresh,
                active = active,
                animationSize = headerHeight * (5f / 7f),
                modifier = Modifier.fillMaxWidth().height(headerHeight).graphicsLayer {
                    translationY = (state.distanceFraction - 1f) * headerPx
                },
            )
        }
    }
}

@Stable
internal class OfficialPullRefreshState : PullToRefreshState {
    private val position = Animatable(0f)
    var animateTransitions = true
    override val distanceFraction: Float get() = position.value
    override val isAnimating: Boolean get() = position.isRunning

    override suspend fun animateToThreshold() = settle(1f)
    override suspend fun animateToHidden() = settle(0f)
    override suspend fun snapTo(targetValue: Float) { position.snapTo(targetValue) }

    private suspend fun settle(target: Float) {
        position.animateTo(target, tween(if (animateTransitions) 250 else 0,
            easing = LinearOutSlowInEasing))
    }
}

@Composable
private fun OfficialRefreshHeader(
    state: PullToRefreshState,
    refreshing: Boolean,
    active: Boolean,
    animationSize: Dp,
    modifier: Modifier,
) {
    val theme = LocalHeyboxTheme.current
    val result = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.official_pull_refresh))
    val playing = active && theme.motionLevel != MotionLevel.OFF &&
        (refreshing || state.distanceFraction > 0.4f)
    val animatedProgress by animateLottieCompositionAsState(
        composition = result.value,
        isPlaying = playing,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = true,
    )
    Box(modifier.semantics {
        if (refreshing || state.distanceFraction > 0f) {
            contentDescription = "下拉刷新"
            stateDescription = when {
                refreshing -> "正在刷新"
                state.distanceFraction >= 1f -> "松开刷新"
                else -> "下拉刷新"
            }
            progressBarRangeInfo = if (refreshing) ProgressBarRangeInfo.Indeterminate else
                ProgressBarRangeInfo(state.distanceFraction.coerceIn(0f, 1f), 0f..1f)
        }
    }, contentAlignment = Alignment.Center) {
        if (result.isFailure) {
            Icon(painterResource(R.drawable.il_refresh), null,
                modifier = Modifier.size(watchDp(24)), tint = theme.muted)
        } else {
            LottieAnimation(
                composition = result.value,
                progress = {
                    if (state.distanceFraction == 0f || theme.motionLevel == MotionLevel.OFF) 0f
                    else animatedProgress
                },
                modifier = Modifier.size(animationSize),
                renderMode = RenderMode.AUTOMATIC,
            )
        }
    }
}
