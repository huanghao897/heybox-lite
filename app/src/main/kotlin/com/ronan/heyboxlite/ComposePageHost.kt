package com.ronan.heyboxlite

import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

object ComposePageHost {
    @JvmStatic
    fun mountDisplayPreview(
        page: LinearLayout,
        dark: Boolean,
        background: Int,
        panel: Int,
        panelElevated: Int,
        text: Int,
        muted: Int,
        subtle: Int,
        hairline: Int,
        accent: Int,
        link: Int,
        onAccent: Int,
        uiScale: Float,
        textScale: Float,
        roundScreen: Boolean,
    ) {
        val state = ComposeThemeState(
            dark = dark,
            background = background.asComposeColor(),
            panel = panel.asComposeColor(),
            panelElevated = panelElevated.asComposeColor(),
            text = text.asComposeColor(),
            muted = muted.asComposeColor(),
            subtle = subtle.asComposeColor(),
            hairline = hairline.asComposeColor(),
            accent = accent.asComposeColor(),
            link = link.asComposeColor(),
            onAccent = onAccent.asComposeColor(),
            uiScale = uiScale.coerceIn(0.5f, 1.6f),
            textScale = textScale.coerceIn(0.75f, 1.8f),
            roundScreen = roundScreen,
        )
        val composeView = ComposeView(page.context)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        composeView.setContent {
            HeyboxComposeTheme(state) {
                DisplayPreviewScreen(state)
            }
        }
        page.addView(composeView, LinearLayout.LayoutParams(-1, -2))
    }
}
