package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

internal enum class ComposeFavoriteTab { POSTS, FOLDERS }

internal data class ComposeFavoriteFolder(val id: String, val name: String, val count: Int) {
    fun route(): String = "favorite_folder?folder=" + URLEncoder.encode(id, "UTF-8") +
        "&name=" + URLEncoder.encode(name, "UTF-8")

    companion object {
        fun fromRoute(route: String): ComposeFavoriteFolder {
            val values = URI("heybox:///" + route).rawQuery.orEmpty().split('&')
                .filter { it.contains('=') }.associate { value ->
                    val parts = value.split('=', limit = 2)
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                }
            return ComposeFavoriteFolder(values["folder"].orEmpty(), values["name"].orEmpty(), 0)
        }
    }
}

internal data class ComposeReadingCenterState(
    val recent: FeedItem?,
    val watchLaterCount: Int,
    val offlineBytes: Long,
    val readingSummary: String,
    val hasSavedPosition: Boolean = false,
) {
    companion object {
        fun from(snapshot: ReadingCenterLoader.Snapshot, summary: String,
                 hasSavedPosition: Boolean = false) = ComposeReadingCenterState(
            snapshot.recent, snapshot.watchLaterCount, snapshot.offlineBytes, summary, hasSavedPosition,
        )
    }
}

@Composable
internal fun ObserveSavedRotary(listState: LazyListState, services: ComposeServices) {
    val rotary = services.theme.rotaryRequest
    LaunchedEffect(rotary?.serial) {
        if (rotary != null) listState.scrollBy(rotary.distance.toFloat())
    }
}

@Composable
internal fun SavedPage(title: String, theme: ComposeThemeState, onBack: () -> Unit,
                       content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(theme.background)) {
        ComposePageHeader(title, onBack)
        Column(Modifier.fillMaxSize().padding(
            horizontal = watchDp(if (theme.roundScreen) 14 else 12), vertical = watchDp(4)),
            content = content)
    }
}

@Composable
internal fun ColumnScope.SavedEmpty(message: String, theme: ComposeThemeState) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(message, color = theme.muted, fontSize = watchSp(12f))
    }
}

@Composable
internal fun SavedLoadError(message: String, cached: Boolean, onRetry: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (cached) "离线缓存" else message, modifier = Modifier.weight(1f),
            color = theme.muted, fontSize = watchSp(11f), maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onRetry) {
            Text("重试", color = theme.accent, fontSize = watchSp(11f))
        }
    }
}
