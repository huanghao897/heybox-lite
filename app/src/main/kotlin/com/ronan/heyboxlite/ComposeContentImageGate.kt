package com.ronan.heyboxlite

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Permission belongs to the source, not the recycled slot or its last bitmap. */
internal class ComposeContentImageGate(
    private val source: List<String>,
    private val approvedSource: MutableState<List<String>>,
    private val enabled: Boolean,
    private val dataSaver: Boolean,
) {
    val waiting: Boolean
        get() = enabled && source.first().isNotBlank() && dataSaver && approvedSource.value != source
    val canLoad: Boolean
        get() = enabled && source.first().isNotBlank() && !waiting

    fun tap(open: (() -> Unit)? = null) {
        if (waiting) approvedSource.value = source else open?.invoke()
    }
}

@Composable
internal fun rememberComposeContentImageGate(
    url: String,
    enabled: Boolean,
    dataSaver: Boolean,
    animatedUrl: String = url,
): ComposeContentImageGate {
    // Save the identity with the permission: restoring a lazy item with a new URL
    // must never inherit a previous source's permission, including GIF originals.
    val approved = rememberSaveable { mutableStateOf(emptyList<String>()) }
    return remember(url, animatedUrl, enabled, dataSaver, approved) {
        ComposeContentImageGate(listOf(url, animatedUrl), approved, enabled, dataSaver)
    }
}

@Composable
internal fun ComposeContentImageLoadIcon(theme: ComposeThemeState = LocalHeyboxTheme.current) {
    Icon(painterResource(R.drawable.ic_download), contentDescription = "\u52a0\u8f7d\u56fe\u7247",
        tint = theme.muted, modifier = Modifier.size(18.dp))
}
