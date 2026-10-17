package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview

internal @Composable fun ComposeSettingsScreen(
    route: String,
    services: ComposeServices,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    when (route.substringBefore('?')) {
        "settings_home" -> ComposeSettingsHomeScreen(services, onNavigate, onBack)
        "display_settings" -> ComposeDisplaySettingsScreen(
            services, onNavigate, onBack, onSettingsChanged,
        )
        "display_preview" -> ComposeDisplayPreviewRoute(services, onBack)
        "startup_settings" -> ComposeStartupSettingsScreen(
            services, onNavigate, onBack, onSettingsChanged,
        )
        "app_settings" -> ComposeContentCacheScreen(
            services, onNavigate, onBack, onSettingsChanged,
        )
        "video_settings" -> ComposeVideoSettingsScreen(
            services, onBack, onSettingsChanged,
        )
        "splash_preview" -> ComposeSplashPreviewScreen(services, onBack)
        "about" -> ComposeAboutScreen(services, onNavigate, onBack)
        "announcement_board" -> ComposeAnnouncementBoardScreen(services, onBack)
        else -> ComposeSettingsHomeScreen(services, onNavigate, onBack)
    }
}

@Composable
private fun ComposeSettingsHomeScreen(
    services: ComposeServices,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    WatchPage("设置", onBack) {
        WatchCard {
            WatchRow(
                title = "显示",
                value = if (services.session.darkMode()) "深色" else "浅色",
                icon = R.drawable.il_palette,
                onClick = { onNavigate("display_settings") },
            )
            WatchRow(
                title = "启动与更新",
                icon = R.drawable.il_refresh,
                onClick = { onNavigate("startup_settings") },
            )
            WatchRow(
                title = "内容与缓存",
                icon = R.drawable.il_globe,
                onClick = { onNavigate("app_settings") },
            )
            WatchRow(
                title = "视频播放",
                icon = R.drawable.il_gif,
                onClick = { onNavigate("video_settings") },
            )
            WatchRow(
                title = "关于",
                value = BuildConfig.VERSION_NAME,
                icon = R.drawable.il_info,
                onClick = { onNavigate("about") },
            )
        }
    }
}

@Composable
internal fun ComposeSettingChoice(
    title: String,
    options: List<String>,
    selected: Int,
    icon: Int? = null,
    onSelected: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val index = selected.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    WatchRow(
        title = title,
        value = options.getOrElse(index) { "" },
        icon = icon,
        onClick = { open = true },
    )
    if (open) {
        ComposeChoiceDialog(
            title = title,
            options = options,
            selected = index,
            onDismiss = { open = false },
            onSelected = { value ->
                open = false
                onSelected(value)
            },
        )
    }
}

@Composable
private fun ComposeChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    WatchDialog(title, onDismiss) {
        options.forEachIndexed { index, option ->
            WatchRow(
                title = option,
                value = if (index == selected) "当前" else "",
                onClick = { onSelected(index) },
            )
        }
    }
}

@Composable
internal fun ComposeSettingText(
    title: String,
    value: String,
    hint: String,
    icon: Int? = null,
    onValueChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    WatchRow(title = title, value = value, icon = icon, onClick = { open = true })
    if (open) {
        ComposeTextDialog(
            title = title,
            initial = value,
            hint = hint,
            onDismiss = { open = false },
            onConfirm = { next ->
                open = false
                onValueChange(next)
            },
        )
    }
}

@Composable
private fun ComposeTextDialog(
    title: String,
    initial: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val style = LocalHeyboxTheme.current
    WatchDialog(title, onDismiss, actions = {
        WatchActionButton("取消", onDismiss, Modifier.weight(1f))
        WatchActionButton("确定", { onConfirm(text.trim()) }, Modifier.weight(1f))
    }) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = watchDp(44))
                .background(style.panelElevated, RoundedCornerShape(watchDp(9)))
                .padding(horizontal = watchDp(10), vertical = watchDp(9))
                .semantics { contentDescription = title },
            textStyle = TextStyle(color = style.text, fontSize = watchSp(13f)),
            cursorBrush = SolidColor(style.text),
            singleLine = true,
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(hint, color = style.subtle, fontSize = watchSp(12f))
                    }
                    field()
                }
            },
        )
    }
}

@Composable
internal fun ComposeConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WatchDialog(title, onDismiss, actions = {
        WatchActionButton("取消", onDismiss, Modifier.weight(1f))
        WatchActionButton("确认", {
            onConfirm()
            onDismiss()
        }, Modifier.weight(1f))
    }) {
        Text(message, color = LocalHeyboxTheme.current.muted,
            fontSize = watchSp(12f), lineHeight = watchSp(16f))
    }
}

@Preview(showBackground = true, widthDp = 192, heightDp = 192)
@Composable
private fun ComposeSettingsPreview() {
    val state = ComposeThemeState(
        dark = true,
        background = Color(0xFF0B0B0C),
        panel = Color(0xFF19191B),
        panelElevated = Color(0xFF242427),
        text = Color(0xFFF3F3F5),
        muted = Color(0xFFA5A5AA),
        subtle = Color(0xFF747479),
        hairline = Color(0xFF2B2B2E),
        accent = Color(0xFFC4C6C9),
        link = Color(0xFF59A9EB),
        onAccent = Color.Black,
        uiScale = 0.82f,
        textScale = 1f,
        roundScreen = true,
    )
    HeyboxComposeTheme(state) {
        WatchPage("设置", null) {
            WatchCard {
                WatchRow("显示", "深色", R.drawable.il_palette)
                WatchRow("内容与缓存", icon = R.drawable.il_globe)
                WatchRow("视频播放", icon = R.drawable.il_gif)
                WatchRow("关于", BuildConfig.VERSION_NAME, R.drawable.il_info)
            }
        }
    }
}
