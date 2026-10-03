package com.ronan.heyboxlite

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** State owned by the Compose navigation shell, independent of screen rendering. */
internal data class ComposeDetailState(
    val item: FeedItem,
    val content: List<RichContent.Block>,
    val videos: List<VideoData>,
    val comments: List<JSONObject>,
    val loading: Boolean,
    val link: JSONObject? = null,
)

internal class ComposeNavigationState {
    val route: MutableState<String> = mutableStateOf("feed")
    private val currentRouteSpecState: MutableState<String> = mutableStateOf("feed")
    val userSpaceRoute: MutableState<String> = mutableStateOf("")
    val userSpaceReturnRoute: MutableState<String> = mutableStateOf("profile")
    val detail: MutableState<ComposeDetailState?> = mutableStateOf(null)
    val detailReturnRoute: MutableState<String> = mutableStateOf("feed")
    val savedDetailForUserSpace: MutableState<ComposeDetailState?> = mutableStateOf(null)
    var savedDetailParentRoute: String = "feed"

    fun setRoute(routeSpec: String) {
        val raw = routeSpec.ifBlank { "feed" }
        val normalized = raw.substringBefore('?').ifBlank { "feed" }
        if (normalized == "user_space") userSpaceRoute.value = raw
        currentRouteSpecState.value = raw
        route.value = normalized
    }

    fun currentRouteSpec(): String = if (route.value == "user_space") {
        userSpaceRoute.value.ifBlank { route.value }
    } else currentRouteSpecState.value.ifBlank { route.value }

    fun detailReturnRouteSpec(): String = if (detailReturnRoute.value == "user_space") {
        userSpaceRoute.value.ifBlank { detailReturnRoute.value }
    } else detailReturnRoute.value

    fun showExternalUserSpace(routeSpec: String) {
        if (route.value != "user_space") captureUserSpaceParent()
        setRoute(routeSpec)
    }

    fun navigate(routeSpec: String) {
        if (routeSpec.substringBefore('?') == "user_space") captureUserSpaceParent()
        setRoute(routeSpec)
    }

    fun showDetailLoading(item: FeedItem) {
        if (route.value != "detail" || detail.value == null) {
            detailReturnRoute.value = currentRouteSpec().takeUnless {
                it.substringBefore('?') == "detail"
            } ?: "feed"
        }
        detail.value = ComposeDetailState(item, emptyList(), item.videos, emptyList(), true)
        currentRouteSpecState.value = "detail"
        route.value = "detail"
    }

    fun showDetailResult(
        result: DetailPageAssembler.Result,
        fallback: FeedItem,
    ) {
        val comments = ArrayList<JSONObject>()
        result.comments?.let { source ->
            for (index in 0 until source.length()) {
                source.optJSONObject(index)?.let(comments::add)
            }
        }
        detail.value = ComposeDetailState(
            fallback,
            result.contentBlocks ?: emptyList(),
            result.videos ?: fallback.videos,
            comments,
            false,
            result.body?.optJSONObject("result")?.optJSONObject("link"),
        )
    }

    fun appendDetailReplies(rootId: String, replies: List<JSONObject>) {
        if (replies.isEmpty()) return
        val current = detail.value ?: return
        val updated = current.comments.map { group ->
            val root = group.optJSONArray("comment")?.optJSONObject(0) ?: group
            if (CommentData.commentId(root) != rootId) return@map group
            val source = group.optJSONArray("comment")
            val array = JSONArray()
            if (source == null) array.put(root) else {
                for (index in 0 until source.length()) array.put(source.opt(index))
            }
            val known = HashSet<String>()
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { known.add(CommentData.commentId(it)) }
            }
            val previousCount = array.length()
            replies.forEach { reply ->
                val id = CommentData.commentId(reply)
                if (id.isEmpty() || known.add(id)) array.put(reply)
            }
            if (array.length() == previousCount) return@map group
            // A new group/array publishes a real snapshot change while retaining
            // the original root used by the existing comment action controller.
            JSONObject().apply {
                group.keys().forEach { key -> if (key != "comment") put(key, group.opt(key)) }
                put("comment", array)
            }
        }
        detail.value = current.copy(comments = updated)
    }

    fun backTarget(): String {
        return when (val current = route.value) {
            "feed" -> ""
            "profile", "search" -> "feed"
            "favorites", "leaderboard" -> "profile"
            "favorite_folder" -> "favorites"
            "watch_later", "reading_history", "reading_stats" -> "reading_center"
            "login" -> "profile"
            "user_space" -> userSpaceReturnRoute.value.ifBlank { "profile" }
            else -> if (isSettingsRoute(current)) {
                if (current == "settings_home") "profile" else "settings_home"
            } else "profile"
        }
    }

    fun restoreSavedDetailIfNeeded(target: String): Boolean {
        if (route.value != "user_space" || target.substringBefore('?') != "detail") {
            return false
        }
        detail.value = savedDetailForUserSpace.value ?: return false
        detailReturnRoute.value = savedDetailParentRoute
        savedDetailForUserSpace.value = null
        currentRouteSpecState.value = "detail"
        return true
    }

    fun clearDetail() {
        detail.value = null
    }

    fun clearSavedState() {
        savedDetailForUserSpace.value = null
    }

    private fun captureUserSpaceParent() {
        userSpaceReturnRoute.value = currentRouteSpec()
        if (route.value == "detail" && detail.value != null) {
            savedDetailForUserSpace.value = detail.value
            savedDetailParentRoute = detailReturnRouteSpec()
        }
    }

    private fun isSettingsRoute(route: String): Boolean = route.startsWith("settings") || route in setOf(
        "display_settings", "display_preview", "startup_settings", "app_settings",
        "video_settings", "splash_preview", "about", "announcement_board", "feedback_group",
    )
}
