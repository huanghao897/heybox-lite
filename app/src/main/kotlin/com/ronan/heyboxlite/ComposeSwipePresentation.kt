package com.ronan.heyboxlite

/** Geometry shared by interactive dragging, settling and route handoff. */
internal object ComposeSwipePresentation {
    private val liveRoutes = setOf(
        "feed", "profile", "search", "favorites", "watch_later", "reading_history",
        "reading_center", "reading_stats", "settings_home", "display_settings",
        "startup_settings", "app_settings", "video_settings", "about",
    )

    @JvmStatic
    fun isLiveRoute(route: String?): Boolean =
        route?.substringBefore('?') in liveRoutes

    @JvmStatic
    fun routes(current: String, target: String?): List<String> =
        if (target != current && isLiveRoute(target)) listOf(current, target!!) else listOf(current)

    @JvmStatic
    fun translation(page: String, target: String?, direction: Int, drag: Float, width: Float): Float {
        if (target == null) return 0f
        val offset = drag.coerceIn(-width, width)
        return if (page == target) offset - direction * width else offset
    }
}
