package com.ronan.heyboxlite

import org.json.JSONObject

/** Keep full-screen paging inside the selected comment or the article's own images. */
internal data class ComposeDetailGallery(val urls: List<String>, val index: Int)

internal fun composeDetailGallery(state: ComposeDetailState, selected: String): ComposeDetailGallery {
    val images = state.content.filter { it.image }.map { it.value }.ifEmpty { state.item.images.toList() }
    val urls = if (selected in images) images.distinct() else listOf(selected)
    return ComposeDetailGallery(urls, urls.indexOf(selected).coerceAtLeast(0))
}

internal fun composeCommentGallery(comment: JSONObject, selected: String): ComposeDetailGallery {
    val images = CommentData.commentImages(comment)
    val index = images.indexOfFirst { it.originalUrl == selected || it.previewUrl == selected }
    if (index < 0) return ComposeDetailGallery(listOf(selected), 0)
    return ComposeDetailGallery(images.map { it.originalUrl.ifBlank { it.previewUrl } }, index)
}

internal fun composeSyncPostActions(source: FeedItem, items: List<FeedItem>) {
    items.forEach { item ->
        if (item.id == source.id) {
            item.liked = source.liked
            item.likes = source.likes
            item.favorited = source.favorited
        }
        if (source.authorId.isNotBlank() && item.authorId == source.authorId) item.following = source.following
    }
}
