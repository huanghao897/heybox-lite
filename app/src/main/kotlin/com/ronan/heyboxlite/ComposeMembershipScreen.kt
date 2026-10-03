package com.ronan.heyboxlite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeMembershipScreen(
    state: ComposeMembershipUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onCheckout: () -> Unit,
    onRedeem: () -> Unit,
    onPurchases: () -> Unit,
    onRetry: () -> Unit,
    onAmount: (String) -> Unit = {},
) {
    val catalog = state.catalog
    val busy = state.requestInFlight || state.catalogLoading
    WatchPage("\u4f1a\u5458\u670d\u52a1", onBack) {
        if (catalog == null) {
            WatchEmptyState(if (state.catalogLoading) "\u6b63\u5728\u8bfb\u53d6\u4f1a\u5458\u4fe1\u606f"
                else "\u4f1a\u5458\u4fe1\u606f\u6682\u4e0d\u53ef\u7528")
        } else {
            MembershipOverview(catalog)
            if (state.catalogLoading) MembershipNotice("\u6b63\u5728\u66f4\u65b0\u4f1a\u5458\u4fe1\u606f")
            if (catalog.products.isNotEmpty()) {
                WatchSectionTitle("\u9009\u62e9\u5957\u9910")
                MembershipProducts(catalog.products, state.selectedSku, !busy, onSelect)
            } else if (!catalog.voluntarySponsorship) {
                MembershipNotice("\u6682\u65e0\u53ef\u7528\u5957\u9910")
            }
            if (state.variableSponsorship()) {
                MembershipInput(state.amount, "\u8d5e\u52a9\u91d1\u989d", onAmount,
                    enabled = !busy, keyboardType = KeyboardType.Decimal,
                    placeholder = if (catalog.plan.currency == "CNY") "\u91d1\u989d\uff08\u5143\uff09"
                        else catalog.plan.currency)
                MembershipBodyText(
                    membershipMoney(catalog.plan.minimumAmountCents, catalog.plan.currency) + " - " +
                        membershipMoney(catalog.plan.maximumAmountCents, catalog.plan.currency),
                    muted = true, fontSize = 10f,
                )
            } else if (catalog.products.isEmpty() && catalog.voluntarySponsorship) {
                MembershipBodyText(catalog.plan.name, weight = FontWeight.Medium)
                MembershipBodyText(membershipMoney(catalog.plan.amountCents, catalog.plan.currency))
            }
            if (!catalog.checkoutAvailable) MembershipNotice("\u5f53\u524d\u65e0\u6cd5\u521b\u5efa\u8ba2\u5355")
            val amountCents = SponsorshipAmount.parseCents(state.amount,
                catalog.plan.minimumAmountCents, catalog.plan.maximumAmountCents)
            val canCheckout = catalog.checkoutAvailable && !busy &&
                (if (state.variableSponsorship()) SponsorshipAmount.validCents(amountCents)
                    else state.selectedProduct()?.active == true)
            CheckinActionButton(
                text = when {
                    state.requestInFlight -> "\u5904\u7406\u4e2d"
                    catalog.voluntarySponsorship -> "\u786e\u8ba4\u8d5e\u52a9"
                    catalog.entitled -> "\u7eed\u8d39\u4f1a\u5458"
                    else -> "\u5f00\u901a\u4f1a\u5458"
                },
                onClick = onCheckout,
                enabled = canCheckout,
                icon = R.drawable.il_crown,
            )
        }
        MembershipNotice(state.message)
        if (catalog == null || state.message.isNotBlank() || catalog?.checkoutAvailable == false) {
            MembershipRetry(onRetry, !busy)
        }
        CheckinPanel {
            CheckinMenuRow("\u5151\u6362\u4f1a\u5458", R.drawable.il_ticket, onRedeem,
                enabled = !state.requestInFlight, modifier = Modifier.semantics { role = Role.Button })
            CheckinDivider()
            CheckinMenuRow("\u8d2d\u4e70\u8bb0\u5f55", R.drawable.il_history, onPurchases,
                enabled = !state.requestInFlight, modifier = Modifier.semantics { role = Role.Button })
        }
    }
}

@Composable
internal fun MembershipOverview(catalog: CheckinBilling.Membership) {
    CheckinPanel {
        Column(Modifier.padding(watchDp(12)),
            verticalArrangement = Arrangement.spacedBy(watchDp(5))) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(watchDp(8))) {
                Icon(painterResource(R.drawable.il_crown), "\u4f1a\u5458\u6743\u76ca",
                    tint = LocalHeyboxTheme.current.text, modifier = Modifier.size(watchDp(23)))
                MembershipBodyText(membershipTitle(catalog), Modifier.weight(1f),
                    fontSize = 14f, weight = FontWeight.SemiBold)
            }
            MembershipBodyText(
                when {
                    catalog.admin && !catalog.entitled -> "\u6743\u76ca\u6682\u672a\u786e\u8ba4"
                    !catalog.required && catalog.mode == "free" -> "\u5f53\u524d\u65e0\u9700\u8d2d\u4e70\u4f1a\u5458"
                    catalog.entitled && catalog.expiresAt.isNotBlank() ->
                        "\u5230\u671f\u65f6\u95f4 " + membershipDate(catalog.expiresAt)
                    !catalog.entitled && catalog.expiresAt.isNotBlank() ->
                        "\u5df2\u5230\u671f " + membershipDate(catalog.expiresAt)
                    catalog.entitled -> "\u6743\u76ca\u6709\u6548"
                    else -> "\u6743\u76ca\u5c1a\u672a\u5f00\u901a"
                },
                muted = true, fontSize = 11f,
            )
            if (catalog.voluntarySponsorship) {
                MembershipBodyText("\u81ea\u613f\u8d5e\u52a9\uff0c\u4e0d\u5f71\u54cd\u7b7e\u5230\u6743\u76ca",
                    muted = true, fontSize = 10f)
            }
        }
    }
}

@Composable
private fun MembershipProducts(
    products: List<CheckinBilling.Product>,
    selectedSku: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val priceStyle = TextStyle(fontSize = watchSp(16f), fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp)
    val priceWidth = products.maxOf { product ->
        with(density) {
            measurer.measure(AnnotatedString(membershipMoney(product.amountCents, product.currency)),
                style = priceStyle).size.width.toDp()
        }
    }
    val minimum = maxOf((96 * theme.textScale * density.fontScale).dp,
        priceWidth + watchDp(20))
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth >= minimum * 2 + watchDp(8)) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(watchDp(8))) {
            products.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(watchDp(8))) {
                    row.forEach { product ->
                        MembershipProduct(product, product.sku == selectedSku,
                            enabled && product.active, { onSelect(product.sku) },
                            Modifier.weight(1f).fillMaxHeight())
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MembershipProduct(
    product: CheckinBilling.Product,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val theme = LocalHeyboxTheme.current
    val shape = RoundedCornerShape(watchDp(8))
    Column(modifier.testTag("membership-product-" + product.sku)
        .clip(shape).background(if (selected) theme.panelElevated else theme.panel)
        .border(BorderStroke(if (selected) 1.dp else 0.5.dp,
            if (selected) theme.accent else theme.hairline), shape)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton,
            onClick = onSelect)
        .heightIn(min = watchDp(92)).padding(watchDp(10)),
        verticalArrangement = Arrangement.spacedBy(watchDp(5))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MembershipBodyText(if (product.durationDays > 0) product.durationDays.toString() + " \u5929"
                else product.name, Modifier.weight(1f), muted = true, fontSize = 11f)
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), "\u5df2\u9009\u62e9",
                    tint = theme.accent, modifier = Modifier.size(watchDp(16)))
            } else Spacer(Modifier.size(watchDp(16)))
        }
        MembershipBodyText(membershipMoney(product.amountCents, product.currency),
            fontSize = 16f, weight = FontWeight.SemiBold)
        MembershipBodyText(product.name, fontSize = 11f)
        if (!product.active) MembershipBodyText("\u6682\u4e0d\u53ef\u552e", muted = true, fontSize = 10f)
    }
}

@Composable
internal fun MembershipBodyText(
    text: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    fontSize: Float = 12f,
    weight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
) {
    Text(text, modifier = modifier,
        color = if (muted) LocalHeyboxTheme.current.muted else LocalHeyboxTheme.current.text,
        fontSize = watchSp(fontSize), fontWeight = weight, letterSpacing = 0.sp, textAlign = textAlign)
}

@Composable
internal fun MembershipNotice(message: String) {
    if (message.isNotBlank()) MembershipBodyText(message,
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        muted = true, fontSize = 11f)
}

@Composable
internal fun MembershipRetry(onRetry: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onRetry, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = watchDp(36))) {
        Icon(painterResource(R.drawable.il_refresh), "\u91cd\u8bd5",
            tint = if (enabled) LocalHeyboxTheme.current.text else LocalHeyboxTheme.current.subtle,
            modifier = Modifier.size(watchDp(15)))
        Spacer(Modifier.width(watchDp(6)))
        MembershipBodyText("\u91cd\u8bd5", fontSize = 12f)
    }
}
