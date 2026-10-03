package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/** Route rendering is separate from lifecycle, requests and navigation ownership. */
@Composable
internal fun ComposeRouteScreen(
    host: ComposeAppHost,
    route: String,
    services: ComposeServices,
    active: Boolean,
    previewListState: LazyListState? = null,
) {
    val userRoute by host.navigation.userSpaceRoute
    val detailState by host.navigation.detail
    val listState = previewListState ?: host.listState(route)
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
                listState = listState,
                observeLoadMore = active,
                interactive = active,
            )
        }
        "profile" -> {
            host.profileRevision.value
            val profileState by host.profile.state
            ComposeProfileScreen(services, profileState, host::navigate)
        }
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
                listState = listState,
                observeLoadMore = active,
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
            listState = listState,
        )
        "watch_later" -> ComposeWatchLaterScreen(
            entries = host.saved.watchLaterItems.value,
            services = services,
            onOpen = host::openDetail,
            onRemove = host.saved::removeWatchLater,
            onBack = { host.handleBack() },
            listState = listState,
        )
        "reading_history" -> ComposeHistoryScreen(
            items = host.saved.historyItems.value,
            loading = host.saved.historyLoading.value,
            services = services,
            onOpen = host::openDetail,
            onBack = { host.handleBack() },
            listState = listState,
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
