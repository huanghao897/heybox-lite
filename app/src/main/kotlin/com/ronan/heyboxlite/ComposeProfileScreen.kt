package com.ronan.heyboxlite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ComposeProfileScreen(
    services: ComposeServices,
    onNavigate: (String) -> Unit,
) {
    val session = services.session
    var recentCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    LaunchedEffect(services.cache) {
        recentCount = withContext(Dispatchers.IO) { services.cache.recentItems().size }
    }
    WatchPage("我的", null) {
        val accountRoute = if (session.isLoggedIn()) {
            "user_space?user=${android.net.Uri.encode(session.userId())}" +
                "&name=${android.net.Uri.encode(session.userName())}" +
                "&avatar=${android.net.Uri.encode(session.avatar())}"
        } else "login"
        WatchCard(modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(watchDp(14)))
            .clickable { onNavigate(accountRoute) }
            .padding(watchDp(2))) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(watchDp(11)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposeMediaImage(
                    session.avatar(),
                    ComposeMediaSettings(!session.noImage(), session.playGif(), 240),
                    Modifier.size(watchDp(46)),
                    androidx.compose.foundation.shape.CircleShape,
                    placeholderLabel = session.userName().ifBlank { "我" }.take(1),
                    ensureTouchTarget = false,
                )
                Spacer(Modifier.size(watchDp(10)))
                Column(Modifier.weight(1f)) {
                    Text(
                        session.userName().ifBlank { "未登录" },
                        color = LocalHeyboxTheme.current.text,
                        fontSize = watchSp(16f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (session.isLoggedIn()) "账号信息" else "登录小黑盒",
                        color = LocalHeyboxTheme.current.muted,
                        fontSize = watchSp(11f),
                    )
                }
            }
        }
        WatchSectionTitle("阅读")
        WatchCard {
            WatchRow("阅读中心", recentCount?.toString() ?: "", R.drawable.il_reading) {
                onNavigate("reading_center")
            }
            WatchRow("收藏", icon = R.drawable.il_bookmark) { onNavigate("favorites") }
            WatchRow("排行榜", icon = R.drawable.il_leaderboard) { onNavigate("leaderboard") }
            WatchRow("小黑盒签到", icon = R.drawable.il_calendar) { onNavigate("checkin_center") }
        }
        WatchSectionTitle("其他")
        WatchCard {
            WatchRow("设置", icon = R.drawable.il_settings) { onNavigate("settings_home") }
        }
    }
}
