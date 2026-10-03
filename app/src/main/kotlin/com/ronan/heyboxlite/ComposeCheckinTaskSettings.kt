package com.ronan.heyboxlite

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeCheckinTaskSettingsScreen(
    state: ComposeCheckinUiState,
    controller: ComposeCheckinController,
    onBack: () -> Unit,
) = ComposeCheckinTaskSettingsContent(
    state = state,
    onBack = onBack,
    onEnabled = controller::setTaskEnabled,
    onTime = controller::setTaskTime,
    onOffset = controller::setTaskOffset,
    onShare = controller::setShare,
)

@Composable
internal fun ComposeCheckinTaskSettingsContent(
    state: ComposeCheckinUiState,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onTime: (String) -> Unit,
    onOffset: (Int) -> Unit,
    onShare: (String, Boolean) -> Unit,
) {
    val task = state.status?.task
    var editingTime by rememberSaveable { mutableStateOf(false) }
    var editingOffset by rememberSaveable { mutableStateOf(false) }
    TaskSettingsPage(onBack) {
        if (state.errorMessage.isNotBlank()) TaskSettingsNotice(state.errorMessage)
        if (task == null) {
            WatchEmptyState("签到设置暂不可用")
        } else {
            val enabled = !state.taskSaving
            WatchSectionTitle("签到计划")
            CheckinPanel {
                TaskSettingsSwitchRow(
                    title = "自动签到",
                    subtitle = "在设定时间内自动执行",
                    checked = task.enabled,
                    icon = R.drawable.il_calendar,
                    enabled = enabled,
                    onCheckedChange = onEnabled,
                )
                CheckinDivider()
                TaskSettingsTimeRow(task, enabled) { editingTime = true }
                CheckinDivider()
                TaskSettingsOffsetRow(task.offsetMinutes, enabled, onOffset) {
                    editingOffset = true
                }
                if (task.platformBlocked || task.signBlocked) {
                    TaskSettingsNotice("服务已暂停此任务，当前设置会保留。")
                }
                if (state.taskSaving) TaskSettingsNotice("正在保存设置")
            }
            WatchSectionTitle("分享任务")
            CheckinPanel {
                if (task.sharing.available) {
                    TaskSettingsSwitchRow("分享帖子", task.sharing.post,
                        R.drawable.il_reading, enabled) { onShare("share_post", it) }
                    CheckinDivider()
                    TaskSettingsSwitchRow("分享游戏", task.sharing.game,
                        R.drawable.ic_game_link, enabled) { onShare("share_game", it) }
                    CheckinDivider()
                    TaskSettingsSwitchRow("分享评价", task.sharing.review,
                        R.drawable.il_star, enabled) { onShare("share_review", it) }
                } else {
                    TaskSettingsNotice("暂未开放")
                }
            }
        }
    }
    if (task != null && editingTime) {
        CheckinTaskTimeDialog(
            initial = CheckinTaskSettingsView.normalizedTime(task),
            enabled = !state.taskSaving,
            onDismiss = { editingTime = false },
            onConfirm = { value ->
                if (!state.taskSaving) {
                    editingTime = false
                    if (value != task.scheduleTime) onTime(value)
                }
            },
        )
    }
    if (task != null && editingOffset) {
        CheckinTaskOffsetDialog(
            initial = task.offsetMinutes,
            enabled = !state.taskSaving,
            onDismiss = { editingOffset = false },
            onConfirm = { value ->
                if (!state.taskSaving) {
                    editingOffset = false
                    if (value != task.offsetMinutes) onOffset(value)
                }
            },
        )
    }
}

@Composable
private fun TaskSettingsPage(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalHeyboxTheme.current
    val scrollState = rememberScrollState()
    val rotary = LocalHeyboxRotaryRequest.current
    LaunchedEffect(rotary?.serial) {
        if (rotary != null) scrollState.scrollBy(rotary.distance.toFloat())
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(theme.background)) {
        val bottomInset = if (theme.roundScreen) minOf(maxWidth, maxHeight) * 0.25f else 0.dp
        Column(Modifier.fillMaxSize()) {
            ComposePageHeader("签到设置", onBack)
            Column(
                modifier = Modifier.weight(1f)
                    // Reserve the narrow lower chord outside the scrolling viewport.
                    .padding(bottom = bottomInset)
                    .verticalScroll(scrollState)
                    .watchHorizontalPadding()
                    .padding(bottom = watchListEndPadding()),
                verticalArrangement = Arrangement.spacedBy(watchDp(9)),
                content = content,
            )
        }
    }
}

@Composable
private fun TaskSettingsSwitchRow(
    title: String,
    checked: Boolean,
    icon: Int,
    enabled: Boolean,
    subtitle: String = "",
    onCheckedChange: (Boolean) -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch,
                onValueChange = onCheckedChange)
            .heightIn(min = watchDp(44))
            .padding(horizontal = watchDp(10), vertical = watchDp(9)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(watchDp(7)),
    ) {
        TaskSettingsIcon(icon, title)
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) theme.text else theme.subtle,
                fontSize = watchSp(13f), fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth())
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = theme.muted, fontSize = watchSp(10f),
                    modifier = Modifier.padding(top = watchDp(3)))
            }
        }
        val position by animateFloatAsState(
            if (checked) 1f else 0f,
            animationSpec = tween(if (Motions.off()) 0 else 160),
            label = "task-settings-switch",
        )
        val track = if (checked) {
            if (theme.dark) Color(0xFF77777D) else Color(0xFFA6A6AB)
        } else {
            if (theme.dark) Color(0xFF3A3A3E) else Color(0xFFD1D1D6)
        }
        Canvas(Modifier.size(watchDp(36), watchDp(22))
            .testTag("task-switch-$title")
            .clip(RoundedCornerShape(50))
            .background(if (enabled) track else track.copy(alpha = 0.45f))) {
            val inset = size.height / 2f
            drawCircle(
                color = if (!enabled) theme.subtle else if (theme.dark) Color(0xFFF5F5F7)
                    else Color.White,
                radius = size.height * 9f / 22f,
                center = Offset(inset + (size.width - 2 * inset) * position, inset),
            )
        }
    }
}

@Composable
private fun TaskSettingsTimeRow(
    task: CheckinCenterClient.Task,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val time = CheckinTaskSettingsView.scheduleLabel(task)
    val window = taskSettingsWindowLabel(task)
    BoxWithConstraints(Modifier.fillMaxWidth()
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "修改执行时间",
            onClick = onClick)
        .semantics(mergeDescendants = true) { stateDescription = "$time，$window" }
        .padding(horizontal = watchDp(10), vertical = watchDp(9))) {
        val stacked = maxWidth < watchDp(180) * theme.textScale * LocalDensity.current.fontScale
        Column(verticalArrangement = Arrangement.spacedBy(watchDp(4))) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(watchDp(7))) {
                TaskSettingsIcon(R.drawable.il_clock, "执行时间")
                Text("执行时间", color = if (enabled) theme.text else theme.subtle,
                    fontSize = watchSp(13f), modifier = Modifier.weight(1f))
                if (!stacked) TaskSettingsTimeValue(time, enabled)
            }
            if (stacked) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TaskSettingsTimeValue(time, enabled)
                }
            }
            Text(window, color = theme.muted, fontSize = watchSp(10f),
                modifier = Modifier.padding(start = watchDp(25)))
        }
    }
}

@Composable
private fun TaskSettingsTimeValue(time: String, enabled: Boolean) {
    Text(time, color = if (enabled) LocalHeyboxTheme.current.muted
        else LocalHeyboxTheme.current.subtle, fontSize = watchSp(12f))
    Icon(painterResource(R.drawable.il_chevron), contentDescription = "修改执行时间",
        tint = LocalHeyboxTheme.current.muted, modifier = Modifier.size(watchDp(14)))
}

internal fun taskSettingsWindowLabel(task: CheckinCenterClient.Task): String = when {
    task.windowStart.isNotBlank() && task.windowEnd.isNotBlank() ->
        "${task.windowStart} - ${task.windowEnd}"
    task.windowStart.isNotBlank() -> task.windowStart
    task.windowEnd.isNotBlank() -> task.windowEnd
    else -> "时间窗暂不可用"
}

@Composable
private fun TaskSettingsOffsetRow(
    value: Int,
    enabled: Boolean,
    onOffset: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    BoxWithConstraints(Modifier.fillMaxWidth()
        .padding(horizontal = watchDp(10), vertical = watchDp(6))) {
        val stacked = maxWidth < watchDp(214) * theme.textScale * LocalDensity.current.fontScale
        Column(verticalArrangement = Arrangement.spacedBy(watchDp(4))) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(watchDp(7))) {
                TaskSettingsIcon(R.drawable.il_shuffle, "随机偏移")
                Text("随机偏移", color = theme.text, fontSize = watchSp(13f),
                    modifier = Modifier.weight(1f))
                if (!stacked) TaskSettingsOffsetStepper(value, enabled, onOffset, onEdit)
            }
            if (stacked) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TaskSettingsOffsetStepper(value, enabled, onOffset, onEdit)
                }
            }
        }
    }
}

@Composable
private fun TaskSettingsOffsetStepper(
    value: Int,
    enabled: Boolean,
    onOffset: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val valueStyle = LocalTextStyle.current.merge(TextStyle(
        fontSize = watchSp(12f), lineHeight = watchSp(16f), letterSpacing = 0.sp,
    ))
    val unitStyle = LocalTextStyle.current.merge(TextStyle(
        fontSize = watchSp(10f), lineHeight = watchSp(14f), letterSpacing = 0.sp,
    ))
    val numberWidth = with(density) {
        maxOf(measurer.measure(AnnotatedString("720"), style = valueStyle).size.width,
            measurer.measure(AnnotatedString("888"), style = valueStyle).size.width).toDp()
    }
    val valueWidth = maxOf(watchDp(40), numberWidth + watchDp(10))
    val controlWidth = watchDp(32) * 2 + valueWidth
    val unitWidth = with(density) {
        measurer.measure(AnnotatedString("分钟"), style = unitStyle).size.width.toDp()
    } + watchDp(3)
    val preferredWidth = maxOf(watchDp(144), controlWidth + unitWidth)
    BoxWithConstraints(Modifier.widthIn(max = preferredWidth)) {
        val availableWidth = maxWidth
        val inlineUnit = availableWidth >= controlWidth + unitWidth
        Column {
            if (availableWidth >= controlWidth) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TaskSettingsStepButton(R.drawable.il_minus, "减少随机偏移", enabled && value > 0) {
                        onOffset((value - 30).coerceIn(0, 720))
                    }
                    TaskSettingsOffsetValue(value, enabled, onEdit, valueStyle, Modifier.width(valueWidth))
                    TaskSettingsStepButton(R.drawable.il_plus, "增加随机偏移", enabled && value < 720) {
                        onOffset((value + 30).coerceIn(0, 720))
                    }
                    if (inlineUnit) {
                        Text("分钟", color = theme.muted, style = unitStyle,
                            modifier = Modifier.padding(start = watchDp(3)))
                    }
                }
            } else {
                TaskSettingsOffsetValue(value, enabled, onEdit, valueStyle, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TaskSettingsStepButton(R.drawable.il_minus, "减少随机偏移", enabled && value > 0) {
                        onOffset((value - 30).coerceIn(0, 720))
                    }
                    Text("分钟", color = theme.muted, style = unitStyle)
                    TaskSettingsStepButton(R.drawable.il_plus, "增加随机偏移", enabled && value < 720) {
                        onOffset((value + 30).coerceIn(0, 720))
                    }
                }
            }
            if (availableWidth >= controlWidth && !inlineUnit) {
                Text("分钟", color = theme.muted, style = unitStyle,
                    modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun TaskSettingsOffsetValue(
    value: Int,
    enabled: Boolean,
    onEdit: () -> Unit,
    style: TextStyle,
    modifier: Modifier,
) {
    Box(modifier.heightIn(min = watchDp(36))
        .clickable(enabled = enabled, role = Role.Button, onClick = onEdit)
        .semantics {
            contentDescription = "输入随机偏移"
            stateDescription = "$value 分钟"
        }, contentAlignment = Alignment.Center) {
        Text(value.toString(), color = if (enabled) LocalHeyboxTheme.current.text
            else LocalHeyboxTheme.current.subtle, style = style, textAlign = TextAlign.Center,
            maxLines = 1, softWrap = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = watchDp(4)))
    }
}

@Composable
private fun TaskSettingsStepButton(icon: Int, label: String, enabled: Boolean,
    onClick: () -> Unit) {
    Box(Modifier.size(watchDp(32), watchDp(36))
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = label,
            onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = label,
            tint = if (enabled) LocalHeyboxTheme.current.text else LocalHeyboxTheme.current.subtle,
            modifier = Modifier.size(watchDp(16)))
    }
}

@Composable
private fun TaskSettingsIcon(icon: Int, label: String) {
    Icon(painterResource(icon), contentDescription = label,
        tint = LocalHeyboxTheme.current.muted, modifier = Modifier.size(watchDp(18)))
}

@Composable
private fun TaskSettingsNotice(message: String) {
    Text(message, color = LocalHeyboxTheme.current.muted, fontSize = watchSp(11f),
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = watchDp(10), vertical = watchDp(8)))
}
