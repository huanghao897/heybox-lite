package com.ronan.heyboxlite

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
    val body: JSONObject? = null,
    val error: String = "",
    val entryKey: Long = 0L,
    val replyStates: Map<String, ComposeReplyLoadState> = emptyMap(),
    val pendingLikes: Set<String> = emptySet(),
    val useImagePager: Boolean = false,
    val likeOverrides: Map<String, ComposeCommentLikeState> = emptyMap(),
    val refreshPending: Boolean = true,
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
    private data class Frame(val route: String, val detail: ComposeDetailState?,
                             val detailParent: String, val userParent: String)
    private val contentHistory = mutableStateListOf<Frame>()
    private var nextEntryKey = 0L

    fun presentationRoute(): String = presentation(currentRouteSpec(), detail.value)

    fun retainedContentRoutes(): Set<String> = buildSet {
        add(presentationRoute())
        contentHistory.forEach { add(presentation(it.route, it.detail)) }
    }

    fun backPresentationRoute(): String = contentHistory.lastOrNull()?.takeIf {
        route.value == "detail" || route.value == "user_space"
    }?.let {
        presentation(it.route, it.detail)
    } ?: backTarget()

    fun detailForPresentation(routeSpec: String): ComposeDetailState? {
        if (routeSpec == "detail" || routeSpec == presentationRoute()) return detail.value
        return contentHistory.lastOrNull { presentation(it.route, it.detail) == routeSpec }?.detail
    }

    fun detailForEntry(entryKey: Long): ComposeDetailState? =
        detail.value?.takeIf { it.entryKey == entryKey }
            ?: contentHistory.lastOrNull { it.detail?.entryKey == entryKey }?.detail

    fun updateDetail(entryKey: Long, update: (ComposeDetailState) -> ComposeDetailState) {
        val current = detail.value
        if (current?.entryKey == entryKey) detail.value = update(current)
        for (index in contentHistory.indices) {
            val frame = contentHistory[index]
            if (frame.detail?.entryKey == entryKey) {
                contentHistory[index] = frame.copy(detail = update(frame.detail))
            }
        }
    }

    private fun presentation(route: String, detail: ComposeDetailState?): String =
        if (route.substringBefore('?') == "detail" && detail != null) "detail?entry=${detail.entryKey}" else route

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

    fun detailReturnRouteSpec(): String = detailReturnRoute.value

    fun showExternalUserSpace(routeSpec: String) {
        if (route.value != "user_space") captureUserSpaceParent()
        setRoute(routeSpec)
    }

    fun navigate(routeSpec: String) {
        if (routeSpec.substringBefore('?') == "user_space") captureUserSpaceParent()
        setRoute(routeSpec)
    }

    fun showDetailLoading(item: FeedItem) {
        val reload = route.value == "detail" && detail.value?.item?.id == item.id
        if (!reload) {
            pushContentParent()
            detailReturnRoute.value = currentRouteSpec().takeUnless {
                it.substringBefore('?') == "detail"
            } ?: "detail"
        }
        detail.value = if (reload) detail.value!!.copy(loading = true, refreshPending = true, error = "") else
            ComposeDetailState(item, emptyList(), item.videos, emptyList(), true, entryKey = ++nextEntryKey)
        currentRouteSpecState.value = "detail"
        route.value = "detail"
    }

    fun showDetailDocument(document: ComposeDetailDocument) {
        val previous = detail.value ?: return
        if (previous.item.id != document.item.id) return
        val preview = document.body.optBoolean("_progressive_preview")
        detail.value = previous.copy(item = document.item, content = document.content,
            videos = document.videos, comments = composeMergeDetailComments(document.comments, previous, preview),
            loading = preview,
            link = document.link, body = document.body, error = "", useImagePager = document.useImagePager)
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

    fun appendDetailReplies(rootId: String, replies: List<JSONObject>, entryKey: Long = detail.value?.entryKey ?: -1) {
        if (replies.isEmpty()) return
        updateDetail(entryKey) { current ->
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
                // Publish a new group while retaining the root used by comment actions.
                JSONObject().apply {
                    group.keys().forEach { key -> if (key != "comment") put(key, group.opt(key)) }
                    put("comment", array)
                }
            }
            current.copy(comments = updated)
        }
    }

    fun backTarget(): String {
        return when (val current = route.value) {
            "detail" -> contentHistory.lastOrNull()?.route ?: detailReturnRouteSpec()
            "feed" -> ""
            "profile", "search" -> "feed"
            "favorites", "leaderboard" -> "profile"
            "favorite_folder" -> "favorites"
            "watch_later", "reading_history", "reading_stats" -> "reading_center"
            "login" -> "profile"
            "user_space" -> contentHistory.lastOrNull()?.route ?: userSpaceReturnRoute.value.ifBlank { "profile" }
            else -> if (isSettingsRoute(current)) {
                if (current == "settings_home") "profile" else "settings_home"
            } else "profile"
        }
    }

    fun restoreSavedDetailIfNeeded(target: String): Boolean {
        if (route.value in setOf("detail", "user_space") && contentHistory.lastOrNull()?.route == target) {
            val parent = contentHistory.removeAt(contentHistory.lastIndex)
            detail.value = parent.detail
            detailReturnRoute.value = parent.detailParent
            userSpaceReturnRoute.value = parent.userParent
            setRoute(parent.route)
            return true
        }
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
        contentHistory.clear()
    }

    private fun captureUserSpaceParent() {
        pushContentParent()
        userSpaceReturnRoute.value = currentRouteSpec()
        if (route.value == "detail" && detail.value != null) {
            savedDetailForUserSpace.value = detail.value
            savedDetailParentRoute = detailReturnRouteSpec()
        }
    }

    private fun pushContentParent() {
        contentHistory.add(Frame(currentRouteSpec(), detail.value, detailReturnRouteSpec(), userSpaceReturnRoute.value))
    }

    private fun isSettingsRoute(route: String): Boolean = route.startsWith("settings") || route in setOf(
        "display_settings", "display_preview", "startup_settings", "app_settings",
        "video_settings", "splash_preview", "about", "announcement_board", "feedback_group",
    )
}
