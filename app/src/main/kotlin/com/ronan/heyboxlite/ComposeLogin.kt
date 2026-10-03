package com.ronan.heyboxlite

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

/** QR login rendered entirely in Compose; polling remains in the existing controller. */
@Composable
internal fun ComposeLoginScreen(
    services: ComposeServices,
    onBack: () -> Unit,
    onGuest: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val controller = remember(services.api, services.handler) {
        QrLoginController(services.api, services.handler)
    }
    val executor = remember(controller) {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "heybox-compose-qr").apply { isDaemon = true }
        }
    }
    var bitmap by remember(controller) { mutableStateOf<Bitmap?>(null) }
    var status by remember(controller) { mutableStateOf("正在获取二维码") }
    var generation by remember(controller) { mutableStateOf(0) }
    var retry by remember(controller) { mutableStateOf(0) }
    val latestOnLoggedIn by rememberUpdatedState(onLoggedIn)
    val latestBitmap by rememberUpdatedState(bitmap)
    val listener = remember(controller) {
        object : QrLoginController.Listener {
            override fun onQrReady(url: String) {
                val current = ++generation
                status = "等待扫码"
                executor.execute {
                    val generated = runCatching {
                        QrCode.create(url, 620)
                    }.getOrNull()
                    services.handler.post {
                        if (current == generation) {
                            bitmap = generated
                            if (generated == null) status = "二维码生成失败"
                        } else if (generated != null && !generated.isRecycled) {
                            generated.recycle()
                        }
                    }
                }
            }

            override fun onStatus(value: String) {
                status = value
            }

            override fun onLogin(result: org.json.JSONObject) {
                services.session.saveLogin(result)
                latestOnLoggedIn()
            }

            override fun onError(message: String) {
                status = message.ifBlank { "二维码获取失败" }
            }
        }
    }
    DisposableEffect(controller) {
        onDispose {
            controller.stop()
            executor.shutdownNow()
            latestBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }
    LaunchedEffect(controller, retry) {
        bitmap = null
        generation++
        status = "正在获取二维码"
        controller.start(listener)
    }

    WatchPage("登录小黑盒", onBack) {
        WatchCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(watchDp(12)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(watchDp(7)),
            ) {
                Text("heybox Lite", color = theme.text, fontSize = watchSp(18f),
                    fontWeight = FontWeight.SemiBold)
                Text(status, color = if (status.contains("失败") || status.contains("过期"))
                    theme.accent else theme.muted, fontSize = watchSp(11f))
                Box(
                    modifier = Modifier
                        .size(watchDp(150))
                        .background(Color.White, RoundedCornerShape(watchDp(7)))
                        .border(watchDp(1), theme.hairline, RoundedCornerShape(watchDp(7)))
                        .padding(watchDp(7)),
                    contentAlignment = Alignment.Center,
                ) {
                    val current = bitmap
                    if (current != null && !current.isRecycled) {
                        Image(current.asImageBitmap(), "登录二维码", Modifier.size(watchDp(136)),
                            contentScale = ContentScale.FillBounds)
                    } else {
                        Text("二维码", color = Color.DarkGray, fontSize = watchSp(12f))
                    }
                }
                Text("请使用小黑盒 App 扫码登录", color = theme.muted, fontSize = watchSp(11f))
                WatchActionText("重新获取") { retry++ }
                WatchActionText("游客浏览", onGuest)
            }
        }
    }
}
