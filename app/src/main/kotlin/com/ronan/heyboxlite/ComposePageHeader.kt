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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Positions the header's controls inside the upper chord without shrinking page content. */
@Composable
internal fun ComposePageHeader(title: String, onBack: (() -> Unit)?) {
    val theme = LocalHeyboxTheme.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val horizontal = if (theme.roundScreen) maxWidth * 0.16f else watchDp(8)
        val top = if (theme.roundScreen) maxWidth * 0.08f else watchDp(4)
        Row(Modifier.fillMaxWidth().padding(start = horizontal, end = horizontal,
            top = top, bottom = watchDp(2)), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(watchDp(34))) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_back),
                        "返回", tint = theme.text, modifier = Modifier.size(watchDp(19)))
                }
            } else {
                Spacer(Modifier.size(watchDp(5)))
            }
            Text(title, modifier = Modifier.weight(1f), color = theme.text,
                fontSize = watchSp(17f), fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Extra scroll travel lets the last row reach the lower circle's wide, tappable area. */
@Composable
internal fun watchListEndPadding(): Dp = if (LocalHeyboxTheme.current.roundScreen) {
    (LocalConfiguration.current.screenHeightDp * 0.22f).dp
} else watchDp(14)
