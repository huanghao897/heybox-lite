package com.ronan.heyboxlite

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.util.concurrent.Executors

/** Owns saved-content requests and local offline snapshots for Compose routes. */
internal class ComposeSavedController(
    private val services: ComposeServices,
) {
    val favoriteItems: MutableState<List<FeedItem>> = mutableStateOf(emptyList())
    val favoriteFolders: MutableState<List<ComposeFavoriteFolder>> = mutableStateOf(emptyList())
    val favoriteTab: MutableState<ComposeFavoriteTab> = mutableStateOf(ComposeFavoriteTab.POSTS)
    val favoriteLoading: MutableState<Boolean> = mutableStateOf(false)
    val favoriteError: MutableState<String> = mutableStateOf("")
    val historyItems: MutableState<List<FeedItem>> = mutableStateOf(emptyList())
    val historyLoading: MutableState<Boolean> = mutableStateOf(false)
    val historyError: MutableState<String> = mutableStateOf("")
    val watchLaterItems: MutableState<List<LocalCache.OfflineItem>> = mutableStateOf(emptyList())

    private var favoriteRequest = 0
    private var selectedFolderId: String? = null
    private var historyRequest = 0
    private var watchRequest = 0
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "heybox-compose-saved").apply { isDaemon = true }
    }

    fun openFavorites(folderId: String? = null) {
        selectedFolderId = folderId
        favoriteTab.value = ComposeFavoriteTab.POSTS
        loadFavoriteFolders()
    }

    fun selectFavoriteTab(tab: ComposeFavoriteTab) {
        favoriteTab.value = tab
        if (tab == ComposeFavoriteTab.POSTS && favoriteItems.value.isEmpty()) {
            loadFavoritePosts(selectedFolderId)
        }
    }

    fun loadFavoriteFolders() {
        val request = ++favoriteRequest
        favoriteLoading.value = true
        favoriteError.value = ""
        services.api.get(
            EndpointProvider.favoriteTabs(),
            emptyMap(),
            object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) {
                    if (request != favoriteRequest) return
                    favoriteFolders.value = SavedPostParser.favoriteFolders(body).map { folder ->
                        ComposeFavoriteFolder(
                            SavedPostParser.favoriteFolderId(folder),
                            SavedPostParser.favoriteFolderName(folder),
                            SavedPostParser.favoriteFolderCount(folder),
                        )
                    }
                    loadFavoritePosts(selectedFolderId, request)
                }

                override fun onError(message: String) {
                    if (request != favoriteRequest) return
                    favoriteLoading.value = false
                    favoriteError.value = message.ifEmpty { "收藏加载失败" }
                    favoriteItems.value = emptyList()
                }
            },
        )
    }

    fun openFolder(folder: ComposeFavoriteFolder) {
        selectedFolderId = folder.id
        favoriteTab.value = ComposeFavoriteTab.POSTS
        val request = ++favoriteRequest
        favoriteLoading.value = true
        favoriteError.value = ""
        loadFavoritePosts(folder.id, request)
    }

    private fun loadFavoritePosts(folderId: String?, parentRequest: Int = favoriteRequest) {
        val request = parentRequest
        services.api.get(
            EndpointProvider.favoriteLinks(),
            OfficialRequestParams.favorites(folderId, 0, 30),
            object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) {
                    if (request != favoriteRequest) return
                    favoriteItems.value = SavedPostParser.feedItems(body)
                    favoriteLoading.value = false
                    favoriteError.value = ""
                }

                override fun onError(message: String) {
                    if (request != favoriteRequest) return
                    favoriteLoading.value = false
                    favoriteError.value = message.ifEmpty { "收藏加载失败" }
                    favoriteItems.value = emptyList()
                }
            },
        )
    }

    fun openHistory() {
        val request = ++historyRequest
        historyLoading.value = true
        historyError.value = ""
        services.api.get(
            EndpointProvider.history(),
            OfficialRequestParams.history(0, 30),
            object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) {
                    if (request != historyRequest) return
                    historyItems.value = SavedPostParser.feedItems(body)
                    historyLoading.value = false
                    historyError.value = ""
                }

                override fun onError(message: String) {
                    if (request != historyRequest) return
                    historyLoading.value = false
                    historyError.value = message.ifEmpty { "历史记录加载失败" }
                    historyItems.value = emptyList()
                }
            },
        )
    }

    fun refreshWatchLater() {
        val request = ++watchRequest
        io.execute {
            val items = services.cache.watchLaterItems()
            services.handler.post {
                if (request == watchRequest) watchLaterItems.value = items
            }
        }
    }

    fun removeWatchLater(item: LocalCache.OfflineItem) {
        services.cache.removeWatchLater(item.item.id)
        refreshWatchLater()
    }

    fun close() {
        favoriteRequest++
        selectedFolderId = null
        historyRequest++
        watchRequest++
        favoriteLoading.value = false
        historyLoading.value = false
        io.shutdownNow()
    }
}
