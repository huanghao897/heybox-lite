package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Video square", widthDp = 240, heightDp = 320, showBackground = true)
@Composable
private fun VideoSquarePreview() = VideoPlayerPreview(false)

@Preview(name = "Video round", widthDp = 227, heightDp = 227, showBackground = true)
@Composable
private fun VideoRoundPreview() = VideoPlayerPreview(true)

@Preview(name = "Video small round large text", widthDp = 192, heightDp = 192,
    showBackground = true, fontScale = 1.5f)
@Composable
private fun VideoSmallRoundPreview() = VideoPlayerPreview(true)

@Preview(name = "Video error", widthDp = 227, heightDp = 227, showBackground = true)
@Composable
private fun VideoErrorPreview() = VideoPlayerPreview(true, "播放器无法打开此视频")

@Composable
private fun VideoPlayerPreview(round: Boolean, error: String = "") {
    HeyboxComposeTheme(composeThemeState(ThemeTokens.of(true, 0, 0), 1f, 1f, round)) {
        ComposeVideoPlayerScreen(ComposeVideoPlayerUiState(title = "游戏实机演示", buffering = false,
            durationMs = 90_000, positionMs = 24_000, videoWidth = 1920, videoHeight = 1080,
            error = error, externalAvailable = true), false, {}, {}, {}, {}, {}, {}, {},
            videoSurface = { bounds -> Box(bounds.background(Color(0xFF242426))) })
    }
}
