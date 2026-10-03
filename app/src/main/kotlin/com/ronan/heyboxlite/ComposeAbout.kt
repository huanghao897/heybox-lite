package com.ronan.heyboxlite

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun ComposeAboutScreen(
    services: ComposeServices,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var live by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        live = true
        onDispose { live = false }
    }
    WatchPage("关于", onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.mipmap.about_app_mark),
                contentDescription = "heybox Lite",
                colorFilter = ColorFilter.tint(LocalHeyboxTheme.current.text),
                modifier = Modifier.size(watchDp(58)),
            )
            Spacer(modifier = Modifier.height(watchDp(7)))
            Text(
                text = "heybox Lite",
                color = LocalHeyboxTheme.current.text,
                fontSize = watchSp(18f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(11f),
                modifier = Modifier.padding(top = watchDp(3)),
            )
            Text(
                text = "开发者：Ronan",
                color = LocalHeyboxTheme.current.text,
                fontSize = watchSp(13f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = watchDp(5)),
            )
        }

        WatchSectionTitle("信息")
        WatchCard {
            WatchRow(
                title = "公告列表",
                icon = R.drawable.il_info,
                onClick = { onNavigate("announcement_board") },
            )
            WatchRow(
                title = "交流群",
                icon = R.drawable.il_qr,
                onClick = { onNavigate("feedback_group") },
            )
            WatchRow(
                title = "检查更新",
                value = if (checking) "检查中" else "",
                icon = R.drawable.il_update,
                enabled = !checking,
                onClick = {
                    if (!checking) {
                        checking = true
                        services.toast.show("正在检查更新")
                        UpdateChecker.check(
                            BuildConfig.VERSION_NAME,
                            services.session,
                            services.session.testReleaseId(),
                            object : UpdateChecker.Callback {
                                override fun onResult(result: UpdateChecker.Result) {
                                    if (!live || services.activity.isFinishing) return
                                    checking = false
                                    if (result.updateAvailable) update = result
                                    else services.toast.show("当前已是最新版")
                                }

                                override fun onError(message: String) {
                                    if (!live || services.activity.isFinishing) return
                                    checking = false
                                    services.toast.show("检查更新失败：$message")
                                }
                            },
                        )
                    }
                },
            )
            WatchRow(
                title = "GitHub 项目",
                icon = R.drawable.il_globe,
                onClick = { onNavigate("open_url?url=https%3A%2F%2Fgithub.com%2Fhuanghao897%2Fheybox-lite") },
            )
        }

        WatchSectionTitle("说明")
        WatchCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = watchDp(14), vertical = watchDp(12)),
            ) {
                Text(
                    text = "支持 Android 7.0 及以上系统",
                    color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(12f),
                    lineHeight = watchSp(16f),
                )
                Text(
                    text = "基于 HeyWear 进行二次开发与方屏适配，非官方应用。",
                    color = LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(12f),
                    lineHeight = watchSp(16f),
                    modifier = Modifier.padding(top = watchDp(7)),
                )
            }
        }
    }
    update?.let { result ->
        ComposeUpdateDialog(
            result = result,
            onDismiss = { update = null },
            onDownload = {
                update = null
                val url = result.downloadUrl.ifEmpty { result.releaseUrl }
                onNavigate("update_download?url=${android.net.Uri.encode(url)}")
            },
        )
    }
}

@Composable
private fun ComposeUpdateDialog(
    result: UpdateChecker.Result,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        WatchCard(modifier = Modifier.padding(watchDp(10))) {
            Text(
                text = "发现新版本 ${result.version}",
                color = LocalHeyboxTheme.current.text,
                fontSize = watchSp(15f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "当前版本：${BuildConfig.VERSION_NAME}\n最新版本：${result.version}" +
                        (if (result.title.isNotBlank()) "\n发布标题：${result.title.trim()}" else "") +
                        "\n\n更新内容：\n" + result.notes.ifBlank { "暂无更新内容说明" }.take(1800),
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(12f),
                lineHeight = watchSp(16f),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                WatchActionText("稍后", onDismiss)
                Spacer(modifier = Modifier.weight(1f))
                WatchActionText("下载", onDownload)
            }
        }
    }
}

@Composable
internal fun ComposeFeedbackGroupScreen(onBack: () -> Unit) {
    WatchPage("交流群", onBack) {
        Text(
            text = "QQ群：781941517",
            color = LocalHeyboxTheme.current.text,
            fontSize = watchSp(12f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Image(
            painter = painterResource(R.drawable.qq_feedback_group_qr),
            contentDescription = "QQ群 781941517 二维码",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ComposeAnnouncementBoardScreen(services: ComposeServices, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var live by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf(listOf(composeWelcomeAnnouncement())) }
    var selected by remember { mutableStateOf<AnnouncementChecker.Item?>(null) }
    val policy = remember { AnnouncementPolicy(services.activity) }

    DisposableEffect(Unit) {
        live = true
        onDispose { live = false }
    }
    LaunchedEffect(refresh) {
        loading = true
        AnnouncementChecker.load(object : AnnouncementChecker.Callback {
            override fun onResult(value: MutableList<AnnouncementChecker.Item>) {
                if (!live || services.activity.isFinishing) return
                items = listOf(composeWelcomeAnnouncement()) +
                        value.filter { it.id != COMPOSE_WELCOME_ID }
                loading = false
            }

            override fun onError(message: String) {
                if (!live || services.activity.isFinishing) return
                items = listOf(composeWelcomeAnnouncement())
                loading = false
                services.toast.show("公告加载失败，已显示本地欢迎公告")
            }
        })
    }

    WatchPage("公告", onBack) {
        WatchRow(
            title = if (loading) "加载中" else "刷新公告",
            icon = R.drawable.il_refresh,
            enabled = !loading,
            onClick = { refresh += 1 },
        )
        items.forEach { item ->
            WatchCard {
                WatchRow(
                    title = item.title.ifBlank { "公告" },
                    value = item.updatedAt,
                    onClick = { selected = item },
                )
                Text(
                    text = item.content.take(200),
                    color = LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(11f),
                    lineHeight = watchSp(15f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = watchDp(11), vertical = watchDp(5)),
                )
            }
        }
    }

    selected?.let { item ->
        ComposeAnnouncementDialog(
            item = item,
            onDismiss = { selected = null },
            onAcknowledge = {
                if (item.id == COMPOSE_WELCOME_ID) services.session.markAnnouncementSeen(item.id)
                selected = null
            },
            onSuppress = {
                policy.suppress(item.id)
                if (item.id == COMPOSE_WELCOME_ID) services.session.markAnnouncementSeen(item.id)
                selected = null
            },
        )
    }
}

@Composable
private fun ComposeAnnouncementDialog(
    item: AnnouncementChecker.Item,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onSuppress: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        WatchCard(modifier = Modifier.padding(watchDp(10))) {
            Text(
                text = item.title,
                color = LocalHeyboxTheme.current.text,
                fontSize = watchSp(15f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.content,
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(12f),
                lineHeight = watchSp(16f),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                WatchActionText("不再提醒", onSuppress)
                Spacer(modifier = Modifier.weight(1f))
                WatchActionText("知道了", onAcknowledge)
            }
        }
    }
}

private const val COMPOSE_WELCOME_ID = "welcome-heybox-lite-1.77"

private fun composeWelcomeAnnouncement(): AnnouncementChecker.Item = AnnouncementChecker.Item(
    COMPOSE_WELCOME_ID,
    "欢迎使用 heybox Lite",
    "欢迎使用 heybox Lite。这里会放版本公告和重要提醒。\n\n" +
            "遇到 bug 或有建议，可以加入交流群：781941517。\n\n" +
            "本项目仅用于学习、研究与个人使用，请在遵守平台规则的前提下使用",
    "normal",
    BuildConfig.VERSION_NAME,
    true,
    true,
)
