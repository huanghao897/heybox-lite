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
            val actionRevision by host.feedRevision
            val items by host.feed.items
            val presentations by host.feed.presentations
            val loading by host.feed.loading
            val refreshing by host.feed.refreshing
            val noMore by host.feed.noMore
            ComposeFeedScreen(
                items, loading, refreshing, noMore, services,
                onOpen = host::openDetail,
                onRefresh = host.feed::refresh,
                onLoadMore = host.feed::loadMore,
                onSearch = { host.navigate("search") },
                onAction = host::feedAction,
                listState = listState,
                observeLoadMore = active,
                interactive = active,
                presentations = presentations,
                actionRevision = actionRevision,
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
                    link = state.link,
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
        "checkin_center", "checkin_page_pairing", "checkin_page_mobile_login", "checkin_page_task_settings",
        "checkin_page_membership", "checkin_page_checkout", "checkin_page_redeem", "checkin_page_purchases",
        "checkin_page_history", "checkin_page_history_detail" -> ComposeCheckinScreen(
            services, host::navigate, { host.handleBack() }, host.checkinPageController(),
            ComposeCheckinNavigation.page(route) ?: ComposeCheckinRoute.CENTER,
        )
        "reading_center" -> ComposeReadingCenterScreen(
            state = host.readingCenter.state.value,
            loading = host.readingCenter.loading.value,
            services = services,
            onOpen = host::openDetail,
            onReadingStats = { host.navigate("reading_stats") },
            onWatchLater = { host.navigate("watch_later") },
            onHistory = { host.navigate("reading_history") },
            onBack = { host.handleBack() },
        )
        "favorites" -> {
            val actionRevision by host.feedRevision
            ComposeFavoritesScreen(
                state = host.saved.favorites.state.value,
                folders = host.saved.favoriteFolders.value,
                selectedTab = host.saved.favoriteTab.value,
                foldersLoading = host.saved.favoriteFoldersLoading.value,
                foldersError = host.saved.favoriteFoldersError.value,
                services = services,
                onTabChange = host.saved::selectFavoriteTab,
                onOpen = host::openDetail,
                onOpenFolder = { host.navigate(it.route()) },
                onAction = host::feedAction,
                onRetry = host.saved::retryFavorites,
                onRetryFolders = { host.saved.loadFavoriteFolders(force = true) },
                onBack = { host.handleBack() },
                listState = listState,
                actionRevision = actionRevision,
            )
        }
        "favorite_folder" -> {
            val actionRevision by host.feedRevision
            ComposeFavoriteFolderScreen(
                title = host.saved.selectedFolder.value?.name.orEmpty(),
                state = host.saved.folderPosts.state.value,
                services = services,
                onOpen = host::openDetail,
                onAction = host::feedAction,
                onRetry = host.saved::retryFolder,
                onBack = { host.handleBack() },
                listState = listState,
                actionRevision = actionRevision,
            )
        }
        "watch_later" -> ComposeWatchLaterScreen(
            state = host.saved.watchLater.state.value,
            services = services,
            onOpen = host::openDetail,
            onRemove = host.saved.watchLater::remove,
            onRetry = host.saved.watchLater::refresh,
            onBack = { host.handleBack() },
            listState = listState,
        )
        "reading_history" -> {
            val actionRevision by host.feedRevision
            ComposeHistoryScreen(
                state = host.saved.history.state.value,
                query = host.saved.historyQuery.value,
                onQueryChange = { host.saved.historyQuery.value = it },
                services = services,
                onOpen = host::openDetail,
                onAction = host::feedAction,
                onRetry = host.saved::retryHistory,
                onBack = { host.handleBack() },
                listState = listState,
                actionRevision = actionRevision,
            )
        }
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
