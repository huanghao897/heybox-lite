package com.ronan.heyboxlite

import android.graphics.Color
import android.graphics.RectF
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** The Activity keeps only OS integration; composition owns pages and image state. */
internal class ComposeImageViewerHost(
    private val activity: ComponentActivity,
    session: SessionStore,
) {
    private val intent = activity.intent
    private val urls = intent.getStringArrayExtra(ImageViewerActivity.EXTRA_URLS)
        ?.toList()?.takeIf { it.isNotEmpty() }
        ?: listOf(intent.getStringExtra(ImageViewerActivity.EXTRA_URL).orEmpty())
    private val initial = intent.getIntExtra(ImageViewerActivity.EXTRA_INDEX, 0).coerceIn(urls.indices)
    private val claimed = ImagePreviewStore.claim(intent.getLongExtra(ImageViewerActivity.EXTRA_PREVIEW_ID, 0))
    private val preview = claimed?.takeIf { it.url == urls[initial] }?.bitmap?.get()
    private val selection = ComposeViewerSelection()
    private var foreground by mutableStateOf(true)
    private var sourceVisibility: Int? = null
    private val view = ComposeView(activity)
    private val origin = RectF().apply {
        val width = intent.getIntExtra(ImageViewerActivity.EXTRA_ORIGIN_WIDTH, 0)
        val height = intent.getIntExtra(ImageViewerActivity.EXTRA_ORIGIN_HEIGHT, 0)
        val x = intent.getIntExtra(ImageViewerActivity.EXTRA_ORIGIN_X, 0)
        val y = intent.getIntExtra(ImageViewerActivity.EXTRA_ORIGIN_Y, 0)
        set(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)
    }.takeIf { it.width() > 1 && it.height() > 1 }

    init {
        val tokens = ThemeTokens.of(session.darkMode(), color(session.primaryColor()),
            color(session.secondaryColor()))
        val theme = composeThemeState(tokens, session.uiScale() / 100f, session.textScale() / 100f,
            session.usesRoundLayout())
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            HeyboxComposeTheme(theme) {
                ComposeImageViewer(urls, initial, preview, theme.roundScreen, session.originalImages(),
                    intent.getBooleanExtra(ImageViewerActivity.EXTRA_LOAD_ORIGINAL_IMMEDIATELY, false),
                    foreground, session.motionLevel(), selection,
                    onSave = { ImageViewerDownload.save(activity, it) },
                    onFinish = { activity.finish(); activity.overridePendingTransition(0, 0) },
                    onSourceVisibility = ::sourceVisible, origin = origin, playGif = session.playGif())
            }
        }
        activity.setContentView(view)
    }

    fun setActive(value: Boolean) { foreground = value }
    fun back() { selection.back() }
    fun currentUrl(): String = urls[selection.index.coerceIn(urls.indices)]
    fun close() { sourceVisible(true); view.disposeComposition() }

    private fun sourceVisible(visible: Boolean) {
        val source = claimed?.source?.get() ?: return
        if (!visible && sourceVisibility == null) {
            sourceVisibility = source.visibility
            source.visibility = View.INVISIBLE
        } else if (visible) {
            sourceVisibility?.let { if (source.visibility == View.INVISIBLE) source.visibility = it }
            sourceVisibility = null
        }
    }

    private fun color(value: String): Int =
        if (value.isBlank()) 0 else try { Color.parseColor(value) } catch (_: IllegalArgumentException) { 0 }
}
