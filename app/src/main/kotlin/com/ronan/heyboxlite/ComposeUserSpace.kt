package com.ronan.heyboxlite

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ComposeUserSpaceRoute(
    val userId: String,
    val name: String,
    val avatar: String,
)

internal fun composeUserSpaceRoute(route: String): ComposeUserSpaceRoute {
    val query = route.substringAfter('?', "")
    val uri = Uri.parse("heybox://user?$query")
    return ComposeUserSpaceRoute(
        userId = uri.getQueryParameter("user").orEmpty(),
        name = uri.getQueryParameter("name").orEmpty(),
        avatar = uri.getQueryParameter("avatar").orEmpty(),
    )
}

@Composable
internal fun ComposeUserSpaceScreen(
    route: ComposeUserSpaceRoute,
    services: ComposeServices,
    onBack: () -> Unit,
    onOpen: (FeedItem) -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val controller = remember(route.userId) {
        ComposeUserSpaceController(services.api, services.cache)
    }
    val state by controller.state
    var articlesOnly by androidx.compose.runtime.remember(route.userId) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    LaunchedEffect(route.userId, route.name, route.avatar) { controller.load(route) }

    WatchPage(state.name, onBack) {
        WatchCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(watchDp(11)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposeRemoteImage(
                    state.avatar, theme, services.session.noImage(), watchDp(48), CircleShape,
                    "用户头像", Modifier.size(watchDp(48)),
                )
                Column(Modifier.weight(1f).padding(start = watchDp(9))) {
                    Text(state.name, color = theme.text, fontSize = watchSp(15f),
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("ID ${route.userId}", color = theme.muted, fontSize = watchSp(10f),
                        modifier = Modifier.padding(top = watchDp(2)))
                    if (state.signature.isNotBlank()) Text(
                        state.signature, color = theme.muted, fontSize = watchSp(10f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = watchDp(3)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = watchDp(11), vertical = watchDp(5))) {
                UserStat("关注", state.follows, theme, Modifier.weight(1f))
                UserStat("粉丝", state.fans, theme, Modifier.weight(1f))
                UserStat("获赞", state.likes, theme, Modifier.weight(1f))
            }
        }
        WatchCard {
            Row(Modifier.fillMaxWidth()) {
                UserSpaceTab("动态", !articlesOnly, theme, Modifier.weight(1f)) {
                    articlesOnly = false
                }
                UserSpaceTab("投稿", articlesOnly, theme, Modifier.weight(1f)) {
                    articlesOnly = true
                }
            }
        }
        when {
            state.loading -> FeedLoading(theme)
            state.error.isNotBlank() -> WatchEmptyState(state.error)
            else -> {
                val filtered = state.items.filter { !articlesOnly || it.article }
                if (filtered.isEmpty()) WatchEmptyState(if (articlesOnly) "暂无投稿" else "暂无动态")
                else Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(watchDp(7)),
                ) {
                    filtered.forEach { item ->
                        ComposeFeedCard(
                            item = item,
                            theme = theme,
                            noImage = services.session.noImage(),
                            currentUserId = services.session.userId(),
                            onOpen = onOpen,
                            onAction = null,
                            showActions = false,
                            showFollow = false,
                            gameCardNoImage = services.session.gameCardNoImage(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserStat(label: String, value: Int, theme: ComposeThemeState, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Format.commentLikeCount(value), color = theme.text, fontSize = watchSp(13f),
            fontWeight = FontWeight.SemiBold)
        Text(label, color = theme.muted, fontSize = watchSp(9f),
            modifier = Modifier.padding(top = watchDp(2)))
    }
}

@Composable
private fun UserSpaceTab(
    text: String,
    selected: Boolean,
    theme: ComposeThemeState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = if (selected) theme.text else theme.muted,
            fontSize = watchSp(12f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
