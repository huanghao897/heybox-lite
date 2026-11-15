package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeCheckinPrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    WatchActionButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        fontSize = WatchType.SECONDARY,
        primary = true,
    )
}

@Composable
internal fun ComposeCheckinQuietButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    WatchActionButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ComposeCheckinTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
) {
    val theme = LocalHeyboxTheme.current
    val fieldStyle = TextStyle(color = theme.text, fontSize = watchSp(WatchType.SECONDARY), letterSpacing = 0.sp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = watchDp(40))
            .testTag("checkin-field-$placeholder")
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(theme.panelElevated, RoundedCornerShape(watchDp(WatchRadius.MEDIUM)))
            .padding(horizontal = watchDp(10), vertical = watchDp(9)),
        textStyle = fieldStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation()
        else VisualTransformation.None,
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = fieldStyle.copy(color = theme.subtle),
                        maxLines = 3)
                }
                field()
            }
        },
    )
}

@Composable
private fun CheckinCodeRow(
    field: @Composable (Modifier) -> Unit,
    button: @Composable (Modifier) -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < (210 * theme.textScale * fontScale).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(watchDp(6))) {
                field(Modifier.fillMaxWidth())
                button(Modifier.fillMaxWidth())
            }
        } else Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(watchDp(6))) {
            field(Modifier.weight(1f))
            button(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ComposeCheckinModeSelector(
    first: String,
    second: String,
    firstSelected: Boolean,
    enabled: Boolean = true,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(watchDp(1), theme.hairline, RoundedCornerShape(watchDp(WatchRadius.MEDIUM)))
            .background(theme.panelElevated, RoundedCornerShape(watchDp(WatchRadius.MEDIUM)))
            .padding(watchDp(3)),
        horizontalArrangement = Arrangement.spacedBy(watchDp(3)),
    ) {
        ComposeCheckinModeItem(first, firstSelected, enabled, onFirst)
        ComposeCheckinModeItem(second, !firstSelected, enabled, onSecond)
    }
}

@Composable
private fun RowScope.ComposeCheckinModeItem(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    Box(
        modifier = Modifier
            .weight(1f)
            .height(watchDp(34))
            .background(
                if (selected) theme.panel else Color.Transparent,
                RoundedCornerShape(watchDp(WatchRadius.SMALL)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) theme.text else theme.subtle,
            fontSize = watchSp(WatchType.CAPTION),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ComposeCheckinPairingScreen(
    state: ComposeCheckinUiState,
    controller: ComposeCheckinController,
    onBack: () -> Unit,
) {
    val pairing = state.pairing
    val tutorialTopic = if (state.serviceMode == CheckinServiceAccountFlow.Mode.REGISTER)
        TutorialTopic.SERVICE_REGISTER else TutorialTopic.SERVICE_LOGIN
    ComposeTutorialTrigger(tutorialTopic, pairing != null)
    val passwordRequester = remember { FocusRequester() }
    val emailCodeRequester = remember { FocusRequester() }
    LaunchedEffect(state.focusTarget) {
        when (state.focusTarget) {
            "service_password" -> passwordRequester.requestFocus()
            "service_email_code" -> emailCodeRequester.requestFocus()
        }
        if (state.focusTarget.isNotEmpty()) controller.consumeFocusTarget()
    }

    WatchPage("连接签到服务", onBack) {
        ComposeCheckinAccountGuide(tutorialTopic)
        if (pairing == null) {
            WatchCard(highlighted = true) {
                Text("正在创建连接", color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(WatchType.SUBTITLE), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(watchDp(5)))
                Text("正在等待签到服务返回配对码", color = LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(WatchType.SECONDARY))
            }
        } else {
            WatchCard(highlighted = true) {
                Text("配对码", color = LocalHeyboxTheme.current.muted, fontSize = watchSp(WatchType.CAPTION))
                Spacer(modifier = Modifier.height(watchDp(2)))
                Text(
                    pairing.userCode,
                    color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(WatchType.DISPLAY),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(watchDp(4)))
                val remaining = state.pairingRemainingSeconds.coerceAtLeast(0L)
                Text(
                    "有效时间 ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}",
                    color = LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(WatchType.CAPTION),
                )
                Spacer(modifier = Modifier.height(watchDp(5)))
                Text(
                    "使用签到服务账号完成连接，手机凭据只用于登录小黑盒。",
                    color = LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(WatchType.CAPTION),
                )
            }

            WatchCard {
                if (pairing.registrationOpen) {
                    ComposeCheckinModeSelector(
                        first = "登录服务",
                        second = "注册服务",
                        firstSelected = state.serviceMode == CheckinServiceAccountFlow.Mode.LOGIN,
                        enabled = state.serviceControlsEnabled,
                        onFirst = { controller.switchServiceMode(CheckinServiceAccountFlow.Mode.LOGIN) },
                        onSecond = { controller.switchServiceMode(CheckinServiceAccountFlow.Mode.REGISTER) },
                    )
                    Spacer(modifier = Modifier.height(watchDp(9)))
                }
                ComposeCheckinTextField(
                    value = state.serviceUsername,
                    placeholder = "签到服务账号",
                    onValueChange = controller::setServiceUsername,
                    enabled = state.serviceControlsEnabled,
                )
                Spacer(modifier = Modifier.height(watchDp(7)))
                ComposeCheckinTextField(
                    value = state.servicePassword,
                    placeholder = "签到服务密码",
                    onValueChange = controller::setServicePassword,
                    enabled = state.serviceControlsEnabled,
                    password = true,
                    focusRequester = passwordRequester,
                )
                if (state.serviceMode == CheckinServiceAccountFlow.Mode.REGISTER) {
                    Spacer(modifier = Modifier.height(watchDp(7)))
                    ComposeCheckinTextField(
                        value = state.servicePasswordConfirmation,
                        placeholder = "再次输入密码",
                        onValueChange = controller::setServicePasswordConfirmation,
                        enabled = state.serviceControlsEnabled,
                        password = true,
                    )
                    Spacer(modifier = Modifier.height(watchDp(5)))
                    Text(
                        "密码至少 12 位，并包含三类字符。",
                        color = LocalHeyboxTheme.current.subtle,
                        fontSize = watchSp(WatchType.CAPTION),
                    )
                    if (pairing.registrationEmailRequired) {
                        Spacer(modifier = Modifier.height(watchDp(7)))
                        ComposeCheckinTextField(
                            value = state.serviceEmail,
                            placeholder = "注册邮箱",
                            onValueChange = controller::setServiceEmail,
                            enabled = state.serviceControlsEnabled,
                            keyboardType = KeyboardType.Email,
                        )
                        Spacer(modifier = Modifier.height(watchDp(6)))
                        CheckinCodeRow(field = { modifier ->
                            ComposeCheckinTextField(
                                value = state.serviceEmailCode,
                                placeholder = "6 位邮箱验证码",
                                onValueChange = controller::setServiceEmailCode,
                                enabled = state.serviceControlsEnabled,
                                keyboardType = KeyboardType.Number,
                                focusRequester = emailCodeRequester,
                                modifier = modifier,
                            )
                        }, button = { modifier ->
                            WatchActionButton(
                                text = state.serviceEmailButton,
                                onClick = controller::sendRegistrationEmail,
                                enabled = state.serviceEmailButtonEnabled,
                                modifier = modifier.heightIn(min = watchDp(40)),
                                fontSize = WatchType.CAPTION,
                            )
                        })
                    }
                }
                Spacer(modifier = Modifier.height(watchDp(10)))
                ComposeCheckinPrimaryButton(
                    text = if (state.serviceMode == CheckinServiceAccountFlow.Mode.LOGIN) {
                        "登录签到服务"
                    } else {
                        "注册签到服务账号"
                    },
                    enabled = state.serviceControlsEnabled,
                    onClick = controller::submitServiceAccount,
                )
                ComposeCheckinStatusText(state.serviceStatus, state.serviceStatusKind)
            }
            ComposeCheckinQuietButton("取消连接", onClick = onBack)
        }
    }
}

@Composable
internal fun ComposeCheckinMobileLoginScreen(
    state: ComposeCheckinUiState,
    controller: ComposeCheckinController,
    onBack: () -> Unit,
) {
    ComposeTutorialTrigger(TutorialTopic.HEYBOX_LOGIN)
    val codeRequester = remember { FocusRequester() }
    val passwordRequester = remember { FocusRequester() }
    LaunchedEffect(state.focusTarget) {
        when (state.focusTarget) {
            "mobile_code" -> codeRequester.requestFocus()
            "mobile_password" -> passwordRequester.requestFocus()
        }
        if (state.focusTarget.isNotEmpty()) controller.consumeFocusTarget()
    }
    WatchPage("手机号登录小黑盒", onBack) {
        ComposeCheckinAccountGuide(TutorialTopic.HEYBOX_LOGIN)
        WatchCard {
            Text("登录小黑盒", color = LocalHeyboxTheme.current.text,
                fontSize = watchSp(WatchType.SUBTITLE), fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(watchDp(4)))
            Text("仅用于服务器自动签到", color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(WatchType.SECONDARY))
            Spacer(modifier = Modifier.height(watchDp(9)))
            ComposeCheckinModeSelector(
                first = "短信验证码",
                second = "密码",
                firstSelected = state.mobileMode == CheckinMobileLoginFlow.Mode.SMS,
                enabled = state.mobileControlsEnabled,
                onFirst = { controller.switchMobileMode(CheckinMobileLoginFlow.Mode.SMS) },
                onSecond = { controller.switchMobileMode(CheckinMobileLoginFlow.Mode.PASSWORD) },
            )
            Spacer(modifier = Modifier.height(watchDp(9)))
            if (state.mobileMode == CheckinMobileLoginFlow.Mode.SMS) {
                CheckinCodeRow(field = { modifier ->
                    ComposeCheckinTextField(
                        value = state.mobilePhone,
                        placeholder = "+86 手机号",
                        onValueChange = controller::setMobilePhone,
                        enabled = state.mobileControlsEnabled && !state.mobileHasSession,
                        keyboardType = KeyboardType.Phone,
                        modifier = modifier,
                    )
                }, button = { modifier ->
                    WatchActionButton(
                        text = state.mobileSmsButton,
                        onClick = controller::sendSmsCode,
                        enabled = state.mobileSmsButtonEnabled,
                        modifier = modifier.heightIn(min = watchDp(40)),
                        fontSize = WatchType.CAPTION,
                    )
                })
                Spacer(modifier = Modifier.height(watchDp(7)))
                ComposeCheckinTextField(
                    value = state.mobileCode,
                    placeholder = "短信验证码",
                    onValueChange = controller::setMobileCode,
                    enabled = state.mobileControlsEnabled && state.mobileHasSession,
                    keyboardType = KeyboardType.Number,
                    focusRequester = codeRequester,
                )
                Spacer(modifier = Modifier.height(watchDp(9)))
                ComposeCheckinPrimaryButton(
                    "登录小黑盒并连接",
                    enabled = state.mobileControlsEnabled && state.mobileHasSession,
                    onClick = controller::submitSmsCode,
                )
            } else {
                ComposeCheckinTextField(
                    value = state.mobilePhone,
                    placeholder = "+86 手机号",
                    onValueChange = controller::setMobilePhone,
                    enabled = state.mobileControlsEnabled,
                    keyboardType = KeyboardType.Phone,
                )
                Spacer(modifier = Modifier.height(watchDp(7)))
                ComposeCheckinTextField(
                    value = state.mobilePassword,
                    placeholder = "小黑盒登录密码",
                    onValueChange = controller::setMobilePassword,
                    enabled = state.mobileControlsEnabled,
                    password = true,
                    focusRequester = passwordRequester,
                )
                Spacer(modifier = Modifier.height(watchDp(9)))
                ComposeCheckinPrimaryButton(
                    "登录小黑盒并连接",
                    enabled = state.mobileControlsEnabled,
                    onClick = controller::submitPasswordLogin,
                )
            }
            ComposeCheckinStatusText(
                state.mobileStatus.ifEmpty { "密码仅用于本次登录" },
                state.mobileStatusKind,
            )
        }
        ComposeCheckinQuietButton("返回签到中心", onClick = onBack)
    }
}

@Composable
internal fun ComposeCheckinStatusText(message: String, kind: ComposeCheckinStatusKind) {
    if (message.isEmpty()) return
    val theme = LocalHeyboxTheme.current
    Text(
        message,
        color = when (kind) {
            ComposeCheckinStatusKind.ERROR -> theme.muted
            ComposeCheckinStatusKind.ACCENT -> theme.accent
            else -> theme.muted
        },
        fontSize = watchSp(WatchType.CAPTION),
        modifier = Modifier.padding(top = watchDp(7)),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}
