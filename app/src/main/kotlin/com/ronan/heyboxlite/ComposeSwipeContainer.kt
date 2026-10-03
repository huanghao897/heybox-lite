package com.ronan.heyboxlite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp

/** Owns the single Compose horizontal gesture owner and its interactive preview layer. */
@Composable
internal fun ComposeSwipeContainer(
    host: ComposeAppHost,
    route: String,
    services: ComposeServices,
    content: @Composable () -> Unit,
) {
    val swipeOffset = remember { mutableStateOf(0f) }
    val swipeDirection = remember { mutableStateOf(0) }
    val previewRoute = remember { mutableStateOf<String?>(null) }
    val pendingTarget = remember { mutableStateOf<String?>(null) }
    val settleCommit = remember { mutableStateOf(false) }
    val settleToken = remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val backEdge = with(density) { (36.dp * services.theme.uiScale).toPx() }
    val backSwipeEnabled = host.composeSwipeEnabled(route)
    val edge = if (route == "feed" || route == "profile") 0f else backEdge

    LaunchedEffect(settleToken.value) {
        if (settleToken.value == 0) return@LaunchedEffect
        val direction = swipeDirection.value
        val target = pendingTarget.value
        if (direction == 0 || target == null) {
            previewRoute.value = null
            swipeOffset.value = 0f
            swipeDirection.value = 0
            settleToken.value = 0
            return@LaunchedEffect
        }
        val width = host.contentWidthPx().coerceAtLeast(1f)
        val destination = if (settleCommit.value) direction * width else 0f
        val animation = Animatable(swipeOffset.value)
        animation.animateTo(
            destination,
            tween(durationMillis = if (services.theme.uiScale < 0.95f) 190 else 220),
        ) {
            swipeOffset.value = value
        }
        if (settleCommit.value) host.completeComposeSwipe(target)
        pendingTarget.value = null
        previewRoute.value = null
        swipeOffset.value = 0f
        swipeDirection.value = 0
        settleCommit.value = false
        settleToken.value = 0
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(services.theme.background)
            .composeBackSwipe(
                route = route,
                enabled = backSwipeEnabled,
                edgePx = edge,
                thresholdPx = with(density) { 44.dp.toPx() * services.theme.uiScale },
                touchSlopPx = touchSlop,
                canStart = { direction ->
                    host.composeSwipeTarget(route, direction) != null
                },
                onStart = { direction ->
                    if (settleToken.value != 0) return@composeBackSwipe false
                    val target = host.composeSwipeTarget(route, direction)
                        ?: return@composeBackSwipe false
                    swipeDirection.value = direction
                    pendingTarget.value = target
                    previewRoute.value = target.takeUnless { it.isEmpty() }
                    swipeOffset.value = 0f
                    true
                },
                onProgress = { swipeOffset.value = it },
                onCancel = {
                    if (pendingTarget.value != null && settleToken.value == 0) {
                        settleCommit.value = false
                        settleToken.value++
                    }
                },
                onComplete = {
                    settleCommit.value = true
                    settleToken.value++
                },
            ),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val preview = previewRoute.value?.let(host::transitionPreview)
        if (previewRoute.value != null) {
            val previewTranslation = if (swipeDirection.value > 0) {
                -widthPx + swipeOffset.value
            } else {
                widthPx + swipeOffset.value
            }
            if (preview != null && !preview.isRecycled) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationX = previewTranslation
                    },
                )
            } else if (previewRoute.value == "profile") {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationX = previewTranslation },
                ) {
                    ComposeProfileScreen(services, host::navigate)
                }
            } else if (previewRoute.value == "feed") {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationX = previewTranslation },
                ) {
                    host.feedRevision.value
                    val items by host.feed.items
                    val loading by host.feed.loading
                    val refreshing by host.feed.refreshing
                    val noMore by host.feed.noMore
                    ComposeFeedScreen(
                        items,
                        loading,
                        refreshing,
                        noMore,
                        services,
                        onOpen = host::openDetail,
                        onRefresh = host.feed::refresh,
                        onLoadMore = host.feed::loadMore,
                        onSearch = { host.navigate("search") },
                        onAction = { item, action ->
                            host.callbacks.feedAction(
                                item,
                                when (action) {
                                    FeedAction.LIKE -> ComposeAppCallbacks.ACTION_LIKE
                                    FeedAction.FAVORITE -> ComposeAppCallbacks.ACTION_FAVORITE
                                    FeedAction.CACHE -> ComposeAppCallbacks.ACTION_CACHE
                                    FeedAction.FOLLOW -> ComposeAppCallbacks.ACTION_FOLLOW
                                    FeedAction.COMMENT -> ComposeAppCallbacks.ACTION_COMMENT
                                },
                            )
                        },
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(services.theme.background)
                        .graphicsLayer { translationX = previewTranslation },
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // The outgoing page follows the finger one-to-one. The
                // previous page is already underneath it, so there is no
                // reset-to-zero frame or black strip on commit.
                translationX = swipeOffset.value
            },
        ) {
            content()
        }
    }
}
