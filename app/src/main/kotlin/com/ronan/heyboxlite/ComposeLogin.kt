package com.ronan.heyboxlite

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.Executors

/** QR login rendered entirely in Compose; polling remains in the existing controller. */
@Composable
internal fun ComposeLoginScreen(
    services: ComposeServices,
    onBack: () -> Unit,
    onGuest: () -> Unit,
    onLoggedIn: () -> Unit,
) {
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
    val generation = remember(controller) { intArrayOf(0) }
    val disposed = remember(controller) { booleanArrayOf(false) }
    var retry by remember(controller) { mutableStateOf(0) }
    val latestOnLoggedIn by rememberUpdatedState(onLoggedIn)
    val latestBitmap by rememberUpdatedState(bitmap)
    val listener = remember(controller) {
        object : QrLoginController.Listener {
            override fun onQrReady(url: String) {
                val current = ++generation[0]
                status = "等待扫码"
                executor.execute {
                    val generated = runCatching {
                        QrCode.create(url, 620)
                    }.getOrNull()
                    services.handler.post {
                        if (!disposed[0] && current == generation[0]) {
                            bitmap?.let { if (!it.isRecycled) it.recycle() }
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
                generation[0]++
                bitmap?.let { if (!it.isRecycled) it.recycle() }
                bitmap = null
                if (services.session.completeLogin(result)) latestOnLoggedIn()
                else status = "无法安全保存登录凭据，请重新获取二维码"
            }

            override fun onError(message: String) {
                status = message.ifBlank { "二维码获取失败" }
            }
        }
    }
    val lifecycle = remember(controller, services.activity) {
        ComposeCheckinActivityLifecycle(services.activity, controller::resume, controller::pause,
            { controller.stop() })
    }
    DisposableEffect(controller) {
        lifecycle.start()
        onDispose {
            disposed[0] = true
            generation[0]++
            lifecycle.close()
            controller.stop()
            executor.shutdownNow()
            latestBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }
    LaunchedEffect(controller, retry) {
        bitmap?.let { if (!it.isRecycled) it.recycle() }
        bitmap = null
        generation[0]++
        status = "正在获取二维码"
        val foreground = (services.activity as androidx.lifecycle.LifecycleOwner)
            .lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        controller.start(listener, foreground)
    }

    ComposeLoginContent(bitmap, status, onBack, onGuest) { retry++ }
}

@Composable
internal fun ComposeLoginContent(bitmap: Bitmap?, status: String, onBack: () -> Unit,
    onGuest: () -> Unit, onRetry: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    WatchPage("登录小黑盒", onBack) {
        WatchCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(watchDp(12)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(watchDp(7)),
            ) {
                Text("heybox Lite", color = theme.text, fontSize = watchSp(WatchType.TITLE),
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
                Text(status, color = if (status.contains("失败") || status.contains("过期"))
                    theme.accent else theme.muted, fontSize = watchSp(WatchType.CAPTION), letterSpacing = 0.sp)
                ComposeLoginQr(bitmap)
                Text("请使用小黑盒 App 扫码登录", color = theme.muted, fontSize = watchSp(WatchType.CAPTION), letterSpacing = 0.sp)
                WatchActionText("重新获取", onRetry)
                WatchActionText("游客浏览", onGuest)
            }
        }
    }
}

@Composable
internal fun ComposeLoginQr(bitmap: Bitmap?) {
    val theme = LocalHeyboxTheme.current
    val config = LocalConfiguration.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = minOf(config.screenWidthDp, config.screenHeightDp).dp
        val side = minOf(maxWidth, watchDp(150), if (theme.roundScreen) diameter * 0.55f else maxWidth)
        Box(Modifier.size(side).testTag("login-qr-frame")
            .background(Color.White, RoundedCornerShape(watchDp(WatchRadius.SMALL)))
            .border(watchDp(1), theme.hairline, RoundedCornerShape(watchDp(WatchRadius.SMALL)))
            .padding(watchDp(7)), contentAlignment = Alignment.Center) {
            if (bitmap != null && !bitmap.isRecycled) {
                Image(bitmap.asImageBitmap(), "登录二维码", Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit)
            } else {
                Text("二维码", color = Color.DarkGray, fontSize = watchSp(WatchType.SECONDARY))
            }
        }
    }
}
