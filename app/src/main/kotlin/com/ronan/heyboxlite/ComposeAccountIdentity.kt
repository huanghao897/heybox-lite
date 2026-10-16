package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeAccountIdentity(
    name: String,
    id: String,
    signature: String = "",
    nameSize: Float = 16f,
    avatar: @Composable () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(watchDp(11))) {
        val stacked = maxWidth < watchDp(150) * theme.textScale * LocalDensity.current.fontScale
        if (stacked) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(watchDp(6))) {
                avatar()
                AccountIdentityText(name, id, signature, nameSize, TextAlign.Center)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(watchDp(10))) {
                avatar()
                Column(Modifier.weight(1f)) {
                    AccountIdentityText(name, id, signature, nameSize, TextAlign.Start)
                }
            }
        }
    }
}

@Composable
private fun AccountIdentityText(name: String, id: String, signature: String, nameSize: Float,
    alignment: TextAlign) {
    val theme = LocalHeyboxTheme.current
    Text(name, color = theme.text, fontSize = watchSp(nameSize),
        letterSpacing = 0.sp,
        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        textAlign = alignment, modifier = Modifier.fillMaxWidth())
    Text(id, color = theme.muted, fontSize = watchSp(11f), textAlign = alignment, letterSpacing = 0.sp,
        modifier = Modifier.fillMaxWidth().padding(top = watchDp(2)))
    if (signature.isNotBlank()) Text(signature, color = theme.muted, fontSize = watchSp(10f),
        letterSpacing = 0.sp,
        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = alignment,
        modifier = Modifier.fillMaxWidth().padding(top = watchDp(3)))
}
