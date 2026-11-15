package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight

/** Keep the account boundary visible even after the first-use tutorial is completed. */
@Composable
internal fun ComposeCheckinAccountGuide(topic: TutorialTopic) {
    val theme = LocalHeyboxTheme.current
    val tutorials = LocalTutorialState.current
    val registering = topic == TutorialTopic.SERVICE_REGISTER
    val official = topic == TutorialTopic.HEYBOX_LOGIN
    val title = when {
        official -> "第 2 步 · 登录小黑盒账号"
        registering -> "第 1 步 · 注册签到服务账号"
        else -> "第 1 步 · 登录签到服务账号"
    }
    val message = if (official) "填写要签到的小黑盒手机号及验证码或密码，不是签到服务账号。"
        else "这是你在签到网站使用的账号，与小黑盒账号不同。连接成功后，还需用手机号登录小黑盒。"
    WatchCard(Modifier.testTag("checkin-account-guide")) {
        Column(Modifier.padding(watchDp(7))) {
            Text(title, color = theme.text, fontSize = watchSp(WatchType.SECONDARY), fontWeight = FontWeight.SemiBold)
            Text(message, color = theme.muted, fontSize = watchSp(WatchType.CAPTION),
                lineHeight = watchLineHeight(WatchType.CAPTION), modifier = Modifier.padding(top = watchDp(4)))
        }
        if (tutorials != null) WatchRow("查看操作教程", icon = topic.icon, onClick = { tutorials.replay(topic) })
    }
}
