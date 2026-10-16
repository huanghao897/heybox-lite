package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal data class ComposeDetailBodyRow(val index: Int, val blocks: List<RichContent.Block>)

/** Only group adjacent images when the original parser selected its gallery mode. */
internal fun composeDetailBodyRows(blocks: List<RichContent.Block>, pager: Boolean): List<ComposeDetailBodyRow> =
    buildList {
        var index = 0
        while (index < blocks.size) {
            val start = index++
            if (pager && blocks[start].image) {
                while (index < blocks.size && blocks[index].image) index++
            }
            add(ComposeDetailBodyRow(start, blocks.subList(start, index)))
        }
    }

internal fun composeDetailImageHeight(width: Float, aspect: Float, uiScale: Float): Float =
    (width * aspect.coerceIn(0.3f, 2.8f)).coerceIn(114f * uiScale, 360f * uiScale)

@Composable
internal fun ComposeDetailImage(
    url: String,
    media: ComposeMediaSettings,
    onOpenImage: (String) -> Unit,
    requests: ComposeMediaImageRequests = ExistingComposeMediaImageRequests,
    onOpenPreview: ((String, android.widget.ImageView) -> Unit)? = null,
) {
    if (!media.enabled) return
    val theme = LocalHeyboxTheme.current
    var aspect by remember(url) { mutableFloatStateOf(0.5625f) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = composeDetailImageHeight(maxWidth.value, aspect, theme.uiScale).dp
        ComposeMediaImage(url, media, Modifier.fillMaxWidth().height(height)
            .testTag("detail-image-$url"), contentScale = ContentScale.Fit,
            contentDescription = "打开正文图片", animated = GifSupport.isGifUrl(url),
            requests = requests, onImageSize = { width, imageHeight ->
                if (width > 0 && imageHeight > 0) aspect = imageHeight.toFloat() / width
            }, onClick = { onOpenImage(url) },
            onPreviewClick = onOpenPreview?.let { open -> { view -> open(url, view) } })
    }
}

@Composable
internal fun ComposeDetailImagePager(
    urls: List<String>,
    media: ComposeMediaSettings,
    onOpenImage: (String) -> Unit,
    requests: ComposeMediaImageRequests = ExistingComposeMediaImageRequests,
    onOpenPreview: ((String, android.widget.ImageView) -> Unit)? = null,
) {
    if (!media.enabled || urls.isEmpty()) return
    val theme = LocalHeyboxTheme.current
    val pager = rememberPagerState { urls.size }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = if (theme.roundScreen) minOf(watchDp(150), maxWidth * 0.58f)
            else minOf(watchDp(270), maxWidth * 0.85f)
        Box(Modifier.fillMaxWidth().height(height)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                ComposeMediaImage(urls[page], media, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, contentDescription = "打开正文图片",
                    animated = page == pager.settledPage && GifSupport.isGifUrl(urls[page]),
                    requests = requests, onClick = { onOpenImage(urls[page]) },
                    onPreviewClick = onOpenPreview?.let { open -> { view -> open(urls[page], view) } })
            }
            Text("${pager.currentPage + 1}/${urls.size}", color = Color.White,
                fontSize = watchSp(10f), modifier = Modifier.align(Alignment.TopEnd)
                    .padding(watchDp(6)).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = watchDp(6), vertical = watchDp(2)))
        }
    }
}
