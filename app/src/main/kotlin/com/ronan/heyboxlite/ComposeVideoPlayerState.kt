package com.ronan.heyboxlite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

internal data class ComposeVideoPlayerUiState(
    val title: String = "视频",
    val coverUrl: String = "",
    val coverVisible: Boolean = false,
    val playing: Boolean = false,
    val completed: Boolean = false,
    val buffering: Boolean = true,
    val controlsVisible: Boolean = true,
    val fastForwarding: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val seekingMs: Int? = null,
    val error: String = "",
    val externalAvailable: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
) {
    val displayedPosition: Int get() = seekingMs ?: positionMs
}

/** Java callbacks publish UI facts here; VideoPlaybackController remains the playback owner. */
internal class ComposeVideoPlayerState(title: String?, cover: String?, private val images: Boolean) {
    private var closed = false
    var ui by mutableStateOf(ComposeVideoPlayerUiState(
        title = RichContent.plainText(title.orEmpty()).ifBlank { "视频" },
        coverUrl = cover.orEmpty(),
        coverVisible = images && !cover.isNullOrBlank(),
    ))
        private set

    fun setPrepared(durationMs: Int) = change { it.copy(
        durationMs = durationMs.coerceAtLeast(0), coverVisible = false,
        buffering = false, playing = false, completed = false, error = "",
        fastForwarding = false, controlsVisible = true,
    ) }

    fun setProgress(positionMs: Int, durationMs: Int) = change {
        val duration = durationMs.takeIf { value -> value > 0 } ?: it.durationMs
        it.copy(positionMs = positionMs.coerceIn(0, duration.takeIf { value -> value > 0 }
            ?: Int.MAX_VALUE), durationMs = duration)
    }

    fun setPlaying(playing: Boolean) = change { it.copy(
        playing = playing, completed = if (playing) false else it.completed,
        controlsVisible = it.controlsVisible || !playing,
    ) }

    fun setBuffering(buffering: Boolean) = change { it.copy(buffering = buffering) }
    fun setVideoSize(width: Int, height: Int) {
        if (width > 0 && height > 0) change { it.copy(videoWidth = width, videoHeight = height) }
    }

    fun setCompleted() = change { it.copy(playing = false, completed = true, buffering = false,
        fastForwarding = false, controlsVisible = true) }

    fun setFastForwarding(value: Boolean) = change { it.copy(fastForwarding = value,
        controlsVisible = it.controlsVisible || value) }

    fun setError(message: String?, externalAvailable: Boolean) = change { it.copy(
        error = message?.takeIf { value -> value.isNotBlank() } ?: "视频播放失败",
        externalAvailable = externalAvailable, buffering = false, playing = false,
        fastForwarding = false, completed = false, controlsVisible = true,
        coverVisible = images && it.coverUrl.isNotBlank(), seekingMs = null,
    ) }

    fun retrying() = change { it.copy(error = "", buffering = true, completed = false,
        fastForwarding = false, controlsVisible = true) }

    fun toggleControls() = change { it.copy(controlsVisible = !it.controlsVisible) }
    fun hideIfPlaying() = change {
        if (it.playing && !it.fastForwarding && it.seekingMs == null && it.error.isEmpty())
            it.copy(controlsVisible = false) else it
    }

    fun previewSeek(position: Float) = change { it.copy(
        seekingMs = position.toInt().coerceIn(0, it.durationMs.coerceAtLeast(0)),
        controlsVisible = true,
    ) }

    fun finishSeek(): Int? {
        if (closed) return null
        val position = ui.seekingMs ?: return null
        change { it.copy(seekingMs = null) }
        return position
    }

    fun close() { closed = true }
    private fun change(transform: (ComposeVideoPlayerUiState) -> ComposeVideoPlayerUiState) {
        if (!closed) ui = transform(ui)
    }
}

internal fun videoPlayerTime(millis: Int): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d",
        seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}

/** Insets the entire control band, not just its center, inside the watch's circular display. */
internal fun videoPlayerCircleInset(width: Float, height: Float, top: Float, bottom: Float,
    safety: Float = 4f): Float {
    val radius = (minOf(width, height) / 2f - safety).coerceAtLeast(0f)
    val distance = maxOf(abs(top - height / 2f), abs(bottom - height / 2f))
    val halfChord = sqrt((radius * radius - distance * distance).coerceAtLeast(0f))
    return (width / 2f - halfChord).coerceAtLeast(safety)
}

internal fun videoPlayerFit(width: Float, height: Float, videoWidth: Int, videoHeight: Int,
    crop: Boolean): Pair<Float, Float> {
    if (crop || videoWidth <= 0 || videoHeight <= 0) return width to height
    val scale = minOf(width / videoWidth, height / videoHeight)
    return videoWidth * scale to videoHeight * scale
}
