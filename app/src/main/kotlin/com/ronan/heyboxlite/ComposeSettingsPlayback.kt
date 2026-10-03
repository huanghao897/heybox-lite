package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text

@Composable
internal fun ComposeStartupSettingsScreen(
    services: ComposeServices,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    val session = services.session
    var updateCheck by remember { mutableStateOf(session.autoUpdateCheck()) }
    var splashEnabled by remember { mutableStateOf(session.splashEnabled()) }
    WatchPage("启动与更新", onBack) {
        WatchSectionTitle("启动与更新")
        WatchCard {
            WatchSwitchRow(
                title = "进入软件时检查更新",
                checked = updateCheck,
                icon = R.drawable.il_update,
            ) { value ->
                updateCheck = value
                session.setAutoUpdateCheck(value)
                onSettingsChanged()
            }
            WatchSwitchRow(
                title = "显示开屏动画",
                checked = splashEnabled,
                icon = R.drawable.il_splash,
            ) { value ->
                splashEnabled = value
                session.setSplashEnabled(value)
                onSettingsChanged()
            }
            ComposeSettingText(
                title = "开屏文字",
                value = session.splashText(),
                hint = SessionStore.DEFAULT_SPLASH_TEXT,
                icon = R.drawable.il_info,
            ) { value ->
                session.setSplashText(value)
                onSettingsChanged()
            }
            ComposeSettingRange(
                title = "开屏时长",
                value = session.splashDuration(),
                min = 500,
                max = 2600,
                step = 50,
                unit = "ms",
                icon = R.drawable.il_history,
            ) { value ->
                session.setSplashDuration(value)
                onSettingsChanged()
            }
            WatchRow(
                title = "预览开屏动画",
                icon = R.drawable.il_eye,
                onClick = { onNavigate("splash_preview") },
            )
        }
    }
}

@Composable
internal fun ComposeVideoSettingsScreen(
    services: ComposeServices,
    onBack: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    val session = services.session
    var autoplay by remember { mutableStateOf(session.videoAutoplay()) }
    var loop by remember { mutableStateOf(session.videoLoop()) }
    var muted by remember { mutableStateOf(session.videoMuted()) }
    var fastForward by remember { mutableStateOf(session.videoLongPressFastForward()) }
    var fallback by remember { mutableStateOf(session.videoExternalFallback()) }
    WatchPage("视频播放", onBack) {
        WatchSectionTitle("基础播放")
        WatchCard {
            WatchSwitchRow("自动播放", autoplay, R.drawable.il_gif) { value ->
                autoplay = value
                session.setVideoAutoplay(value)
                onSettingsChanged()
            }
            WatchSwitchRow("循环播放", loop, R.drawable.il_refresh) { value ->
                loop = value
                session.setVideoLoop(value)
                onSettingsChanged()
            }
            WatchSwitchRow("默认静音", muted, R.drawable.il_gif) { value ->
                muted = value
                session.setVideoMuted(value)
                onSettingsChanged()
            }
        }

        WatchSectionTitle("手势操作")
        WatchCard {
            WatchSwitchRow("长按 2 倍速快进", fastForward, R.drawable.il_swipe) { value ->
                fastForward = value
                session.setVideoLongPressFastForward(value)
                onSettingsChanged()
            }
        }

        WatchSectionTitle("画面显示")
        WatchCard {
            ComposeSettingChoice(
                title = "视频显示方式",
                options = listOf("适应屏幕", "裁切填充"),
                selected = session.videoDisplayMode(),
                icon = R.drawable.il_zoom,
            ) { value ->
                session.setVideoDisplayMode(value)
                onSettingsChanged()
            }
        }

        WatchSectionTitle("播放失败")
        WatchCard {
            WatchSwitchRow("失败时使用凉腕播放器", fallback, R.drawable.il_gif) { value ->
                fallback = value
                session.setVideoExternalFallback(value)
                onSettingsChanged()
            }
        }
    }
}

@Composable
internal fun ComposeSplashPreviewScreen(services: ComposeServices, onBack: () -> Unit) {
    val session = services.session
    WatchPage("开屏预览", onBack) {
        WatchCard(highlighted = true) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = watchDp(34)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "heybox Lite",
                    color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(18f),
                )
                Text(
                    text = session.splashText(),
                    color = LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(12f),
                    modifier = Modifier.padding(top = watchDp(8)),
                )
                Text(
                    text = "${session.splashDuration()} ms",
                    color = LocalHeyboxTheme.current.subtle,
                    fontSize = watchSp(10f),
                    modifier = Modifier.padding(top = watchDp(5)),
                )
            }
        }
    }
}
