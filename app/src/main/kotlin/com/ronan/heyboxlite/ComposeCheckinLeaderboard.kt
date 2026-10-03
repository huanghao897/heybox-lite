package com.ronan.heyboxlite

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

internal enum class ComposeCheckinBoard {
    CHECKIN,
    SPONSORSHIP,
}

internal data class ComposeCheckinLeaderboardState(
    val loading: Boolean = false,
    val errorMessage: String = "",
    val data: CheckinLeaderboard.Data? = null,
    val board: ComposeCheckinBoard = ComposeCheckinBoard.CHECKIN,
    val avatars: Map<String, Bitmap> = emptyMap(),
)

internal class ComposeCheckinLeaderboardController(
    private val services: ComposeServices,
    private val coordinator: CheckinCenterCoordinator,
) {
    var uiState by mutableStateOf(ComposeCheckinLeaderboardState())
        private set

    private var closed = false
    private var started = false
    private var generation = 0

    fun start() {
        if (closed || started) return
        started = true
        refresh()
    }

    fun close() {
        closed = true
        generation++
    }

    fun refresh() {
        if (closed || uiState.loading) return
        val request = ++generation
        uiState = uiState.copy(loading = true, errorMessage = "")
        coordinator.getLeaderboard(object : CheckinCenterClient.Callback<CheckinLeaderboard.Data> {
            override fun onSuccess(value: CheckinLeaderboard.Data) {
                if (closed || request != generation) return
                uiState = uiState.copy(loading = false, errorMessage = "", data = value)
                loadAvatars(value, request)
            }

            override fun onError(error: CheckinCenterClient.ApiError) {
                if (closed || request != generation) return
                uiState = uiState.copy(
                    loading = false,
                    errorMessage = error?.message?.takeIf { it.isNotEmpty() } ?: "排行榜加载失败",
                )
            }
        })
    }

    fun select(board: ComposeCheckinBoard) {
        if (uiState.board != board) uiState = uiState.copy(board = board)
    }

    private fun loadAvatars(value: CheckinLeaderboard.Data, request: Int) {
        if (services.session.noImage()) return
        val target = (64f * services.activity.resources.displayMetrics.density).toInt()
        (value.checkin + value.sponsorship).forEach { entry ->
            val url = entry.avatarUrl
            if (url.isEmpty() || uiState.avatars.containsKey(url)) return@forEach
            ImageLoader.load(url, target) { bitmap ->
                if (closed || request != generation || bitmap == null) return@load
                uiState = uiState.copy(avatars = uiState.avatars + (url to bitmap))
            }
        }
    }
}

@Composable
internal fun ComposeCheckinLeaderboardScreen(
    services: ComposeServices,
    onBack: () -> Unit,
) {
    HeyboxComposeTheme(services.theme) {
        val coordinator = services.checkin
        if (coordinator == null) {
            WatchPage("排行榜", onBack) { WatchEmptyState("排行榜服务暂不可用") }
            return@HeyboxComposeTheme
        }
        val controller = remember(coordinator) {
            ComposeCheckinLeaderboardController(services, coordinator)
        }
        DisposableEffect(controller) {
            controller.start()
            onDispose { controller.close() }
        }
        ComposeCheckinLeaderboardContent(controller.uiState, onBack, controller::select, controller::refresh)
    }
}

@Composable
private fun ComposeCheckinLeaderboardContent(
    state: ComposeCheckinLeaderboardState,
    onBack: () -> Unit,
    onBoardSelected: (ComposeCheckinBoard) -> Unit,
    onRetry: () -> Unit,
) {
    WatchPage("排行榜", onBack) {
        when {
            state.loading -> WatchCard(highlighted = true) {
                Text("正在加载", color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(15f), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(watchDp(4)))
                Text("正在读取排行榜", color = LocalHeyboxTheme.current.muted, fontSize = watchSp(12f))
            }
            state.errorMessage.isNotEmpty() -> WatchCard(highlighted = true) {
                Text("暂时无法加载", color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(15f), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(watchDp(4)))
                Text(state.errorMessage, color = LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(12f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(watchDp(7)))
                ComposeCheckinQuietButton("重新加载", onClick = onRetry)
            }
            state.data != null -> {
                WatchSectionTitle("榜单")
                ComposeCheckinModeSelector(
                    first = "连续签到",
                    second = "赞助排行",
                    firstSelected = state.board == ComposeCheckinBoard.CHECKIN,
                    onFirst = { onBoardSelected(ComposeCheckinBoard.CHECKIN) },
                    onSecond = { onBoardSelected(ComposeCheckinBoard.SPONSORSHIP) },
                )
                Spacer(modifier = Modifier.height(watchDp(7)))
                ComposeCheckinBoardCard(state)
            }
            else -> WatchEmptyState("暂无数据")
        }
    }
}

@Composable
private fun ComposeCheckinBoardCard(state: ComposeCheckinLeaderboardState) {
    val data = state.data ?: return
    val entries = if (state.board == ComposeCheckinBoard.CHECKIN) data.checkin else data.sponsorship
    val title = if (state.board == ComposeCheckinBoard.CHECKIN) "连续签到" else "赞助排行"
    val subtitle = if (state.board == ComposeCheckinBoard.CHECKIN) {
        "按连续签到天数排序"
    } else {
        "按累计赞助金额排序"
    }
    WatchCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = LocalHeyboxTheme.current.text, fontSize = watchSp(14.5f),
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${entries.size} 人", color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(11f))
        }
        Text(subtitle, color = LocalHeyboxTheme.current.muted,
            fontSize = watchSp(11f), modifier = Modifier.padding(top = watchDp(2)))
        if (entries.isEmpty()) {
            WatchEmptyState("暂无数据")
        } else {
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(watchDp(1)))
                }
                ComposeCheckinLeaderboardRow(entry, state.avatars[entry.avatarUrl])
            }
        }
    }
}

@Composable
private fun ComposeCheckinLeaderboardRow(
    entry: CheckinLeaderboard.Entry,
    avatar: Bitmap?,
) {
    val theme = LocalHeyboxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = watchDp(5)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.rank.toString(),
            color = if (entry.rank <= 3) theme.accent else theme.muted,
            fontSize = watchSp(11.5f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(watchDp(23)),
        )
        Box(
            modifier = Modifier
                .size(watchDp(28))
                .clip(CircleShape)
                .background(theme.panelElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar.asImageBitmap(),
                    contentDescription = "${entry.displayName}头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(watchDp(28)).clip(CircleShape),
                )
            } else {
                Text(entry.initial, color = theme.text, fontSize = watchSp(11f),
                    fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.width(watchDp(7)))
        Text(
            entry.displayName,
            color = theme.text,
            fontSize = watchSp(12f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(watchDp(5)))
        Text(
            entry.valueLabel,
            color = theme.text,
            fontSize = watchSp(11f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = watchDp(50), max = watchDp(96)),
        )
    }
}

@Preview(name = "Leaderboard square", showBackground = true, widthDp = 320, heightDp = 420)
@Composable
private fun ComposeCheckinLeaderboardPreview() {
    val data = CheckinLeaderboard.Data(
        listOf(
            CheckinLeaderboard.Entry(1, "夜航星", 32, "32 天", "夜"),
            CheckinLeaderboard.Entry(2, "小盒子", 28, "28 天", "小"),
            CheckinLeaderboard.Entry(3, "观测员", 21, "21 天", "观"),
        ),
        emptyList(),
    )
    HeyboxComposeTheme(composePreviewTheme(roundScreen = false)) {
        ComposeCheckinLeaderboardContent(
            ComposeCheckinLeaderboardState(data = data),
            onBack = {},
            onBoardSelected = {},
            onRetry = {},
        )
    }
}
