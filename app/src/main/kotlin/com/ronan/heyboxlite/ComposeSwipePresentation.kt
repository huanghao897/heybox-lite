package com.ronan.heyboxlite

/** Geometry shared by interactive dragging, settling and route handoff. */
internal object ComposeSwipePresentation {
    private val liveRoutes = setOf(
        "feed", "profile", "search", "favorites", "favorite_folder", "watch_later", "reading_history",
        "reading_center", "reading_stats", "settings_home", "display_settings",
        "startup_settings", "app_settings", "video_settings", "about",
    )

    @JvmStatic
    fun isLiveRoute(route: String?): Boolean =
        route?.substringBefore('?') in liveRoutes || ComposeCheckinNavigation.page(route.orEmpty()) != null

    @JvmStatic
    fun routes(current: String, target: String?): List<String> {
        val page = target?.substringBefore('?')
        return if (page != current && isLiveRoute(page)) listOf(current, page!!) else listOf(current)
    }

    @JvmStatic
    fun translation(page: String, target: String?, direction: Int, drag: Float, width: Float): Float {
        if (target == null) return 0f
        val offset = drag.coerceIn(-width, width)
        return if (page.substringBefore('?') == target.substringBefore('?')) {
            offset - direction * width
        } else offset
    }
}
