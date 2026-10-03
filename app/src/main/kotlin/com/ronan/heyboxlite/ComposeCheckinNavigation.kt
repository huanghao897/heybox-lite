package com.ronan.heyboxlite

/** Internal pages use the same live swipe host as the rest of the application. */
internal object ComposeCheckinNavigation {
    fun key(page: ComposeCheckinRoute): String = if (page == ComposeCheckinRoute.CENTER) "checkin_center"
        else "checkin_page_${page.name.lowercase()}"

    fun page(key: String): ComposeCheckinRoute? = ComposeCheckinRoute.entries.firstOrNull { key(it) == key }

    fun parent(page: ComposeCheckinRoute): ComposeCheckinRoute? = when (page) {
        ComposeCheckinRoute.CENTER -> null
        ComposeCheckinRoute.CHECKOUT, ComposeCheckinRoute.REDEEM, ComposeCheckinRoute.PURCHASES ->
            ComposeCheckinRoute.MEMBERSHIP
        ComposeCheckinRoute.HISTORY_DETAIL -> ComposeCheckinRoute.HISTORY
        else -> ComposeCheckinRoute.CENTER
    }
}
