package com.ronan.heyboxlite

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Page titles use the upper chord without reserving a separate return toolbar. */
@Composable
internal fun ComposePageHeader(title: String, onBack: (() -> Unit)?) {
    val theme = LocalHeyboxTheme.current
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("watch-page-header")) {
        val horizontal = watchHeaderHorizontal(maxWidth, theme)
        val top = watchHeaderTop(maxWidth, theme)
        Row(Modifier.fillMaxWidth().padding(start = horizontal, end = horizontal,
            top = top, bottom = watchDp(2)), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(watchDp(28).coerceAtLeast(28.dp))) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_back),
                        "返回", tint = theme.text, modifier = Modifier.size(watchDp(19)))
                }
            } else Spacer(Modifier.size(watchDp(5)))
            Text(title, modifier = Modifier.weight(1f), color = theme.text,
                fontSize = watchSp(17f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
                maxLines = if (theme.roundScreen) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Extra scroll travel lets the last row reach the lower circle's wide, tappable area. */
@Composable
internal fun watchListEndPadding(withBottomNavigation: Boolean = false): Dp {
    val theme = LocalHeyboxTheme.current
    val base = if (theme.roundScreen) (LocalConfiguration.current.screenHeightDp * 0.25f).dp
        else watchDp(14)
    if (!withBottomNavigation) return base
    val metrics = LocalContext.current.resources.displayMetrics
    val dock = ResponsiveDock.fromScreen(metrics.widthPixels, metrics.heightPixels,
        theme.roundScreen, theme.uiScale)
    val dockSpace = with(LocalDensity.current) { (dock.height + dock.marginBottom).toDp() } + watchDp(8)
    return maxOf(base, dockSpace)
}
