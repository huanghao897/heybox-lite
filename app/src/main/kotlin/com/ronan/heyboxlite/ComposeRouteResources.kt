package com.ronan.heyboxlite

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder

/** Retains live return-page resources without retaining them across account changes. */
internal class ComposeRouteResources(
    private val api: ApiClient,
    private val cache: LocalCache,
    private val feedListState: LazyListState,
) {
    private val userSpaces = mutableMapOf<String, ComposeUserSpaceController>()
    private val listStates = mutableMapOf("feed" to feedListState)

    fun listState(route: String, navigation: ComposeNavigationState, folderId: String): LazyListState {
        val key = when (route.substringBefore('?')) {
            "favorite_folder" -> "favorite_folder:$folderId"
            "detail" -> "detail:${navigation.detailForPresentation(route)?.entryKey}"
            else -> route
        }
        return listStates.getOrPut(key) { LazyListState() }
    }

    fun userSpace(route: ComposeUserSpaceRoute): ComposeUserSpaceController =
        userSpaces.getOrPut(route.userId) {
            ComposeUserSpaceController(api, cache).also { it.load(route) }
        }

    fun retain(routes: Set<String>) {
        val userIds = routes.filter { it.substringBefore('?') == "user_space" }
            .mapTo(mutableSetOf()) { composeUserSpaceRoute(it).userId }
        userSpaces.keys.filterNot { it in userIds }.forEach { userSpaces.remove(it)?.close() }
        val details = routes.filter { it.substringBefore('?') == "detail" }
            .mapTo(mutableSetOf()) { "detail:${android.net.Uri.parse(it).getQueryParameter("entry")}" }
        listStates.keys.removeAll { key -> key.startsWith("detail:") && key !in details ||
            key.startsWith("user_space?") && key !in routes }
    }

    fun clear() {
        userSpaces.values.forEach { it.close() }
        userSpaces.clear()
        listStates.clear()
        listStates["feed"] = feedListState
    }
}

@Composable
internal fun RetainComposeRouteResources(
    route: String,
    navigation: ComposeNavigationState,
    resources: ComposeRouteResources,
    pageStates: SaveableStateHolder,
) {
    val retainedContent = remember { mutableSetOf<String>() }
    LaunchedEffect(route) {
        val routes = navigation.retainedContentRoutes()
        resources.retain(routes)
        retainedContent.filterNot { it in routes }.forEach(pageStates::removeState)
        retainedContent.clear()
        retainedContent.addAll(routes.filter { it.startsWith("detail?") || it.startsWith("user_space?") })
    }
}
