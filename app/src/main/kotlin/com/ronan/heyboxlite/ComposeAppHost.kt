package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/**
 * Owns the Compose surface mounted above the legacy shell.  It deliberately
 * keeps data and side effects in the existing Java services and only owns
 * route state plus screen state required for progressive rendering.
 */
internal class ComposeAppHost(
    activity: ComponentActivity,
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
    internal val navigation = ComposeNavigationState()
    private val servicesState = mutableStateOf(
        ComposeServices(
            activity, session, api, GameDetailClient(api), cache, handler, reading, checkin,
            composeThemeState(tokens, uiScale, textScale, roundScreen)
                .copy(reading = ComposeReadingSettings.from(session)), toast,
        ),
    )
    internal val feed = ComposeFeedController(session, api, cache, toast::show)
    internal val profile = ComposeProfileController(servicesState.value)
    internal val search = ComposeSearchController(session, api, cache, handler)
    internal val saved = ComposeSavedController(servicesState.value)
    private val readingLoader = ReadingCenterLoader(activity, cache)
    internal val readingCenter = ComposeReadingCenterController(
        loadSnapshot = readingLoader::load,
        mapSnapshot = { snapshot ->
            val current = servicesState.value
            val stats = current.reading.stats()
            ComposeReadingCenterState.from(snapshot,
                "${Format.readingDuration(stats.todayMs())} · 累计 ${Format.readingDuration(stats.totalMs())}",
                snapshot.recent != null && (current.cache.scroll(snapshot.recent.id) > 0 ||
                    ComposeDetailPosition.restore(current.activity, current.session.userId(), snapshot.recent.id).hasProgress))
        },
        closeLoader = readingLoader::close,
    )
    private val transitionSnapshots = TransitionSnapshotStore()
    internal val callbacks = callbacks
    internal val detailComments = ComposeCommentActions(navigation, session, callbacks, toast::show)
    private val view = ComposeView(activity)
    internal val feedRevision = mutableStateOf(0)
    internal val profileRevision = mutableStateOf(0)
    internal val feedListState = LazyListState()
    internal val feedRotaryInput = ComposeFeedRotaryInput()
    internal val detailRotaryInput = ComposeFeedRotaryInput()
    private val routeResources = ComposeRouteResources(api, cache, feedListState)
    private var accountId = session.userId()
    private var sessionGeneration = session.sessionGeneration()
    private var feedStarted = false
    private var mounted = true
    private var suppressNextTransitionSnapshot = false
    private var checkinPageOwner: ComposeCheckinController? = null
    private val surfaceActive = mutableStateOf(true)
    private val savedStateRegistry = activity.savedStateRegistry

    init {
        val restored = savedStateRegistry.consumeRestoredStateForKey("heybox.compose.saved-query")
        if (restored?.getString("account") == session.userId()) {
            saved.historyQuery.value = restored.getString("history", "")
        }
        savedStateRegistry.registerSavedStateProvider("heybox.compose.saved-query") {
            Bundle().apply {
                putString("account", session.userId())
                putString("history", saved.historyQuery.value)
            }
        }
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
        if (route.substringBefore('?') != "detail") detailComments.dismiss()
        captureCurrentTransitionSnapshot()
        clearRotaryInput()
        navigation.setRoute(route)
        prepareSavedRoute(route)
    }

    /**
     * Entry point for native callers opening a Compose user page. Unlike a
     * plain setRoute call, this records the actual current route so the page
     * can return to search, reading center or the preserved detail screen.
     */
    fun showExternalUserSpace(route: String) {
        saveDetailProgress()
        detailComments.dismiss()
        captureCurrentTransitionSnapshot()
        clearRotaryInput()
        navigation.showExternalUserSpace(route)
    }

    fun currentRoute(): String = navigation.route.value

    fun currentRouteSpec(): String = navigation.currentRouteSpec()

    fun isRoute(route: String): Boolean = navigation.route.value == route

    fun isMounted(): Boolean = mounted && view.parent != null

    /**
     * Returns the route that should be visible underneath a horizontal drag.
     * An empty target means the home-screen exit action, not another page.
     */
    fun composeSwipeTarget(route: String, direction: Int): String? {
        val key = route.substringBefore('?')
        if (key == "detail" && direction > 0 &&
            !routeResources.canSwipeDetailBack(currentDetailEntryKey())) return null
        val checkinPage = ComposeCheckinNavigation.page(route)
        if (checkinPage != null && direction > 0 && servicesState.value.session.shellBackSwipe()) {
            ComposeCheckinNavigation.parent(checkinPage)?.let { return ComposeCheckinNavigation.key(it) }
        }
        if (direction < 0 && key == "feed") return "profile"
        if (direction > 0 && key == "feed") {
            return if (servicesState.value.session.homeSwipeExit()) "" else null
        }
        if (direction > 0 && servicesState.value.session.shellBackSwipe()) {
            val target = navigation.backPresentationRoute()
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
        if (navigation.route.value == "checkin_center" && ComposeCheckinNavigation.page(target) != null) {
            checkinPageOwner?.navigateBack { }
            return
        }
        if (target.isEmpty()) {
            callbacks.back()
            return
        }
        val current = navigation.route.value
        suppressNextTransitionSnapshot = true
        if (target == navigation.backPresentationRoute() && current != "feed") {
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

    fun setSurfaceActive(active: Boolean) {
        if (!active) feedRotaryInput.clear()
        surfaceActive.value = active
    }

    internal fun listState(route: String): LazyListState =
        routeResources.listState(route, navigation, saved.selectedFolder.value?.id.orEmpty())

    internal fun userSpaceController(route: ComposeUserSpaceRoute): ComposeUserSpaceController =
        routeResources.userSpace(route)

    internal fun setDetailPage(entryKey: Long, page: ComposeDetailPagerNavigation?) =
        routeResources.setDetailPage(entryKey, page)

    fun updateTheme(tokens: ThemeTokens, roundScreen: Boolean, uiScale: Float, textScale: Float) {
        feedRotaryInput.clear()
        view.setBackgroundColor(tokens.background)
        val current = servicesState.value
        servicesState.value = current.copy(
            theme = composeThemeState(tokens, uiScale, textScale, roundScreen)
                .copy(reading = ComposeReadingSettings.from(current.session)),
        )
    }

    private fun captureCurrentTransitionSnapshot() {
        if (suppressNextTransitionSnapshot) {
            suppressNextTransitionSnapshot = false
            return
        }
        if (!mounted || !view.isShown) return
        val routeSpec = navigation.currentRouteSpec().ifBlank { "feed" }
        if (ComposeSwipePresentation.isLiveRoute(routeSpec)) return
        transitionSnapshots.capture(
            routeSpec,
            8,
            view,
            servicesState.value.theme.background.toArgb(),
            servicesState.value.cache,
        )
    }

    fun invalidateFeed() {
        if (isRoute("detail")) currentDetailItem()?.let { item ->
            composeSyncPostActions(item, feed.items.value)
            composeSyncPostActions(item, search.items.value)
        }
        feedRevision.value++
    }

    fun invalidateProfile() {
        val session = servicesState.value.session
        val currentAccount = session.userId()
        val currentGeneration = session.sessionGeneration()
        if (currentAccount != accountId || currentGeneration != sessionGeneration) {
            detailComments.dismiss()
            navigation.clearSavedState()
            navigation.clearDetail()
            routeResources.clear()
            feed.invalidateSession()
            search.invalidateSession()
            feedStarted = false
            accountId = currentAccount
            sessionGeneration = currentGeneration
            if (isRoute("feed")) startFeedIfNeeded()
        }
        profile.invalidate()
        saved.invalidateAccount()
        profileRevision.value++
    }

    fun scrollRotary(distance: Int): Boolean {
        if (!mounted || distance == 0) return false
        if (navigation.route.value in setOf("feed", "detail")) {
            if (surfaceActive.value) {
                (if (navigation.route.value == "feed") feedRotaryInput else detailRotaryInput).offer(distance)
            }
            return true
        }
        val current = servicesState.value
        val serial = (current.theme.rotaryRequest?.serial ?: 0) + 1
        servicesState.value = current.copy(
            theme = current.theme.copy(
                rotaryRequest = ComposeRotaryRequest(serial, distance),
            ),
        )
        return true
    }

    private fun clearRotaryInput() {
        feedRotaryInput.clear()
        detailRotaryInput.clear()
        val current = servicesState.value
        if (current.theme.rotaryRequest != null) {
            servicesState.value = current.copy(theme = current.theme.copy(rotaryRequest = null))
        }
    }

    fun close() {
        if (!mounted) return
        mounted = false
        feedRotaryInput.clear()
        detailRotaryInput.clear()
        savedStateRegistry.unregisterSavedStateProvider("heybox.compose.saved-query")
        navigation.clearSavedState()
        routeResources.clear()
        detailComments.close()
        feed.close()
        profile.close()
        search.close()
        saved.close()
        readingCenter.close()
        checkinPageOwner?.close()
        checkinPageOwner = null
        transitionSnapshots.releaseAll()
        view.disposeComposition()
    }

    private fun prepareSavedRoute(route: String) {
        when (route.substringBefore('?')) {
            "favorites" -> saved.openFavorites()
            "favorite_folder" -> saved.openFolder(ComposeFavoriteFolder.fromRoute(route))
            "watch_later" -> saved.watchLater.refresh()
            "reading_history" -> saved.openHistory()
            "reading_center" -> readingCenter.open()
        }
    }

    fun showDetailLoading(item: FeedItem) {
        if (navigation.detail.value?.item?.id != item.id) detailComments.dismiss()
        captureCurrentTransitionSnapshot()
        clearRotaryInput()
        navigation.showDetailLoading(item)
    }

    fun currentDetailItem(): FeedItem? = navigation.detail.value?.item
    fun currentDetailBody(): org.json.JSONObject? = navigation.detail.value?.body
    fun currentDetailContext(): ComposeDetailContext? =
        if (isRoute("detail")) navigation.detail.value?.actionContext() else null
    fun currentDetailEntryKey(): Long = navigation.detail.value?.entryKey ?: -1
    fun detailNeedsLoad(): Boolean = navigation.detail.value?.let { it.loading || it.refreshPending } == true

    fun finishDetailRequest() {
        navigation.detail.value = navigation.detail.value?.copy(refreshPending = false)
    }

    fun showDetailDocument(document: ComposeDetailDocument) {
        navigation.showDetailDocument(document)
    }

    fun showDetailError(message: String) {
        navigation.detail.value = navigation.detail.value?.copy(loading = false, error = message)
    }

    fun finishDetailLoading() {
        navigation.detail.value = navigation.detail.value?.copy(loading = false)
    }

    fun saveDetailProgress() {
        if (!mounted || !navigation.route.value.equals("detail")) return
        val services = servicesState.value
        if (!services.session.rememberDetailScroll()) return
        val detail = navigation.detail.value ?: return
        val scroll = listState("detail")
        val position = ComposeDetailPosition.from(scroll)
        ComposeDetailPosition.save(services.activity, services.session.userId(), detail.item.id, position)
    }

    fun showDetailResult(result: DetailPageAssembler.Result, fallback: FeedItem) {
        navigation.showDetailResult(result, fallback)
    }

    fun handleBack(): Boolean {
        if (detailComments.editor.value != null) { detailComments.dismiss(); return true }
        val current = navigation.route.value
        if (current == "checkin_center" && checkinPageOwner?.uiState?.route != ComposeCheckinRoute.CENTER
            && checkinPageOwner != null) {
            checkinPageOwner?.navigateBack { }
            return true
        }
        if (current == "feed") return false
        if (current == "detail") {
            if (routeResources.returnToDetailBody(currentDetailEntryKey())) return true
            saveDetailProgress()
            val targetSpec = navigation.backTarget()
            navigation.restoreSavedDetailIfNeeded(targetSpec)
            callbacks.backTo(targetSpec)
            return true
        }
        val target = navigation.backTarget()
        clearRotaryInput()
        navigation.restoreSavedDetailIfNeeded(target)
        callbacks.backTo(target)
        return true
    }

    internal fun checkinPageController(): ComposeCheckinController? {
        val services = servicesState.value
        val coordinator = services.checkin ?: return null
        return checkinPageOwner ?: ComposeCheckinController(services, coordinator).also { checkinPageOwner = it }
    }

    internal fun navigate(route: String) {
        saveDetailProgress()
        if (route.substringBefore('?') != "detail") detailComments.dismiss()
        val key = route.substringBefore('?')
        if ((key == "favorites" || key == "reading_history" || key == "favorite_folder")
            && !servicesState.value.session.isLoggedIn()) {
            navigate("login")
            return
        }
        when (key) {
            "cache_prune", "cache_clear", "diagnostics_export", "diagnostics_upload",
            "crash_test", "logout", "open_url", "update_download" -> {
                callbacks.runSettingsAction(route)
            }
            else -> {
                captureCurrentTransitionSnapshot()
                clearRotaryInput()
                navigation.navigate(route)
                prepareSavedRoute(route)
                callbacks.navigate(route)
            }
        }
    }

    internal fun openDetail(item: FeedItem) {
        clearRotaryInput()
        callbacks.openDetail(item)
    }

    fun likeDetailComment(comment: org.json.JSONObject) { detailComments.like(comment) }
    fun editDetailComment(comment: org.json.JSONObject?, rootId: String) { detailComments.edit(comment, rootId) }
    fun loadDetailReplies(comment: org.json.JSONObject) { detailComments.replies(comment) }

    private fun startFeedIfNeeded() {
        if (feedStarted || !mounted) return
        feedStarted = true
        feed.restoreCache()
        feed.loadInitial()
    }

    @Composable
    private fun ComposeAppRoot(host: ComposeAppHost) {
        val services by host.servicesState
        val route by host.navigation.route
        val swipeRoute = if (route == "checkin_center") host.checkinPageController()?.uiState?.route
            ?.let(ComposeCheckinNavigation::key) ?: route else host.navigation.presentationRoute()
        val screenStates = rememberSaveableStateHolder()
        RetainComposeRouteResources(swipeRoute, host.navigation, host.routeResources, screenStates)
        LaunchedEffect(route) {
            if (route == "checkin_center") host.checkinPageController()?.start() else {
                host.checkinPageOwner?.close()
                host.checkinPageOwner = null
            }
            if (route == "profile") host.profile.refresh()
            if (route == "feed") host.startFeedIfNeeded()
        }
        HeyboxComposeTheme(services.theme) {
            ComposeSwipeContainer(host, swipeRoute, services) { page, active ->
                val pageActive = active && host.surfaceActive.value
                val pageServices = if (pageActive) services else services.copy(
                    theme = services.theme.copy(rotaryRequest = null),
                )
                screenStates.SaveableStateProvider(page) {
                    ComposeRouteScreen(host, page, pageServices, pageActive)
                }
            }
        }
    }
}
