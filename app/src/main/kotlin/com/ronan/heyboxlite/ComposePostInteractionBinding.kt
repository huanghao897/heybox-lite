package com.ronan.heyboxlite

import android.os.Handler
import androidx.compose.runtime.snapshots.SnapshotStateObserver

/** Projects the shared owner onto retained collections without another interaction state machine. */
internal class ComposePostInteractionBinding(
    private val owner: PostInteractionState,
    private val navigation: ComposeNavigationState,
    feed: ComposeFeedController,
    search: ComposeSearchController,
    saved: ComposeSavedController,
    reading: ComposeReadingCenterController,
    handler: Handler,
    private val onPublished: () -> Unit,
) {
    private var closed = false
    private val projections = mutableMapOf<Any, () -> Unit>()
    private val userSpaces = mutableMapOf<String, ComposeUserSpaceController>()
    private val changed: (Any) -> Unit = { scope ->
        projections[scope]?.let { project -> project(); onPublished() }
    }
    private val observer = SnapshotStateObserver { task ->
        handler.post { if (!closed) task() }
    }

    init {
        navigation.bindInteractions(owner)
        observer.start()
        feed.setInteractionMerger(owner::applyAll)
        search.setInteractionMerger(owner::applyAll)
        watch(feed) { feed.items.value }
        watch(search) { search.items.value }
        watch(saved.favorites) { saved.favorites.state.value.items }
        watch(saved.folderPosts) { saved.folderPosts.state.value.items }
        watch(saved.history) { saved.history.state.value.items }
        watch(saved.watchLater) { saved.watchLater.state.value.entries.map { it.item } }
        watch(reading) { listOfNotNull(reading.state.value?.recent) }
    }

    fun mergeAll() {
        if (closed) return
        navigation.mergeInteractions()
        projections.values.toList().forEach { it() }
    }

    fun watchUserSpace(id: String, controller: ComposeUserSpaceController) {
        if (closed || userSpaces[id] === controller) return
        userSpaces.put(id, controller)?.let(::forget)
        watch(controller) { controller.state.value.items }
    }

    fun retainUserSpaces(routes: Set<String>) {
        val ids = routes.filter { it.substringBefore('?') == "user_space" }
            .mapTo(mutableSetOf()) { composeUserSpaceRoute(it).userId }
        userSpaces.keys.filterNot { it in ids }.forEach { id ->
            userSpaces.remove(id)?.let(::forget)
        }
    }

    fun close() {
        closed = true
        observer.stop()
        observer.clear()
        projections.clear()
        userSpaces.clear()
    }

    private fun watch(scope: Any, read: () -> List<FeedItem>) {
        projections[scope] = {
            var items = emptyList<FeedItem>()
            observer.observeReads(scope, changed) { items = read() }
            owner.applyAll(items)
        }
        projections.getValue(scope)()
    }

    private fun forget(scope: Any) {
        observer.clear(scope)
        projections.remove(scope)
    }
}
