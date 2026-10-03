package com.ronan.heyboxlite

import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.util.concurrent.Executors

/** Coordinates collections without owning their UI, disk work or request state machines. */
internal class ComposeSavedController(private val services: ComposeServices) {
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "heybox-compose-saved").apply { isDaemon = true }
    }
    private var closed = false
    private var folderRequest = 0
    private var foldersAccount = ""
    val favoriteFoldersLoading = mutableStateOf(false)
    private var foldersLoaded = false
    private var foldersLoadedAt = 0L
    val favoriteFolders = mutableStateOf<List<ComposeFavoriteFolder>>(emptyList())
    val favoriteFoldersError = mutableStateOf("")
    val favoriteTab = mutableStateOf(ComposeFavoriteTab.POSTS)
    val selectedFolder = mutableStateOf<ComposeFavoriteFolder?>(null)
    val favorites = posts(EndpointProvider.favoriteLinks(), markFavorites = true)
    val folderPosts = posts(EndpointProvider.favoriteLinks(), markFavorites = true)
    val history = posts(EndpointProvider.history())
    val historyQuery = mutableStateOf("")
    val watchLater = ComposeWatchLaterController(
        readEntries = {
            services.cache.watchLaterItems().map { entry ->
                ComposeOfflineEntry(entry.item,
                    entry.detailBytes + ImageLoader.offlineBytes(entry.imageUrls), entry.updatedAt)
            }
        },
        remove = services.cache::removeWatchLater,
        background = ::background,
        publish = { services.handler.post(it) },
        reportFailure = ::cacheFailure,
    )

    fun openFavorites() {
        if (closed) return
        favorites.load("all", OfficialRequestParams.favorites(null, 0, 30))
        loadFavoriteFolders()
    }

    fun selectFavoriteTab(tab: ComposeFavoriteTab) {
        favoriteTab.value = tab
        if (tab == ComposeFavoriteTab.FOLDERS) loadFavoriteFolders()
    }

    fun loadFavoriteFolders(force: Boolean = false) {
        if (closed) return
        val account = accountId()
        if (account.isBlank()) return
        if (!force && foldersAccount == account && (favoriteFoldersLoading.value
                || (foldersLoaded && System.nanoTime() / 1_000_000L - foldersLoadedAt < 60_000L))) return
        if (foldersAccount != account) favoriteFolders.value = emptyList()
        foldersAccount = account
        favoriteFoldersLoading.value = true
        val request = ++folderRequest
        services.api.get(EndpointProvider.favoriteTabs(), emptyMap(), object : ApiClient.Callback {
            override fun onSuccess(body: JSONObject) {
                if (closed || request != folderRequest || account != accountId()) return
                favoriteFoldersLoading.value = false
                foldersLoaded = true
                foldersLoadedAt = System.nanoTime() / 1_000_000L
                favoriteFoldersError.value = ""
                favoriteFolders.value = SavedPostParser.favoriteFolders(body).map { folder ->
                    ComposeFavoriteFolder(SavedPostParser.favoriteFolderId(folder),
                        SavedPostParser.favoriteFolderName(folder), SavedPostParser.favoriteFolderCount(folder))
                }
            }

            override fun onError(message: String) {
                if (closed || request != folderRequest || account != accountId()) return
                favoriteFoldersLoading.value = false
                favoriteFoldersError.value = message.ifBlank { "收藏夹加载失败" }
            }
        })
    }

    fun openFolder(folder: ComposeFavoriteFolder) {
        if (closed) return
        selectedFolder.value = folder
        folderPosts.load(folder.id, OfficialRequestParams.favorites(folder.id, 0, 30))
    }

    fun openHistory() {
        history.load("history", OfficialRequestParams.history(0, 30))
    }

    fun retryFavorites() {
        favorites.load("all", OfficialRequestParams.favorites(null, 0, 30), force = true)
    }

    fun retryFolder() {
        val folder = selectedFolder.value ?: return
        folderPosts.load(folder.id, OfficialRequestParams.favorites(folder.id, 0, 30), force = true)
    }

    fun retryHistory() {
        history.load("history", OfficialRequestParams.history(0, 30), force = true)
    }

    fun invalidateAccount() {
        folderRequest++
        favoriteFoldersLoading.value = false
        foldersLoaded = false
        foldersAccount = ""
        favoriteFolders.value = emptyList()
        favoriteFoldersError.value = ""
        favoriteTab.value = ComposeFavoriteTab.POSTS
        selectedFolder.value = null
        favorites.invalidate()
        folderPosts.invalidate()
        history.invalidate()
        historyQuery.value = ""
    }

    fun close() {
        closed = true
        folderRequest++
        favorites.close()
        folderPosts.close()
        history.close()
        watchLater.close()
        io.shutdownNow()
    }

    private fun posts(path: String, markFavorites: Boolean = false) = ComposeSavedPostsController(
        path = path,
        accountId = ::accountId,
        request = services.api::get,
        readCache = services.cache::savedList,
        writeCache = services.cache::saveSavedList,
        filter = { FeedCollection.filter(it, services.session.blockKeywordList()) },
        background = ::background,
        publish = { services.handler.post(it) },
        cacheFailure = ::cacheFailure,
        markFavorites = markFavorites,
    )

    private fun accountId(): String = if (services.session.isLoggedIn()) services.session.userId() else ""

    private fun background(task: () -> Unit) {
        if (!closed) io.execute(task)
    }

    private fun cacheFailure(error: RuntimeException) {
        services.cache.log("saved content IO failed error=" + error.javaClass.simpleName)
    }
}
