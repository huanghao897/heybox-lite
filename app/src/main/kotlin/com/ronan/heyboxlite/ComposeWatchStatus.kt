package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * One look for loading, empty and failed content on every page. [fill] centres the
 * state in the whole page; otherwise it sits inline in a list. Sizes come from the
 * passed theme so hosts that only hold a ComposeThemeState can use them too.
 */

@Composable
internal fun WatchLoading(
    modifier: Modifier = Modifier,
    label: String = "加载中",
    fill: Boolean = false,
    theme: ComposeThemeState = LocalHeyboxTheme.current,
) {
    WatchStatusFrame(modifier, fill, theme) {
        CircularProgressIndicator(Modifier.size((WatchIcon.XLARGE * theme.uiScale).dp),
            color = theme.accent, strokeWidth = 2.dp, trackColor = theme.panelElevated)
        if (label.isNotEmpty()) WatchStatusText(label, theme, Modifier.padding(top = (8 * theme.uiScale).dp))
    }
}

@Composable
internal fun WatchEmptyState(
    text: String,
    modifier: Modifier = Modifier,
    action: String = "",
    onAction: (() -> Unit)? = null,
    fill: Boolean = false,
    theme: ComposeThemeState = LocalHeyboxTheme.current,
) {
    WatchStatusFrame(modifier, fill, theme) {
        WatchStatusText(text, theme)
        if (action.isNotEmpty() && onAction != null) {
            WatchActionButton(action, onAction, Modifier.padding(top = (8 * theme.uiScale).dp),
                theme = theme)
        }
    }
}

@Composable
internal fun WatchErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    retryLabel: String = "重试",
    fill: Boolean = false,
    theme: ComposeThemeState = LocalHeyboxTheme.current,
) {
    WatchStatusFrame(modifier, fill, theme) {
        WatchStatusText(message, theme, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (onRetry != null) {
            WatchActionButton(retryLabel, onRetry, Modifier.padding(top = (8 * theme.uiScale).dp),
                icon = R.drawable.il_refresh, theme = theme)
        }
    }
}

@Composable
private fun WatchStatusText(text: String, theme: ComposeThemeState, modifier: Modifier = Modifier) {
    Text(text, modifier, color = theme.muted, fontSize = (WatchType.SECONDARY * theme.textScale).sp,
        lineHeight = (WatchType.lineHeight(WatchType.SECONDARY) * theme.textScale).sp,
        textAlign = TextAlign.Center)
}

@Composable
private fun WatchStatusFrame(
    modifier: Modifier,
    fill: Boolean,
    theme: ComposeThemeState,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (fill) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val frame = if (constraints.hasBoundedHeight) {
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                    .padding(horizontal = (12 * theme.uiScale).dp, vertical = (12 * theme.uiScale).dp)
            } else Modifier.fillMaxWidth().padding(vertical = (20 * theme.uiScale).dp)
            Column(frame,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content)
        }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = (20 * theme.uiScale).dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
