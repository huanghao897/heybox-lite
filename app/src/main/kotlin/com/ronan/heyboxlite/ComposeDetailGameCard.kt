@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeDetailGameCard(
    block: RichContent.Block,
    media: ComposeMediaSettings,
    services: ComposeServices?,
) {
    val state = rememberDetailGameCard(block, services?.gameDetails)
    val data = state.data
    val theme = LocalHeyboxTheme.current
    Row(Modifier.fillMaxWidth().testTag("detail-game-card-${block.value}")
        .clip(RoundedCornerShape(watchDp(8)))
        .background(theme.panelElevated).padding(watchDp(8)),
        verticalAlignment = Alignment.CenterVertically) {
        if (media.enabled && !media.gameCardNoImage && !data?.coverUrl.isNullOrBlank()) {
            val coverSize = watchDp(if (theme.roundScreen) 44 else 52)
            ComposeRemoteImage(data?.coverUrl.orEmpty(), theme, false, coverSize,
                RoundedCornerShape(watchDp(6)), "游戏封面", Modifier.size(coverSize))
        } else {
            Icon(painterResource(R.drawable.ic_game_link), null,
                tint = theme.accent, modifier = Modifier.size(watchDp(25)))
        }
        Spacer(Modifier.width(watchDp(8)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(watchDp(3))) {
            Text(data?.name?.ifBlank { "游戏" } ?: "游戏", fontSize = watchSp(12.5f),
                lineHeight = watchSp(17f), letterSpacing = 0.sp, color = theme.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val metadata = listOfNotNull(data?.platforms?.takeIf { it.isNotBlank() },
                data?.score?.takeIf { it.isNotBlank() }?.let { "评分 $it" },
                data?.followers?.takeIf { it.isNotBlank() }?.let { "关注 $it" }).joinToString(" · ")
            if (metadata.isNotBlank()) Text(metadata, fontSize = watchSp(9.5f),
                lineHeight = watchSp(13f), letterSpacing = 0.sp, color = theme.muted)
            if (data != null) DetailGameCardPrices(data)
            val status = when {
                state.loading -> "正在获取游戏信息"
                data?.hasVisibleDetails() != true -> "游戏信息暂不可用"
                else -> ""
            }
            if (status.isNotBlank()) Text(status, fontSize = watchSp(10f),
                lineHeight = watchSp(14f), letterSpacing = 0.sp, color = theme.muted,
                modifier = Modifier.testTag("detail-game-state-${block.value}"))
        }
    }
}

@Composable
private fun DetailGameCardPrices(data: GameCardData) {
    val theme = LocalHeyboxTheme.current
    val current = if (data.free) "免费" else data.currentPrice.trim()
    val original = data.originalPrice.trim().takeIf {
        it.isNotBlank() && it != data.currentPrice.trim()
    }.orEmpty()
    val discount = data.discount.takeIf { it.isNotBlank() && it != "0" }?.let { "-$it%" }.orEmpty()
    if (current.isBlank() && original.isBlank() && discount.isBlank()) return
    // Keep unqualified server amounts unqualified: the model does not retain currency metadata.
    FlowRow(Modifier.fillMaxWidth().testTag("detail-game-prices-${data.appId}"),
        horizontalArrangement = Arrangement.spacedBy(watchDp(5)),
        verticalArrangement = Arrangement.spacedBy(watchDp(2))) {
        if (current.isNotBlank()) Text(current, color = theme.text,
            fontSize = watchSp(11.5f), lineHeight = watchSp(16f), letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.testTag("detail-game-current-${data.appId}"))
        if (original.isNotBlank()) Text(original, color = theme.muted,
            fontSize = watchSp(9.5f), lineHeight = watchSp(16f), letterSpacing = 0.sp,
            textDecoration = TextDecoration.LineThrough,
            modifier = Modifier.testTag("detail-game-original-${data.appId}"))
        if (discount.isNotBlank()) Text(discount, color = theme.accent,
            fontSize = watchSp(9.5f), lineHeight = watchSp(16f), letterSpacing = 0.sp,
            modifier = Modifier.testTag("detail-game-discount-${data.appId}"))
    }
}

private data class DetailGameCardState(val data: GameCardData?, val loading: Boolean)

@Composable
private fun rememberDetailGameCard(block: RichContent.Block, client: GameDetailClient?): DetailGameCardState {
    val appId = block.value.trim()
    val embedded = remember(appId, block.gameObject) { GameCardData.fromEmbedded(block.gameObject, appId) }
    // A cover or score alone must not suppress the existing price enrichment endpoint.
    val needsDetails = embedded == null || !embedded.hasVisibleDetails() ||
        (embedded.currentPrice.isBlank() && !embedded.free)
    val shouldLoad = needsDetails && appId.isNotEmpty() && client != null
    var state by remember(appId, block.gameObject, client) {
        mutableStateOf(DetailGameCardState(embedded, shouldLoad))
    }
    DisposableEffect(appId, block.gameObject, client) {
        var active = true
        if (shouldLoad) client?.loadBatch(
            listOf(RichLinkClassifier.GameLinkInfo(appId, "pc", "", "")),
            object : GameDetailClient.BatchCallback {
                override fun onComplete(value: Map<String, GameCardData>) {
                    if (active) state = DetailGameCardState(
                        embedded?.withDetails(value[appId]) ?: value[appId], false)
                }
            })
        onDispose { active = false }
    }
    return state
}
