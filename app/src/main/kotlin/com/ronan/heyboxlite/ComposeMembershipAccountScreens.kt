package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeMembershipRedeemScreen(
    state: ComposeMembershipUiState,
    onBack: () -> Unit,
    onCode: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    WatchPage("\u5151\u6362\u4f1a\u5458", onBack) {
        MembershipInput(state.redeemCode, "\u4f1a\u5458\u5151\u6362\u7801", onCode,
            enabled = !state.requestInFlight, keyboardType = KeyboardType.Ascii,
            placeholder = "\u5151\u6362\u7801")
        CheckinActionButton(if (state.requestInFlight) "\u63d0\u4ea4\u4e2d" else "\u786e\u8ba4\u5151\u6362",
            onSubmit, enabled = !state.requestInFlight && state.redeemCode.isNotBlank(),
            icon = R.drawable.il_ticket)
        MembershipNotice(state.message)
        state.catalog?.let { MembershipOverview(it) }
    }
}

@Composable
internal fun ComposeMembershipPurchasesScreen(
    state: ComposeMembershipUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    WatchPage("\u8d2d\u4e70\u8bb0\u5f55", onBack) {
        if (state.purchasesLoading) MembershipNotice("\u6b63\u5728\u8bfb\u53d6\u8d2d\u4e70\u8bb0\u5f55")
        MembershipNotice(state.message)
        if (state.purchases.isEmpty() && !state.purchasesLoading) {
            WatchEmptyState(if (state.message.isBlank()) "\u6682\u65e0\u8d2d\u4e70\u8bb0\u5f55"
                else "\u8d2d\u4e70\u8bb0\u5f55\u6682\u4e0d\u53ef\u7528")
        }
        state.purchases.forEach { record ->
            CheckinPanel(Modifier.testTag("membership-purchase-" + record.orderId)) {
                Column(Modifier.padding(watchDp(12)),
                    verticalArrangement = Arrangement.spacedBy(watchDp(5))) {
                    val name = record.productName.ifBlank {
                        state.catalog?.products?.firstOrNull { it.sku == record.productSku }?.name
                            ?: record.productSku.ifBlank { "\u5957\u9910\u4fe1\u606f\u5f85\u786e\u8ba4" }
                    }
                    MembershipBodyText(name, fontSize = 13f, weight = FontWeight.Medium)
                    MembershipInfoRow("\u91d1\u989d",
                        membershipMoney(record.payableAmountCents, record.currency))
                    MembershipInfoRow("\u8d2d\u4e70\u65f6\u95f4", membershipDate(record.createdAt))
                    if (record.durationDays > 0) {
                        MembershipBodyText(record.durationDays.toString() + " \u5929", muted = true, fontSize = 10f)
                    }
                    CheckinBadge(membershipOrderLabel(record.status))
                }
            }
        }
        MembershipRetry(onRetry, !state.purchasesLoading && !state.requestInFlight)
    }
}

@Composable
internal fun MembershipInput(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
) {
    val theme = LocalHeyboxTheme.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = theme.text, fontSize = watchSp(13f), letterSpacing = 0.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        cursorBrush = SolidColor(theme.text),
        modifier = Modifier.fillMaxWidth().heightIn(min = watchDp(40))
            .background(theme.panelElevated, RoundedCornerShape(watchDp(8)))
            .padding(horizontal = watchDp(10), vertical = watchDp(10))
            .semantics { contentDescription = label },
        decorationBox = { field ->
            if (value.isEmpty()) Text(placeholder, color = theme.subtle,
                fontSize = watchSp(13f), letterSpacing = 0.sp)
            field()
        },
    )
}
