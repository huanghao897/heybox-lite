package com.ronan.heyboxlite

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

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
    private val navigation = ComposeNavigationState()
    private val servicesState = mutableStateOf(
        ComposeServices(
            activity, session, api, GameDetailClient(api), cache, handler, reading, checkin,
            composeThemeState(tokens, uiScale, textScale, roundScreen), toast,
        ),
    )
    internal val feed = ComposeFeedController(session, api, cache, toast::show)
    private val search = ComposeSearchController(session, api, cache, handler)
    private val saved = ComposeSavedController(servicesState.value)
    private val readingLoader = ReadingCenterLoader(activity, cache)
    private val readingState = mutableStateOf<ComposeReadingCenterState?>(null)
    private val readingLoading = mutableStateOf(false)
    private var readingStarted = false
    private val transitionSnapshots = TransitionSnapshotStore()
    internal val callbacks = callbacks
    private val view = ComposeView(activity)
    internal val feedRevision = mutableStateOf(0)
    private var feedStarted = false
    private var mounted = true
    private var suppressNextTransitionSnapshot = false

    init {
        // The content layer moves during a back gesture. Keep the host view
        // painted so the exposed strip never reveals the hidden legacy layer.
        view.setBackgroundColor(tokens.background)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            ComposeAppRoot(this)
        }
        parent.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    fun setRoute(route: String) {
        val normalized = route.ifBlank { "feed" }.substringBefore('?').ifBlank { "feed" }
        captureCurrentTransitionSnapshot()
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        navigation.setRoute(route)
        when (normalized) {
            "favorites" -> saved.openFavorites()
            "watch_later" -> saved.refreshWatchLater()
            "reading_history" -> saved.openHistory()
            "reading_center" -> loadReadingCenter()
        }
    }

    /**
     * Entry point for native callers opening a Compose user page. Unlike a
     * plain setRoute call, this records the actual current route so the page
     * can return to search, reading center or the preserved detail screen.
     */
    fun showExternalUserSpace(route: String) {
        captureCurrentTransitionSnapshot()
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        navigation.showExternalUserSpace(route)
    }

    fun currentRoute(): String = navigation.route.value

    fun currentRouteSpec(): String = navigation.currentRouteSpec()

    fun detailReturnRoute(): String = navigation.detailReturnRoute.value

    fun detailReturnRouteSpec(): String = navigation.detailReturnRouteSpec()

    fun isRoute(route: String): Boolean = navigation.route.value == route

    fun isMounted(): Boolean = mounted && view.parent != null

    /**
     * Returns the route that should be visible underneath a horizontal drag.
     * An empty target means the home-screen exit action, not another page.
     */
    fun composeSwipeTarget(route: String, direction: Int): String? {
        if (direction < 0 && route == "feed") return "profile"
        if (direction > 0 && route == "feed") {
            return if (servicesState.value.session.homeSwipeExit()) "" else null
        }
        if (direction > 0 && servicesState.value.session.shellBackSwipe()) {
            val target = navigation.backTarget()
            return target.takeUnless { it.isEmpty() || it == route }
        }
        return null
    }

    fun composeSwipeEnabled(route: String): Boolean {
        return composeSwipeTarget(route, -1) != null
                || composeSwipeTarget(route, 1) != null
    }

    fun completeComposeSwipe(target: String) {
        if (!mounted) return
        if (target.isEmpty()) {
            callbacks.back()
            return
        }
        val current = navigation.route.value
        if (target == navigation.backTarget() && current != "feed") {
            suppressNextTransitionSnapshot = true
            handleBack()
        } else {
            navigate(target)
        }
    }

    fun transitionPreview(routeSpec: String): Bitmap? {
        val raw = routeSpec.ifBlank { "feed" }
        return transitionSnapshots.get(raw)
            ?: transitionSnapshots.get(raw.substringBefore('?'))
    }

    fun contentWidthPx(): Float = view.width.toFloat()

    fun updateTheme(tokens: ThemeTokens, roundScreen: Boolean, uiScale: Float, textScale: Float) {
        view.setBackgroundColor(tokens.background)
        val current = servicesState.value
        servicesState.value = current.copy(
            theme = composeThemeState(tokens, uiScale, textScale, roundScreen),
        )
    }

    private fun captureCurrentTransitionSnapshot() {
        if (suppressNextTransitionSnapshot) {
            suppressNextTransitionSnapshot = false
            return
        }
        if (!mounted || !view.isShown) return
        val routeSpec = navigation.currentRouteSpec().ifBlank { "feed" }
        transitionSnapshots.capture(
            routeSpec,
            8,
            view,
            servicesState.value.theme.background.toArgb(),
            servicesState.value.cache,
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
        navigation.clearSavedState()
        feed.close()
        search.close()
        saved.close()
        readingLoader.close()
        transitionSnapshots.releaseAll()
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
        captureCurrentTransitionSnapshot()
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        navigation.showDetailLoading(item)
    }

    fun showDetailResult(result: DetailPageAssembler.Result, fallback: FeedItem) {
        navigation.showDetailResult(result, fallback)
    }

    fun appendDetailReplies(rootId: String, replies: List<org.json.JSONObject>) {
        navigation.appendDetailReplies(rootId, replies)
    }

    fun handleBack(): Boolean {
        val current = navigation.route.value
        if (current == "feed") return false
        if (current == "detail") {
            val targetSpec = detailReturnRouteSpec()
            navigation.clearDetail()
            callbacks.backTo(targetSpec)
            return true
        }
        val target = navigation.backTarget()
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        navigation.restoreSavedDetailIfNeeded(target)
        callbacks.backTo(target)
        return true
    }

    internal fun navigate(route: String) {
        val key = route.substringBefore('?')
        when (key) {
            "cache_prune", "cache_clear", "diagnostics_export", "diagnostics_upload",
            "crash_test", "logout", "open_url", "update_download" -> {
                callbacks.runSettingsAction(route)
            }
            else -> {
                captureCurrentTransitionSnapshot()
                servicesState.value = servicesState.value.copy(
                    theme = servicesState.value.theme.copy(rotaryRequest = null),
                )
                navigation.navigate(route)
                when (key) {
                    "favorites" -> saved.openFavorites()
                    "watch_later" -> saved.refreshWatchLater()
                    "reading_history" -> saved.openHistory()
                    "reading_center" -> loadReadingCenter()
                }
                callbacks.navigate(route)
            }
        }
    }

    internal fun openDetail(item: FeedItem) {
        servicesState.value = servicesState.value.copy(
            theme = servicesState.value.theme.copy(rotaryRequest = null),
        )
        navigation.showDetailLoading(item)
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
        val route by host.navigation.route
        val userRoute by host.navigation.userSpaceRoute
        val detailState by host.navigation.detail
        LaunchedEffect(route) {
            if (route == "feed" && !host.feedStarted) {
                host.feedStarted = true
                host.feed.restoreCache()
                host.feed.refresh()
            }
        }
        HeyboxComposeTheme(services.theme) {
            ComposeSwipeContainer(host, route, services) {
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

}
