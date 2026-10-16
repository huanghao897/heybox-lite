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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Positions the header's controls inside the upper chord without shrinking page content. */
@Composable
internal fun ComposePageHeader(title: String, onBack: (() -> Unit)?) {
    val theme = LocalHeyboxTheme.current
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("watch-page-header")) {
        val horizontal = watchHeaderHorizontal(maxWidth, theme)
        val top = watchHeaderTop(maxWidth, theme)
        Row(Modifier.fillMaxWidth().padding(start = horizontal, end = horizontal,
            top = top, bottom = watchDp(2)), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                ComposePageBackButton(onBack)
            } else {
                Spacer(Modifier.size(watchDp(5)))
            }
            Text(title, modifier = Modifier.weight(1f), color = theme.text,
                fontSize = watchSp(17f), fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ComposePageBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalHeyboxTheme.current
    IconButton(onClick = onBack, modifier = modifier.size(watchDp(34).coerceAtLeast(28.dp))) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_back),
            "返回", tint = theme.text, modifier = Modifier.size(watchDp(19)))
    }
}

/** Extra scroll travel lets the last row reach the lower circle's wide, tappable area. */
@Composable
internal fun watchListEndPadding(): Dp = if (LocalHeyboxTheme.current.roundScreen) {
    (LocalConfiguration.current.screenHeightDp * 0.25f).dp
} else watchDp(14)
