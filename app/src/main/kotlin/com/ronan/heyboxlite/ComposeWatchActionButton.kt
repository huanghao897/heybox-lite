package com.ronan.heyboxlite

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WatchActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Float = 11f,
    @DrawableRes icon: Int? = null,
    theme: ComposeThemeState = LocalHeyboxTheme.current,
    primary: Boolean = false,
) {
    val scale = theme.uiScale
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .widthIn(min = (48 * scale).dp.coerceAtLeast(28.dp))
                .heightIn(min = ((if (primary) 40 else 36) * scale).dp.coerceAtLeast(28.dp)),
            shape = RoundedCornerShape(((if (primary) 8 else 7) * scale).dp.coerceAtMost(8.dp)),
            border = if (primary) null else BorderStroke(1.dp,
                if (enabled) theme.hairline else theme.hairline.copy(alpha = 0.6f)),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (primary) theme.accent else theme.panelElevated,
                contentColor = if (primary) theme.onAccent else theme.text,
                disabledContainerColor = if (primary) theme.panelElevated else theme.panel,
                disabledContentColor = theme.subtle,
            ),
            elevation = null,
            contentPadding = PaddingValues(horizontal = (10 * scale).dp,
                vertical = (7 * scale).dp),
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null,
                    modifier = Modifier.size((15 * scale).dp))
                Spacer(Modifier.width((6 * scale).dp))
            }
            Text(text, fontSize = (fontSize * theme.textScale).sp,
                letterSpacing = 0.sp, textAlign = TextAlign.Center)
        }
    }
}
