package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun ComposeContentCacheScreen(
    services: ComposeServices,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    val session = services.session
    var noImage by remember { mutableStateOf(session.noImage()) }
    var gameCardNoImage by remember { mutableStateOf(session.gameCardNoImage()) }
    var originalImages by remember { mutableStateOf(session.originalImages()) }
    var crownEnabled by remember { mutableStateOf(session.crownScrollEnabled()) }
    var crownHaptics by remember { mutableStateOf(session.crownHapticsEnabled()) }
    var backSwipe by remember { mutableStateOf(session.shellBackSwipe()) }
    var homeSwipe by remember { mutableStateOf(session.homeSwipeExit()) }
    var confirmExit by remember { mutableStateOf(session.confirmExitOnBack()) }
    var rememberScroll by remember { mutableStateOf(session.rememberDetailScroll()) }
    var doubleTapReply by remember { mutableStateOf(session.doubleTapCommentReply()) }
    var playGif by remember { mutableStateOf(session.playGif()) }
    var filter by remember { mutableStateOf(session.blockKeywords()) }
    var detailCount by remember { mutableStateOf(0) }
    var offlineBytes by remember { mutableStateOf(0L) }
    var cacheStatsVersion by remember { mutableStateOf(0) }

    LaunchedEffect(services.cache, cacheStatsVersion) {
        if (cacheStatsVersion > 0) delay(300)
        val snapshot = withContext(Dispatchers.IO) {
            sessionDetailCount(services) to sessionOfflineBytes(services)
        }
        detailCount = snapshot.first
        offlineBytes = snapshot.second
    }

    fun changed() = onSettingsChanged()

    WatchPage("内容与缓存", onBack) {
        WatchSectionTitle("浏览与交互")
        WatchCard {
            WatchSwitchRow("无图模式", noImage, R.drawable.il_image) { value ->
                noImage = value
                session.setNoImage(value)
                changed()
            }
            WatchSwitchRow("游戏卡片无图", gameCardNoImage, R.drawable.il_image) { value ->
                gameCardNoImage = value
                session.setGameCardNoImage(value)
                changed()
            }
            WatchSwitchRow("查看原图", originalImages, R.drawable.il_image) { value ->
                originalImages = value
                session.setOriginalImages(value)
                changed()
            }
            WatchSwitchRow("播放 GIF", playGif, R.drawable.il_gif) { value ->
                playGif = value
                session.setPlayGif(value)
                changed()
            }
            WatchSwitchRow("表冠滚动", crownEnabled, R.drawable.il_scroll) { value ->
                crownEnabled = value
                session.setCrownScrollEnabled(value)
                changed()
            }
            ComposeSettingRange(
                title = "滚动速度",
                value = session.crownScrollSpeed(),
                min = CrownScrollController.MIN_SPEED_PERCENT,
                max = CrownScrollController.SLIDER_MAX_SPEED_PERCENT,
                step = 5,
                unit = "%",
                icon = R.drawable.il_scroll,
            ) { value ->
                session.setCrownScrollSpeed(value)
                changed()
            }
            WatchSwitchRow("表冠触感", crownHaptics, R.drawable.il_info) { value ->
                crownHaptics = value
                session.setCrownHapticsEnabled(value)
                changed()
            }
            WatchSwitchRow("右滑返回上一级", backSwipe, R.drawable.il_swipe) { value ->
                backSwipe = value
                session.setShellBackSwipe(value)
                changed()
            }
            WatchSwitchRow("主页右滑退出", homeSwipe, R.drawable.il_swipe) { value ->
                homeSwipe = value
                session.setHomeSwipeExit(value)
                changed()
            }
            WatchSwitchRow("退出确认", confirmExit, R.drawable.il_info) { value ->
                confirmExit = value
                session.setConfirmExitOnBack(value)
                changed()
            }
            WatchSwitchRow("记住帖子阅读位置", rememberScroll, R.drawable.il_reading) { value ->
                rememberScroll = value
                session.setRememberDetailScroll(value)
                changed()
            }
            ComposeSettingChoice(
                title = "自动清理",
                options = listOf("关闭", "30 天"),
                selected = if (session.autoOfflineCleanup()) 1 else 0,
                icon = R.drawable.il_cleanup,
            ) { value ->
                val enabled = value == 1
                session.setAutoOfflineCleanup(enabled)
                changed()
                if (enabled) onNavigate("cache_prune")
            }
            WatchSwitchRow("双击评论回复", doubleTapReply, R.drawable.il_reply) { value ->
                doubleTapReply = value
                session.setDoubleTapCommentReply(value)
                changed()
            }
        }

        WatchSectionTitle("内容过滤")
        WatchCard {
            Text(
                text = "屏蔽关键词，用逗号分隔",
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(11f),
            )
            Spacer(modifier = Modifier.height(watchDp(6)))
            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(watchDp(62))
                    .background(
                        LocalHeyboxTheme.current.panelElevated,
                        RoundedCornerShape(watchDp(9)),
                    )
                    .padding(horizontal = watchDp(10), vertical = watchDp(8)),
                textStyle = TextStyle(
                    color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(12f),
                ),
                decorationBox = { field ->
                    if (filter.isEmpty()) {
                        Text(
                            text = "例如：抽奖，广告",
                            color = LocalHeyboxTheme.current.subtle,
                            fontSize = watchSp(12f),
                        )
                    }
                    field()
                },
            )
            WatchActionText("保存内容过滤") {
                session.setBlockKeywords(filter)
                changed()
                services.toast.show("内容过滤已保存")
            }
        }

        WatchSectionTitle("维护")
        WatchCard {
            WatchRow(
                title = "清理过期离线内容",
                value = "$detailCount 篇",
                icon = R.drawable.il_cleanup,
                onClick = { onNavigate("cache_prune") },
            )
            WatchRow(
                title = "导出日志",
                icon = R.drawable.il_scroll,
                onClick = { onNavigate("diagnostics_export") },
            )
            WatchRow(
                title = "上传日志",
                icon = R.drawable.il_info,
                onClick = { onNavigate("diagnostics_upload") },
            )
            WatchRow(
                title = "崩溃测试",
                icon = R.drawable.il_info,
                onClick = { onNavigate("crash_test") },
            )
            WatchRow(
                title = "清除缓存",
                value = Format.cacheMb(offlineBytes),
                icon = R.drawable.il_cleanup,
                onClick = {
                    onNavigate("cache_clear")
                    cacheStatsVersion++
                },
            )
            WatchRow(
                title = if (session.isLoggedIn()) "退出登录" else "二维码登录",
                value = if (session.isLoggedIn()) "当前账号 ID ${session.userId()}" else "扫码登录小黑盒账号",
                icon = if (session.isLoggedIn()) R.drawable.ic_logout else R.drawable.il_qr,
                onClick = {
                    onNavigate(if (session.isLoggedIn()) "logout" else "login")
                },
            )
        }
    }
}

private fun sessionDetailCount(services: ComposeServices): Int = services.cache.detailCount()

private fun sessionOfflineBytes(services: ComposeServices): Long = services.cache.offlineBytes()
