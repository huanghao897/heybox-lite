package com.ronan.heyboxlite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp

/** A live target keeps its composition when it becomes the current page. */
@Composable
internal fun ComposeSwipeContainer(
    host: ComposeAppHost,
    route: String,
    services: ComposeServices,
    content: @Composable (route: String, active: Boolean) -> Unit,
) {
    val offset = remember { mutableStateOf(0f) }
    val direction = remember { mutableStateOf(0) }
    val target = remember { mutableStateOf<String?>(null) }
    val handoff = remember { mutableStateOf(false) }
    val settling = remember { mutableStateOf(false) }
    val commit = remember { mutableStateOf(false) }
    val settleSerial = remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val edge = if (route == "feed" || route == "profile") 0f else with(density) {
        maxOf(32.dp.toPx(), (52.dp * services.theme.uiScale).toPx())
    }

    fun reset() {
        offset.value = 0f
        direction.value = 0
        target.value = null
        handoff.value = false
        settling.value = false
        commit.value = false
    }

    LaunchedEffect(settleSerial.value) {
        if (settleSerial.value == 0) return@LaunchedEffect
        val destinationRoute = target.value ?: return@LaunchedEffect
        val width = host.contentWidthPx().coerceAtLeast(1f)
        val destination = if (commit.value) direction.value * width else 0f
        val animation = Animatable(offset.value)
        animation.animateTo(destination, tween(
            durationMillis = if (Motions.off()) 0 else 220,
            easing = LinearOutSlowInEasing,
        )) { offset.value = value }
        if (commit.value && destinationRoute.isNotEmpty()) {
            handoff.value = true
            host.completeComposeSwipe(destinationRoute)
        } else {
            if (commit.value) host.completeComposeSwipe(destinationRoute)
            reset()
        }
    }

    LaunchedEffect(route, handoff.value) {
        if (!handoff.value || route != target.value?.substringBefore('?')) return@LaunchedEffect
        // The target is the same keyed composition before and after the route change.
        withFrameNanos { }
        reset()
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(services.theme.background)) {
        val width = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val destinationRoute = target.value
        val snapshot = destinationRoute?.takeUnless(ComposeSwipePresentation::isLiveRoute)
            ?.let(host::transitionPreview)
        Box(Modifier.fillMaxSize().composeBackSwipe(
            route = route,
            enabled = host.composeSwipeEnabled(route),
            edgePx = edge,
            thresholdPx = with(density) { 44.dp.toPx() * services.theme.uiScale },
            touchSlopPx = touchSlop,
            maxDragPx = width,
            canStart = { !settling.value && !handoff.value && host.composeSwipeTarget(route, it) != null },
            onStart = { dragDirection ->
                val next = host.composeSwipeTarget(route, dragDirection)
                if (next == null || settling.value || handoff.value) false else {
                    target.value = next
                    direction.value = dragDirection
                    offset.value = 0f
                    true
                }
            },
            onProgress = { offset.value = it },
            onCancel = {
                if (target.value != null && !settling.value) {
                    settling.value = true
                    commit.value = false
                    settleSerial.value++
                }
            },
            onComplete = {
                if (target.value != null && !settling.value) {
                    settling.value = true
                    commit.value = true
                    settleSerial.value++
                }
            },
        )) {
            if (destinationRoute != null && !ComposeSwipePresentation.isLiveRoute(destinationRoute)) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationX = ComposeSwipePresentation.translation(
                        destinationRoute, destinationRoute, direction.value, offset.value, width,
                    )
                }) {
                    if (snapshot != null && !snapshot.isRecycled) {
                        Image(snapshot.asImageBitmap(), null, Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds)
                    } else {
                        Box(Modifier.fillMaxSize().background(services.theme.background))
                    }
                }
            }
            for (page in ComposeSwipePresentation.routes(route, destinationRoute)) {
                key(page) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        translationX = ComposeSwipePresentation.translation(
                            page, target.value, direction.value, offset.value, width,
                        )
                    }) {
                        content(page, page == route && !settling.value && target.value == null)
                    }
                }
            }
        }
    }
}
