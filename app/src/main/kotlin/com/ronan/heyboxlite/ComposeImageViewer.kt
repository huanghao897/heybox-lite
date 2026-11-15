package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal class ComposeViewerSelection {
    var page by mutableStateOf<ComposeViewerPageState?>(null)
    var transform by mutableStateOf<ComposeViewerTransform?>(null)
    var index by mutableStateOf(0)
    var closing by mutableStateOf(false)

    fun back() {
        val state = transform
        if (state?.zoomed == true) state.reset() else closing = true
    }
}

@Composable
internal fun ComposeImageViewer(
    urls: List<String>,
    initialIndex: Int,
    preview: Bitmap?,
    round: Boolean,
    originalEnabled: Boolean,
    immediateOriginal: Boolean,
    active: Boolean,
    motion: Int,
    selection: ComposeViewerSelection,
    onSave: (String) -> Unit,
    onFinish: () -> Unit,
    onSourceVisibility: (Boolean) -> Unit = {},
    requests: ViewerImageRequests? = null,
    origin: RectF? = null,
    playGif: Boolean = true,
) {
    val pager = rememberPagerState(initialPage = initialIndex, pageCount = { urls.size })
    val context = LocalContext.current
    val imageRequests = requests ?: remember(context) { ExistingViewerImageRequests(context) }
    val enter = remember { Animatable(if (motion == MotionLevel.OFF) 1f else 0f) }
    val exit = remember { Animatable(1f) }
    var ready by remember { mutableStateOf(preview != null) }
    var width by remember { mutableStateOf(0) }
    val duration = if (motion == MotionLevel.OFF) 0 else 200
    LaunchedEffect(ready, selection.closing) {
        if (ready && !selection.closing) {
            withFrameNanos { }
            onSourceVisibility(false)
            enter.animateTo(1f, tween(duration))
        }
    }
    LaunchedEffect(selection.closing) {
        if (selection.closing) {
            exit.animateTo(0f, tween(duration))
            onSourceVisibility(true)
            onFinish()
        }
    }
    DisposableEffect(Unit) { onDispose { onSourceVisibility(true) } }
    val pull = selection.transform?.dismiss?.y ?: 0f
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { width = it.width }
        .testTag("image-viewer")) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = enter.value * exit.value * (1f - (kotlin.math.abs(pull) /
                size.height.coerceAtLeast(1f)).coerceIn(0f, .65f))
        }.background(Color(0xFF101113)))
        if (width > 0) HorizontalPager(
            state = pager,
            beyondViewportPageCount = 0,
            userScrollEnabled = active && !selection.closing && selection.transform?.zoomed != true,
            modifier = Modifier.fillMaxSize(),
            key = { it },
        ) { index ->
            val state = remember(index, urls[index]) {
                ComposeViewerPageState(urls[index], width.coerceIn(480, 1440),
                    if (index == initialIndex) preview else null, imageRequests)
            }
            val transform = remember(index) { ComposeViewerTransform() }
            val selected = index == pager.currentPage
            if (selected) SideEffect {
                selection.index = index
                selection.page = state
                selection.transform = transform
            }
            DisposableEffect(state) {
                if (selected && active) state.start() else state.setActive(false)
                onDispose {
                    state.close()
                    if (selection.page === state) { selection.page = null; selection.transform = null }
                }
            }
            LaunchedEffect(state, active, selected, selection.closing) {
                state.setActive(active && selected && !selection.closing)
            }
            LaunchedEffect(state, enter.value == 1f, selected, active, selection.closing) {
                if (selected && active && !selection.closing && enter.value == 1f)
                    state.enterCompleted(immediateOriginal, playGif)
            }
            LaunchedEffect(state.bitmap, state.failed, selected) {
                if (selected && (state.bitmap != null || state.failed)) ready = true
            }
            ComposeViewerImagePage(state, transform, round, active && selected && !selection.closing,
                enter.value == 1f, motion, onDismiss = { selection.closing = true },
                transitionOrigin = if (index == initialIndex && state.bitmap != null &&
                    !transform.zoomed && state.longSource == null)
                    origin else null,
                transitionProgress = {
                    (if (index == initialIndex && state.bitmap != null) enter.value else 1f) * exit.value
                })
        }
        if (!selection.closing) {
            ComposeViewerChrome(round, pager.currentPage, urls.size, originalEnabled,
                selection.page?.original == true, selection.page?.loading == true,
                onOriginal = { selection.page?.loadOriginal() },
                onSave = { onSave(urls[pager.currentPage]) },
                onClose = { selection.closing = true }, imageAvailable = selection.page?.bitmap != null)
        }
    }
}

@Composable
private fun ComposeViewerImagePage(
    state: ComposeViewerPageState,
    transform: ComposeViewerTransform,
    round: Boolean,
    active: Boolean,
    entered: Boolean,
    motion: Int,
    onDismiss: () -> Unit,
    transitionOrigin: RectF?,
    transitionProgress: () -> Float,
) {
    val bitmap = state.bitmap
    var viewport by remember { mutableStateOf(Size.Zero) }
    var readyLongSource by remember(state) { mutableStateOf<LongImageSource?>(null) }
    val longViewport = remember(state) { ComposeLongImageViewport() }
    val longSource = state.longSource
    val retainedSource = state.retainedLongSource
    val showingLong = longSource != null && (readyLongSource === longSource || retainedSource != null)
    SideEffect {
        transform.layout(viewport,
            if (showingLong || bitmap == null) viewport
            else Size(bitmap.width.toFloat(), bitmap.height.toFloat()), round)
    }
    Box(Modifier.fillMaxSize().testTag("viewer-image-page")
        .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
        .viewerTransition(transform, transitionOrigin, transitionProgress)
        .then(if (active && !showingLong && (entered || bitmap == null))
            Modifier.viewerGestures(transform, false, onDismiss, {}, motion = motion,
                zoomEnabled = entered && bitmap != null)
            else Modifier)
        .graphicsLayer {
            val progress = ImageDismissPolicy.progress(transform.dismiss.y, size.height.toInt())
            val pullScale = ImageDismissPolicy.scale(progress)
            scaleX = (if (showingLong) 1f else transform.zoom) * pullScale
            scaleY = (if (showingLong) 1f else transform.zoom) * pullScale
            translationX = (if (showingLong) 0f else transform.pan.x) + transform.dismiss.x
            translationY = (if (showingLong) 0f else transform.pan.y) + transform.dismiss.y
        }, contentAlignment = Alignment.Center) {
        val animation = state.animation
        if (animation != null) {
            ComposeViewerGif(animation, active, Modifier.fillMaxSize(if (round) .8f else 1f))
        } else if (bitmap != null && !showingLong) {
            val inset = if (round) .8f else 1f
            Image(bitmap.asImageBitmap(), null,
                Modifier.fillMaxSize(inset).testTag("viewer-bitmap"),
                contentScale = ContentScale.Fit, filterQuality = FilterQuality.Medium)
        }
        if (entered) {
            // A keyed layer keeps its View and decoded tiles when it becomes the retained source.
            for (source in listOfNotNull(retainedSource, longSource)) key(source) {
                ComposeViewerLongImage(source, active, round, transform,
                    onReady = {
                        if (source === state.longSource) {
                            readyLongSource = source
                            state.regionReady(source)
                        }
                    },
                    onFailed = { state.regionFailed(source) },
                    onDismiss = onDismiss, viewport = longViewport,
                    interactive = source === longSource, motion = motion)
            }
        }
        if (bitmap == null && state.loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
        if (state.failed) {
            WatchActionButton(if (bitmap == null) "重试图片" else "重试清晰图", state::retry,
                modifier = Modifier.align(Alignment.Center))
        }
        if (state.longFailed && !state.failed) {
            WatchActionButton("重试长图", state::retryLongImage,
                modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Preview(name = "Viewer round", widthDp = 227, heightDp = 227)
@Composable
private fun ViewerRoundPreview() {
    HeyboxComposeTheme(composePreviewTheme(true)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101113))) {
            ComposeViewerChrome(true, 0, 3, true, false, false, {}, {}, {})
        }
    }
}

@Preview(name = "Viewer square", widthDp = 240, heightDp = 320)
@Composable
private fun ViewerSquarePreview() {
    HeyboxComposeTheme(composePreviewTheme(false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101113))) {
            ComposeViewerChrome(false, 0, 3, true, false, false, {}, {}, {})
        }
    }
}
