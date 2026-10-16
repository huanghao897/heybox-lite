package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ComposeDetailPagerNavigation(
    val state: PagerState,
    private val scope: CoroutineScope,
) {
    val canSwipeBack: Boolean get() = state.currentPage == 0 && !state.isScrollInProgress

    fun returnToBody(): Boolean {
        if (state.currentPage == 0 && state.currentPageOffsetFraction == 0f && state.targetPage == 0) return false
        scope.launch {
            if (Motions.off()) state.scrollToPage(0) else state.animateScrollToPage(0)
        }
        return true
    }
}

/** The route keeps ownership of returning to its live parent; this pager owns its two content pages. */
@Composable
internal fun ComposeDetailPager(
    state: PagerState,
    active: Boolean,
    visible: Boolean,
    onBack: () -> Unit,
    onNavigation: (ComposeDetailPagerNavigation?) -> Unit = {},
    body: @Composable (PaddingValues, Boolean) -> Unit,
    comments: @Composable (PaddingValues, Boolean) -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val scope = rememberCoroutineScope()
    val publishNavigation by rememberUpdatedState(onNavigation)
    val navigation = remember(state, scope) { ComposeDetailPagerNavigation(state, scope) }
    val galleryConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset,
                source: NestedScrollSource): Offset = Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    DisposableEffect(active, navigation) {
        if (active) publishNavigation(navigation)
        onDispose { if (active) publishNavigation(null) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(theme.background)
        .graphicsLayer { alpha = if (visible) 1f else 0f }) {
        val headerTop = watchHeaderTop(maxWidth, theme)
        val headerStart = watchHeaderHorizontal(maxWidth, theme)
        val backSize = watchDp(34).coerceAtLeast(28.dp)
        val inset = watchPageHorizontal(maxWidth, theme)
        val padding = PaddingValues(start = inset, end = inset,
            top = headerTop + backSize + watchDp(8), bottom = watchListEndPadding())
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize().testTag("compose-detail-pager"),
            userScrollEnabled = active,
            // A gallery owns horizontal drags that start on its images, including its end pages.
            pageNestedScrollConnection = galleryConnection,
            key = { if (it == 0) "body" else "comments" },
        ) { page ->
            val pageActive = active && page == state.currentPage
            if (page == 0) body(padding, pageActive) else comments(padding, pageActive)
        }
        IconButton(onClick = { if (!navigation.returnToBody()) onBack() },
            enabled = active,
            modifier = Modifier.padding(start = headerStart, top = headerTop)
                .size(backSize).clip(CircleShape).background(theme.panel.copy(alpha = 0.92f))) {
            Icon(painterResource(R.drawable.ic_arrow_back), "返回", tint = theme.text,
                modifier = Modifier.size(watchDp(18)))
        }
    }
}
