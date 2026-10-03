package com.ronan.heyboxlite

import org.json.JSONObject

internal fun composeFeedText(source: String): String =
    RichContent.commentText(source).ifEmpty { RichContent.plainText(source) }

internal data class ComposeFeedPresentation(
    val title: String,
    val description: String,
    val game: ComposeGameCardPresentation?,
)

/** Java action callbacks mutate FeedItem in place; publish their visible values explicitly. */
internal data class ComposeFeedActionState(
    val likes: Int,
    val liked: Boolean,
    val following: Boolean,
    val followPending: Boolean,
) {
    companion object {
        fun from(item: FeedItem) = ComposeFeedActionState(
            item.likes, item.liked, item.following, item.followPending,
        )
    }
}

internal fun composeFeedPresentation(item: FeedItem) = ComposeFeedPresentation(
    title = composeFeedText(item.title).ifEmpty { "\u65e0\u6807\u9898\u5185\u5bb9" },
    description = composeFeedText(item.description),
    game = composeGameCardPresentation(item.contentPreload),
)

/** Owned by one screen, not a lazy item: retain parses across scroll-out and scroll-in. */
internal class ComposeFeedPresentationCache(private val capacity: Int = 64) {
    init { require(capacity > 0) }

    private val entries = object : LinkedHashMap<FeedItem, ComposeFeedPresentation>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FeedItem, ComposeFeedPresentation>?) =
            size > capacity
    }

    // FeedItem has identity equality. A replacement with the same post ID must be parsed anew.
    fun get(item: FeedItem): ComposeFeedPresentation = entries.getOrPut(item) {
        composeFeedPresentation(item)
    }

    val size: Int get() = entries.size
}

/** Only parsed content is cached; mutable FeedItem action fields stay outside this snapshot. */
internal data class ComposeGameCardPresentation(
    val count: Int,
    val name: String,
    val coverUrl: String,
    val metadata: String,
)

internal fun composeGameCardPresentation(preload: JSONObject?): ComposeGameCardPresentation? {
    if (preload == null) return null
    val count = ArticleGameCards.count(preload)
    if (count <= 0) return null
    val data = GameCardData.fromEmbedded(preload, "")
    return ComposeGameCardPresentation(
        count = count,
        name = data?.name.orEmpty(),
        coverUrl = data?.coverUrl.orEmpty(),
        metadata = listOf(data?.platforms, data?.score, data?.currentPrice)
            .filter { !it.isNullOrEmpty() }.joinToString(" \u00b7 "),
    )
}
