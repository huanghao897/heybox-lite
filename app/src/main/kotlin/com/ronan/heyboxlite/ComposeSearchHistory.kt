package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign

@Composable
internal fun ComposeSearchHistory(
    history: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    listState: LazyListState,
) {
    val theme = LocalHeyboxTheme.current
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = watchListEndPadding()),
        verticalArrangement = Arrangement.spacedBy(watchDp(5))) {
        item(key = "search-history-header") {
            Row(Modifier.fillMaxWidth().padding(top = watchDp(8)),
                verticalAlignment = Alignment.CenterVertically) {
                Text("最近搜索", Modifier.weight(1f), color = theme.muted,
                    fontSize = watchSp(11f), fontWeight = FontWeight.Medium)
                IconButton(onClick = onClear, modifier = Modifier.size(watchDp(30))) {
                    Icon(painterResource(R.drawable.ic_close), "清空搜索历史",
                        tint = theme.muted, modifier = Modifier.size(watchDp(15)))
                }
            }
        }
        items(history.take(4), key = { "search-history-$it" }) { keyword ->
            Text(keyword, color = theme.text, fontSize = watchSp(12f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (theme.roundScreen) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().heightIn(min = watchDp(34))
                    .clip(RoundedCornerShape(watchDp(8))).background(theme.panel)
                    .clickable { onSelect(keyword) }
                    .padding(horizontal = watchDp(10), vertical = watchDp(8)))
        }
    }
}
