package com.ronan.heyboxlite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeDisplaySettingsScreen(
    services: ComposeServices,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    val session = services.session
    var resetDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(session.darkMode()) }
    var roundScreen by remember { mutableStateOf(session.roundScreen()) }
    var bodyBold by remember { mutableStateOf(session.bodyBold()) }

    WatchPage("显示", onBack) {
        WatchSectionTitle("外观")
        WatchCard {
            WatchSwitchRow(
                title = "夜间模式",
                checked = darkMode,
                icon = R.drawable.il_sun,
            ) { value ->
                darkMode = value
                session.setDarkMode(value)
                onSettingsChanged()
            }
            WatchRow(
                title = "颜色主题",
                value = composeThemeCaption(session),
                icon = R.drawable.il_palette,
                onClick = { themeDialog = true },
            )
            ComposeSettingRange(
                title = "界面大小",
                value = session.configuredUiScale(),
                min = SessionStore.MIN_UI_SCALE,
                max = SessionStore.MAX_UI_SCALE,
                step = 1,
                unit = "%",
                icon = R.drawable.ic_expand,
            ) { value ->
                session.setUiScale(value)
                onSettingsChanged()
            }
            ComposeSettingRange(
                title = "文字大小",
                value = session.configuredTextScale(),
                min = SessionStore.MIN_TEXT_SCALE,
                max = SessionStore.MAX_TEXT_SCALE,
                step = 1,
                unit = "%",
                icon = R.drawable.il_info,
            ) { value ->
                session.setTextScale(value)
                onSettingsChanged()
            }
            ComposeSettingRange(
                title = "左右边距",
                value = session.pagePadding(),
                min = 0,
                max = 30,
                step = 1,
                unit = "dp",
                icon = R.drawable.ic_expand,
            ) { value ->
                session.setPagePadding(value)
                onSettingsChanged()
            }
            WatchRow(
                title = "界面预览",
                icon = R.drawable.il_eye,
                onClick = { onNavigate("display_preview") },
            )
        }

        WatchSectionTitle(composeScreenAdaptationLabel(services))
        WatchCard {
            if (RoundLayoutMetrics.isRoundDisplay(services.activity)) {
                WatchRow(
                    title = "屏幕形状",
                    value = "圆屏（自动适配）",
                    icon = R.drawable.il_round_screen,
                    enabled = false,
                )
            } else {
                WatchSwitchRow(
                    title = "圆屏适配",
                    checked = roundScreen,
                    icon = R.drawable.il_round_screen,
                ) { value ->
                    roundScreen = value
                    session.setRoundScreen(value)
                    onSettingsChanged()
                }
            }
        }

        WatchSectionTitle("正文排版")
        WatchCard {
            ComposeSettingRange(
                title = "正文字号",
                value = session.bodyTextScale(),
                min = 75,
                max = 170,
                step = 1,
                unit = "%",
                icon = R.drawable.il_info,
            ) { value ->
                session.setBodyTextScale(value)
                onSettingsChanged()
            }
            ComposeSettingRange(
                title = "字间",
                value = session.bodyLetterSpacing(),
                min = 0,
                max = 20,
                step = 1,
                unit = "",
                icon = R.drawable.il_info,
            ) { value ->
                session.setBodyLetterSpacing(value)
                onSettingsChanged()
            }
            ComposeSettingRange(
                title = "段落间距",
                value = session.bodyParagraphSpacing(),
                min = 0,
                max = 24,
                step = 1,
                unit = "dp",
                icon = R.drawable.ic_expand,
            ) { value ->
                session.setBodyParagraphSpacing(value)
                onSettingsChanged()
            }
            ComposeSettingRange(
                title = "行距",
                value = session.bodyLineSpacing(),
                min = 100,
                max = 180,
                step = 1,
                unit = "%",
                icon = R.drawable.ic_expand,
            ) { value ->
                session.setBodyLineSpacing(value)
                onSettingsChanged()
            }
            WatchSwitchRow(
                title = "正文与一级评论加粗",
                checked = bodyBold,
                icon = R.drawable.il_bold,
            ) { value ->
                bodyBold = value
                session.setBodyBold(value)
                onSettingsChanged()
            }
        }

        WatchSectionTitle("动画")
        WatchCard {
            ComposeSettingChoice(
                title = "动画效果",
                options = listOf("关闭", "精简", "完整"),
                selected = session.motionLevel(),
                icon = R.drawable.il_splash,
            ) { value ->
                session.setMotionLevel(value)
                onSettingsChanged()
            }
            WatchRow(
                title = "恢复默认设置",
                icon = R.drawable.il_refresh,
                onClick = { resetDialog = true },
            )
        }
    }

    if (resetDialog) {
        ComposeConfirmDialog(
            title = "恢复默认显示设置",
            message = "主题、字体、间距和界面大小都将恢复为默认值。",
            onDismiss = { resetDialog = false },
            onConfirm = {
                session.resetDisplaySettings()
                darkMode = session.darkMode()
                roundScreen = session.roundScreen()
                bodyBold = session.bodyBold()
                onSettingsChanged()
                services.toast.show("已恢复默认显示设置")
            },
        )
    }
    if (themeDialog) {
        ComposeThemeDialog(
            services = services,
            onDismiss = { themeDialog = false },
            onSettingsChanged = onSettingsChanged,
        )
    }
}

@Composable
internal fun ComposeDisplayPreviewRoute(services: ComposeServices, onBack: () -> Unit) {
    val session = services.session
    val state = LocalHeyboxTheme.current
    val bodyScale = session.bodyTextScale() / 100f
    val lineScale = session.bodyLineSpacing() / 100f
    WatchPage("界面预览", onBack) {
        Text(
            text = "预览使用当前已保存的显示参数",
            color = state.muted,
            fontSize = watchSp(10f),
            modifier = Modifier.fillMaxWidth(),
        )
        WatchCard {
            Text(
                text = "文章",
                color = state.onAccent,
                fontSize = watchSp(9f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(state.accent, androidx.compose.foundation.shape.RoundedCornerShape(watchDp(4)))
                    .padding(horizontal = watchDp(8), vertical = watchDp(3)),
            )
            Text(
                text = "方屏上的社区，也可以清晰又从容",
                color = state.text,
                fontSize = watchSp(15f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = watchDp(6)),
            )
            Text(
                text = "这是一条帖子列表摘要，用来观察整体字号、卡片间距和主题颜色",
                color = state.muted,
                fontSize = watchSp(11f),
                lineHeight = watchSp(15f),
                modifier = Modifier.padding(top = watchDp(5)),
            )
            Text(
                text = "Ronan   👍 128   评论 36",
                color = state.accent,
                fontSize = watchSp(10f),
                modifier = Modifier.padding(top = watchDp(7)),
            )
        }
        WatchCard {
            Text(
                text = "帖子正文预览",
                color = state.text,
                fontSize = watchSp(16f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "这是正文第一段，用于预览文字大小、字间距与行距。\n\n这是正文第二段。调整设置后保存，再回到这里就能查看最终效果",
                color = state.text,
                fontSize = watchSp(14f * bodyScale),
                lineHeight = watchSp(14f * bodyScale * lineScale),
                fontWeight = if (session.bodyBold()) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.padding(top = watchDp(session.bodyParagraphSpacing())),
            )
        }
        WatchCard {
            Text(
                text = "评论层级预览",
                color = state.text,
                fontSize = watchSp(14f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "一级评论会稍微加粗，方便快速浏览主要观点",
                color = state.text,
                fontSize = watchSp(13f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = watchDp(7)),
            )
            Row(modifier = Modifier.padding(top = watchDp(7))) {
                Spacer(
                    modifier = Modifier
                        .width(watchDp(2))
                        .padding(vertical = watchDp(1))
                        .background(state.accent),
                )
                Text(
                    text = "二级评论使用稍轻的字重，并通过主题色竖线建立层级",
                    color = state.muted,
                    fontSize = watchSp(12f),
                    lineHeight = watchSp(15f),
                    modifier = Modifier.padding(start = watchDp(8)),
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatch(index: Int, selected: Boolean) {
    val primary = ThemePalette.primary(index).asComposeColor()
    val secondary = ThemePalette.secondary(index).asComposeColor()
    val density = LocalDensity.current.density
    val scale = LocalHeyboxTheme.current.uiScale
    val ringGap = 3f * scale * density
    val ringStroke = 2f * scale * density
    Canvas(modifier = Modifier.size(watchDp(34))) {
        drawArc(primary, 135f, 180f, true)
        drawArc(secondary, 315f, 180f, true)
        if (selected) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 2f - ringGap,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun ComposeThemeDialog(
    services: ComposeServices,
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    val session = services.session
    val currentPrimary = ThemePalette.currentPrimary(session)
    val currentSecondary = ThemePalette.currentSecondary(session)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        WatchCard(modifier = Modifier.padding(watchDp(10))) {
            Text(
                text = "颜色主题",
                color = LocalHeyboxTheme.current.text,
                fontSize = watchSp(15f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = watchDp(11), vertical = watchDp(8)),
            )
            (0 until ThemePalette.count()).chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = watchDp(8)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { index ->
                        val selected = currentPrimary == ThemePalette.primary(index)
                                && currentSecondary == ThemePalette.secondary(index)
                        Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = watchDp(5)),
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(watchDp(40))
                                    .padding(watchDp(3))
                                    .then(
                                        if (selected) Modifier.background(
                                            LocalHeyboxTheme.current.text,
                                            androidx.compose.foundation.shape.CircleShape,
                                        ) else Modifier,
                                    )
                                    .padding(watchDp(2))
                                    .clickable {
                                        session.setTheme(
                                            Format.colorHex(ThemePalette.primary(index)),
                                            Format.colorHex(ThemePalette.secondary(index)),
                                        )
                                        onSettingsChanged()
                                        onDismiss()
                                    },
                            ) {
                                ThemeSwatch(index, selected)
                            }
                            Text(
                                ThemePalette.name(index),
                                color = LocalHeyboxTheme.current.muted,
                                fontSize = watchSp(9f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun composeThemeCaption(session: SessionStore): String {
    if (ThemePalette.isDefault(session)) return "黑灰"
    val index = ThemePalette.indexOf(
        ThemePalette.currentPrimary(session),
        ThemePalette.currentSecondary(session),
    )
    return if (index >= 0) ThemePalette.name(index) else "自定义配色"
}

private fun composeScreenAdaptationLabel(services: ComposeServices): String {
    if (RoundLayoutMetrics.isRoundDisplay(services.activity)) return "屏幕适配 · 已识别圆屏手表"
    if (RoundLayoutMetrics.isRectangularWatchDisplay(services.activity)) {
        return "屏幕适配 · 已识别方屏手表"
    }
    return "屏幕适配"
}
