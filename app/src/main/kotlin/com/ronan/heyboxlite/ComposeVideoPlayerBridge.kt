package com.ronan.heyboxlite

import android.content.Context
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView

internal interface ComposeVideoPlayerActions {
    fun onBack()
    fun onTogglePlayback()
    fun onSeekTo(positionMs: Int)
    fun onRetry()
    fun onExternalPlayer()
    fun onDoubleTap()
    fun onFastForwardStart(): Boolean
    fun onFastForwardEnd()
    fun onSurfaceAvailable(surface: SurfaceView)
    fun onSurfaceReleased(surface: SurfaceView)
}

/** The same platform gesture detector is shared by the cover and the whole playback canvas. */
internal class ComposeVideoPlayerGestures(
    context: Context,
    private val longPressEnabled: Boolean,
    private val onTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onFastStart: () -> Boolean,
    private val onFastEnd: () -> Unit,
) {
    private var active = true
    private var closed = false
    private var fast = false
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            if (active && !closed) onTap()
            return true
        }
        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (active && !closed) onDoubleTap()
            return true
        }
        override fun onLongPress(event: MotionEvent) {
            if (active && !closed && longPressEnabled && !fast) fast = onFastStart()
        }
    })

    fun onTouch(event: MotionEvent): Boolean {
        if (!active || closed) return false
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
            endFastForward()
        return true
    }

    fun resume() { if (!closed) active = true }
    fun pause() {
        active = false
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        detector.onTouchEvent(cancel)
        cancel.recycle()
        endFastForward()
    }
    fun close() { pause(); closed = true }
    private fun endFastForward() {
        if (!fast) return
        fast = false
        onFastEnd()
    }
}

/** A real Compose root called from Java; only the rendering surface stays a native View. */
@OptIn(ExperimentalComposeUiApi::class)
internal class ComposeVideoPlayerBridge(
    activity: ComponentActivity,
    session: SessionStore,
    tokens: ThemeTokens,
    private val state: ComposeVideoPlayerState,
    actions: ComposeVideoPlayerActions,
) {
    private var closed = false
    private val gestures = ComposeVideoPlayerGestures(activity, session.videoLongPressFastForward(),
        state::toggleControls, actions::onDoubleTap,
        { actions.onFastForwardStart().also { state.setFastForwarding(it) } },
        { actions.onFastForwardEnd(); state.setFastForwarding(false) })
    val view = ComposeView(activity)

    init {
        OfficialEmojiFallback.load(activity)
        val theme = composeThemeState(tokens, session.uiScale() / 100f,
            session.textScale() / 100f, session.usesRoundLayout())
        view.setBackgroundColor(android.graphics.Color.BLACK)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            DisposableEffect(Unit) { onDispose { gestures.close() } }
            HeyboxComposeTheme(theme) {
                ComposeVideoPlayerScreen(
                    state = state.ui,
                    crop = session.videoDisplayMode() == 1,
                    onBack = actions::onBack,
                    onTogglePlayback = actions::onTogglePlayback,
                    onSeekPreview = state::previewSeek,
                    onSeekCommit = { state.finishSeek()?.let(actions::onSeekTo) },
                    onRetry = actions::onRetry,
                    onExternalPlayer = actions::onExternalPlayer,
                    onHideControls = state::hideIfPlaying,
                    gestureModifier = Modifier.pointerInteropFilter(onTouchEvent = gestures::onTouch),
                    videoSurface = { modifier ->
                        AndroidView(
                            modifier = modifier,
                            factory = { context -> SurfaceView(context).also {
                                it.keepScreenOn = true
                                actions.onSurfaceAvailable(it)
                            } },
                            onRelease = {
                                it.keepScreenOn = false
                                actions.onSurfaceReleased(it)
                            },
                        )
                    },
                )
            }
        }
    }

    fun resume() = gestures.resume()
    fun pause() = gestures.pause()
    fun close() {
        if (closed) return
        closed = true
        gestures.close()
        state.close()
        view.disposeComposition()
    }
}
