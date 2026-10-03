package com.ronan.heyboxlite

import android.app.Activity
import android.os.Handler
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Owns the Compose surface mounted above the legacy shell.  It deliberately
 * keeps data and side effects in the existing Java services and only owns
 * route state plus screen state required for progressive rendering.
 */
internal class ComposeAppHost(
    activity: Activity,
    parent: FrameLayout,
    session: SessionStore,
    api: ApiClient,
    cache: LocalCache,
    handler: Handler,
    reading: ReadingTimeTracker,
    checkin: CheckinCenterCoordinator?,
    tokens: ThemeTokens,
    roundScreen: Boolean,
    uiScale: Float,
    textScale: Float,
    callbacks: ComposeAppCallbacks,
    toast: ComposeToast,
) {
    private val routeState = mutableStateOf("feed")
    private val userSpaceRoute = mutableStateOf("")
    private val userSpaceReturnRoute = mutableStateOf("profile")
    private val servicesState = mutableStateOf(
        ComposeServices(
            activity, session, api, GameDetailClient(api), cache, handler, reading, checkin,
            composeThemeState(tokens, uiScale, textScale, roundScreen), toast,
        ),
    )
    private val feed = ComposeFeedController(session, api, cache, toast::show)
    private val search = ComposeSearchController(session, api, cache, handler)
    private val saved = ComposeSavedController(servicesState.value)
    private val readingLoader = ReadingCenterLoader(activity, cache)
    private val readingState = mutableStateOf<ComposeReadingCenterState?>(null)
    private val readingLoading = mutableStateOf(false)
    private var readingStarted = false
    private val detail = mutableStateOf<ComposeDetailState?>(null)
    private val detailReturnRoute = mutableStateOf("feed")
    private val savedDetailForUserSpace = mutableStateOf<ComposeDetailState?>(null)
    private var savedDetailParentRoute = "feed"
    private val callbacks = callbacks
    private val view = ComposeView(activity)
    private val feedRevision = mutableStateOf(0)
    private var feedStarted = false
    private var mounted = true

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            ComposeAppRoot(this)
        }
        parent.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    fun setRoute(route: String) {
        val raw = route.ifBlank { "feed" }
        val normalized = raw.substringBefore('?').ifBlank { "feed" }
        if (normalized == "user_space") userSpaceRoute.value = raw
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        routeState.value = normalized
        when (normalized) {
            "favorites" -> saved.openFavorites()
            "watch_later" -> saved.refreshWatchLater()
            "reading_history" -> saved.openHistory()
            "reading_center" -> loadReadingCenter()
        }
    }

    fun currentRoute(): String = routeState.value

    fun isRoute(route: String): Boolean = routeState.value == route

    fun isMounted(): Boolean = mounted && view.parent != null

    fun updateTheme(tokens: ThemeTokens, roundScreen: Boolean, uiScale: Float, textScale: Float) {
        val current = servicesState.value
        servicesState.value = current.copy(
            theme = composeThemeState(tokens, uiScale, textScale, roundScreen),
        )
    }

    fun invalidateFeed() {
        feedRevision.value++
    }

    fun scrollRotary(distance: Int): Boolean {
        if (!mounted || distance == 0) return false
        val current = servicesState.value
        val serial = (current.theme.rotaryRequest?.serial ?: 0) + 1
        servicesState.value = current.copy(
            theme = current.theme.copy(
                rotaryRequest = ComposeRotaryRequest(serial, distance),
            ),
        )
        return true
    }

    fun close() {
        mounted = false
        savedDetailForUserSpace.value = null
        feed.close()
        search.close()
        saved.close()
        readingLoader.close()
        view.disposeComposition()
    }

    private fun loadReadingCenter() {
        if (readingLoading.value && readingStarted) return
        readingStarted = true
        readingLoading.value = true
        readingLoader.load(object : ReadingCenterLoader.Callback {
            override fun onLoaded(snapshot: ReadingCenterLoader.Snapshot) {
                val services = servicesState.value
                val recent = snapshot.recent
                val hasPosition = recent != null && services.cache.scroll(recent.id) > 0
                val stats = services.reading.stats()
                val today = Format.readingDuration(stats.todayArticleMs + stats.todayPostMs)
                val total = Format.readingDuration(stats.totalArticleMs + stats.totalPostMs)
                readingState.value = ComposeReadingCenterState.from(
                    snapshot,
                    "$today · 累计 $total",
                    hasPosition,
                )
                readingLoading.value = false
            }

            override fun onError() {
                readingLoading.value = false
            }
        })
    }

    fun showDetailLoading(item: FeedItem) {
        if (routeState.value != "detail" || detail.value == null) {
            detailReturnRoute.value = routeState.value.takeUnless { it == "detail" } ?: "feed"
        }
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        detail.value = ComposeDetailState(item, emptyList(), item.videos, emptyList(), true)
        routeState.value = "detail"
    }

    fun showDetailResult(result: DetailPageAssembler.Result, fallback: FeedItem) {
        val comments = ArrayList<JSONObject>()
        val source = result.comments
        if (source != null) {
            for (index in 0 until source.length()) {
                source.optJSONObject(index)?.let { comments.add(it) }
            }
        }
        detail.value = ComposeDetailState(
            fallback,
            result.contentBlocks ?: emptyList(),
            result.videos ?: fallback.videos,
            comments,
            false,
        )
    }

    fun appendDetailReplies(rootId: String, replies: List<JSONObject>) {
        if (replies.isEmpty()) return
        val current = detail.value ?: return
        val updated = current.comments.map { group ->
            val root = group.optJSONArray("comment")?.optJSONObject(0) ?: group
            if (CommentData.commentId(root) != rootId) return@map group
            val array = group.optJSONArray("comment") ?: JSONArray().also { group.put("comment", it) }
            val known = HashSet<String>()
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { known.add(CommentData.commentId(it)) }
            }
            replies.forEach { reply ->
                val id = CommentData.commentId(reply)
                if (id.isEmpty() || known.add(id)) array.put(reply)
            }
            group
        }
        detail.value = current.copy(comments = updated)
    }

    fun handleBack(): Boolean {
        val current = routeState.value
        if (current == "feed") return false
        if (current == "detail") {
            detail.value = null
            callbacks.backTo(detailReturnRoute.value)
            routeState.value = detailReturnRoute.value
            return true
        }
        val target = when {
            current == "profile" -> "feed"
            current == "search" -> "feed"
            current == "favorites" -> "profile"
            current == "leaderboard" -> "profile"
            current == "watch_later" || current == "reading_history" -> "reading_center"
            current == "reading_stats" -> "reading_center"
            current == "login" -> "profile"
            current == "user_space" -> userSpaceReturnRoute.value.ifBlank { "profile" }
            current.startsWith("settings") || current in setOf(
                "display_settings", "display_preview", "startup_settings",
                "app_settings", "video_settings", "splash_preview", "about",
                "announcement_board", "feedback_group",
            ) -> if (current == "settings_home") "profile" else "settings_home"
            else -> "profile"
        }
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        if (current == "user_space" && target == "detail") {
            detail.value = savedDetailForUserSpace.value
            detailReturnRoute.value = savedDetailParentRoute
            savedDetailForUserSpace.value = null
        }
        routeState.value = target
        callbacks.backTo(target)
        return true
    }

    private fun navigate(route: String) {
        val key = route.substringBefore('?')
        when (key) {
            "cache_prune", "cache_clear", "diagnostics_export", "diagnostics_upload",
            "crash_test", "logout", "open_url", "update_download" -> {
                callbacks.runSettingsAction(route)
            }
            else -> {
                if (key == "user_space") {
                    userSpaceReturnRoute.value = routeState.value
                    if (routeState.value == "detail" && detail.value != null) {
                        savedDetailForUserSpace.value = detail.value
                        savedDetailParentRoute = detailReturnRoute.value
                    }
                }
                setRoute(route)
                callbacks.navigate(route)
            }
        }
    }

    private fun openDetail(item: FeedItem) {
        detailReturnRoute.value = routeState.value
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        detail.value = ComposeDetailState(item, emptyList(), item.videos, emptyList(), true)
        routeState.value = "detail"
        callbacks.openDetail(item)
    }

    private fun detailAction(action: ComposeDetailAction) {
        when (action) {
            is ComposeDetailAction.LikePost -> callbacks.feedAction(action.item, ComposeAppCallbacks.ACTION_LIKE)
            is ComposeDetailAction.WriteComment -> callbacks.writeComment(action.item)
            is ComposeDetailAction.LikeComment -> callbacks.likeComment(action.comment)
            is ComposeDetailAction.Reply -> callbacks.replyComment(action.comment)
            is ComposeDetailAction.LoadReplies -> callbacks.loadReplies(action.root)
        }
    }

    @Composable
    private fun ComposeAppRoot(host: ComposeAppHost) {
        val services by host.servicesState
        val route by host.routeState
        val userRoute by host.userSpaceRoute
        val detailState by host.detail
        LaunchedEffect(route) {
            if (route == "feed" && !host.feedStarted) {
                host.feedStarted = true
                host.feed.restoreCache()
                host.feed.refresh()
            }
        }
        HeyboxComposeTheme(services.theme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(
                        route,
                        services.theme.roundScreen,
                        services.theme.uiScale,
                    ) {
                        val edgePx = 28.dp.toPx() * services.theme.uiScale
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val edgeArmed = !down.isConsumed && ComposeSwipePolicy.canArm(
                                route, down.position.x, edgePx,
                            )
                            var totalX = 0f
                            var totalY = 0f
                            var decided = false
                            var accepted = false
                            val touchSlop = viewConfiguration.touchSlop
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                if (!decided && change.isConsumed) break
                                val delta = change.positionChangeIgnoreConsumed()
                                totalX += delta.x
                                totalY += delta.y
                                if (!decided && maxOf(abs(totalX), abs(totalY)) >= touchSlop) {
                                    decided = true
                                    accepted = edgeArmed && totalX > 0f
                                            && abs(totalX) > abs(totalY)
                                }
                                if (accepted) change.consume()
                            }
                            val threshold = 44f * services.theme.uiScale
                            if (accepted && totalX > threshold) {
                                if (route == "feed") {
                                    if (services.session.homeSwipeExit()) host.callbacks.back()
                                } else if (services.session.shellBackSwipe()) {
                                    host.handleBack()
                                }
                            }
                        }
                    },
            ) {
                when (route) {
                "feed" -> {
                    host.feedRevision.value
                    val items by host.feed.items
                    val loading by host.feed.loading
                    val refreshing by host.feed.refreshing
                    val noMore by host.feed.noMore
                    ComposeFeedScreen(
                        items, loading, refreshing, noMore, services,
                        onOpen = host::openDetail,
                        onRefresh = host.feed::refresh,
                        onLoadMore = host.feed::loadMore,
                        onSearch = { host.navigate("search") },
                        onAction = { item, action ->
                            val code = when (action) {
                                FeedAction.LIKE -> ComposeAppCallbacks.ACTION_LIKE
                                FeedAction.FAVORITE -> ComposeAppCallbacks.ACTION_FAVORITE
                                FeedAction.CACHE -> ComposeAppCallbacks.ACTION_CACHE
                                FeedAction.FOLLOW -> ComposeAppCallbacks.ACTION_FOLLOW
                                FeedAction.COMMENT -> ComposeAppCallbacks.ACTION_COMMENT
                            }
                            host.callbacks.feedAction(item, code)
                        },
                    )
                }
                "profile" -> ComposeProfileScreen(services, host::navigate)
                "login" -> ComposeLoginScreen(
                    services = services,
                    onBack = { host.handleBack() },
                    onGuest = { host.setRoute("feed"); host.callbacks.navigate("feed") },
                    onLoggedIn = {
                        host.callbacks.loginCompleted()
                        host.setRoute("feed")
                        host.callbacks.navigate("feed")
                    },
                )
                "user_space" -> ComposeUserSpaceScreen(
                    route = composeUserSpaceRoute(userRoute),
                    services = services,
                    onBack = { host.handleBack() },
                    onOpen = host::openDetail,
                )
                "search" -> {
                    val items by host.search.items
                    val loading by host.search.loading
                    val noMore by host.search.noMore
                    val query by host.search.query
                    ComposeSearchScreen(
                        query, items, loading, noMore, services,
                        onQueryChange = host.search::setQuery,
                        onOpen = host::openDetail,
                        onBack = { host.handleBack() },
                        onLoadMore = host.search::loadMore,
                    )
                }
                "detail" -> {
                    val state = detailState
                    if (state == null) {
                        WatchEmptyState("正在打开…", Modifier.background(services.theme.background))
                    } else {
                        ComposeDetailScreen(
                            item = state.item,
                            content = state.content,
                            videos = state.videos,
                            comments = state.comments,
                            loading = state.loading,
                            services = services,
                            onBack = { host.handleBack() },
                            onOpenImage = { host.callbacks.requestImage(it) },
                            onOpenVideo = { host.callbacks.requestVideo(it) },
                            onOpenUser = { id, name, avatar ->
                                host.navigate("user_space?user=${android.net.Uri.encode(id)}&name=${android.net.Uri.encode(name)}&avatar=${android.net.Uri.encode(avatar)}")
                            },
                            onCommentAction = host::detailAction,
                        )
                    }
                }
                "settings_home", "display_settings", "display_preview", "startup_settings",
                "app_settings", "video_settings", "splash_preview", "about",
                "announcement_board", "feedback_group" -> ComposeSettingsScreen(
                    route, services, host::navigate, { host.handleBack() },
                    host.callbacks::settingsChanged,
                )
                "checkin_center" -> ComposeCheckinScreen(
                    services, host::navigate, { host.handleBack() },
                )
                "reading_center" -> ComposeReadingCenterScreen(
                    state = host.readingState.value,
                    loading = host.readingLoading.value,
                    services = services,
                    onOpen = host::openDetail,
                    onReadingStats = { host.navigate("reading_stats") },
                    onWatchLater = { host.navigate("watch_later") },
                    onHistory = { host.navigate("reading_history") },
                    onBack = { host.handleBack() },
                )
                "favorites" -> ComposeFavoritesScreen(
                    items = host.saved.favoriteItems.value,
                    folders = host.saved.favoriteFolders.value,
                    selectedTab = host.saved.favoriteTab.value,
                    loading = host.saved.favoriteLoading.value,
                    services = services,
                    onTabChange = host.saved::selectFavoriteTab,
                    onOpen = host::openDetail,
                    onOpenFolder = host.saved::openFolder,
                    onAction = { item, action ->
                        val code = when (action) {
                            FeedAction.LIKE -> ComposeAppCallbacks.ACTION_LIKE
                            FeedAction.FAVORITE -> ComposeAppCallbacks.ACTION_FAVORITE
                            FeedAction.CACHE -> ComposeAppCallbacks.ACTION_CACHE
                            FeedAction.FOLLOW -> ComposeAppCallbacks.ACTION_FOLLOW
                            FeedAction.COMMENT -> ComposeAppCallbacks.ACTION_COMMENT
                        }
                        host.callbacks.feedAction(item, code)
                    },
                    onBack = { host.handleBack() },
                )
                "watch_later" -> ComposeWatchLaterScreen(
                    entries = host.saved.watchLaterItems.value,
                    services = services,
                    onOpen = host::openDetail,
                    onRemove = host.saved::removeWatchLater,
                    onBack = { host.handleBack() },
                )
                "reading_history" -> ComposeHistoryScreen(
                    items = host.saved.historyItems.value,
                    loading = host.saved.historyLoading.value,
                    services = services,
                    onOpen = host::openDetail,
                    onBack = { host.handleBack() },
                )
                "reading_stats" -> ComposeReadingStatsScreen(
                    services = services,
                    onBack = { host.handleBack() },
                )
                "leaderboard" -> ComposeCheckinLeaderboardScreen(
                    services = services,
                    onBack = { host.handleBack() },
                )
                    else -> ComposeFallbackScreen(route, services, { host.handleBack() })
                }
            }
        }
    }

    private data class ComposeDetailState(
        val item: FeedItem,
        val content: List<RichContent.Block>,
        val videos: List<VideoData>,
        val comments: List<JSONObject>,
        val loading: Boolean,
    )
}
