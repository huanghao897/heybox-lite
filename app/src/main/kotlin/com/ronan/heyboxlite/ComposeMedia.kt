package com.ronan.heyboxlite

import android.widget.ImageView as AndroidImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Media knobs are derived from SessionStore by the Compose host. */
internal data class ComposeMediaSettings(
    val enabled: Boolean = true,
    val playGif: Boolean = true,
    val imageTargetPx: Int = 720,
    val gameCardNoImage: Boolean = false,
)

@Composable
internal fun ComposeMediaImage(
    url: String,
    settings: ComposeMediaSettings,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String = "图片",
    animated: Boolean = false,
    placeholderLabel: String = "图片",
    ensureTouchTarget: Boolean = true,
    animatedUrl: String = url,
    retry: Int = 0,
    requests: ComposeMediaImageRequests = ExistingComposeMediaImageRequests,
    onLoaded: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val theme = LocalHeyboxTheme.current
    val surface = theme.panelElevated
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(onClick = onClick)
    }
    val targetModifier = if (ensureTouchTarget) {
        Modifier.heightIn(min = 48.dp)
    } else {
        Modifier
    }
    BoxWithConstraints(
        modifier = modifier
            .then(targetModifier)
            .clip(shape)
            .background(surface)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank() || !settings.enabled) {
            if (placeholderLabel.isNotBlank()) {
                Text(
                    text = placeholderLabel,
                    color = theme.muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            val density = LocalDensity.current
            val ceiling = settings.imageTargetPx.coerceAtLeast(96)
            val targetPx = if (constraints.hasBoundedWidth) {
                with(density) { maxWidth.toPx().toInt() }.coerceIn(96, ceiling)
            } else ceiling
            val request = ComposeMediaImageRequest(url, targetPx,
                if (animated && settings.playGif) animatedUrl else "", retry)
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    GifImageView(context).apply {
                        scaleType = if (contentScale == ContentScale.Fit) {
                            AndroidImageView.ScaleType.FIT_CENTER
                        } else {
                            AndroidImageView.ScaleType.CENTER_CROP
                        }
                        this.contentDescription = contentDescription
                        setBackgroundColor(surface.toArgb())
                    }
                },
                onReset = ::resetComposeMediaImage,
                onRelease = ::resetComposeMediaImage,
                update = { view ->
                    view.scaleType = if (contentScale == ContentScale.Fit) {
                        AndroidImageView.ScaleType.FIT_CENTER
                    } else {
                        AndroidImageView.ScaleType.CENTER_CROP
                    }
                    view.contentDescription = contentDescription
                    view.setBackgroundColor(surface.toArgb())
                    bindComposeMediaImage(view, request, requests, onLoaded)
                },
            )
        }
    }
}

@Composable
internal fun ComposeVideoCard(
    video: VideoData,
    settings: ComposeMediaSettings,
    modifier: Modifier = Modifier,
    onOpenVideo: (VideoData) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .aspectRatio(16f / 9f)
            .clip(shape),
    ) {
        ComposeMediaImage(
            url = video.cover,
            settings = settings,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            contentDescription = video.title.ifBlank { "视频封面" },
            placeholderLabel = "视频",
            ensureTouchTarget = false,
            onClick = { onOpenVideo(video) },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.26f))
                .clickable { onOpenVideo(video) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = "播放视频",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
        if (video.title.isNotBlank()) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.54f))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun ComposeMediaGrid(
    urls: List<String>,
    settings: ComposeMediaSettings,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    imageShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
    onOpenImage: (String) -> Unit,
) {
    if (urls.isEmpty() || !settings.enabled) return
    val count = columns.coerceIn(1, 4)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        urls.chunked(count).forEach { rowUrls ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                rowUrls.forEach { url ->
                    ComposeMediaImage(
                        url = url,
                        settings = settings,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        shape = imageShape,
                        contentDescription = "打开图片",
                        ensureTouchTarget = true,
                        onClick = { onOpenImage(url) },
                    )
                }
                repeat(count - rowUrls.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
