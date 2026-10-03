package com.ronan.heyboxlite

import org.json.JSONObject

internal fun composeFeedText(source: String): String =
    RichContent.commentText(source).ifEmpty { RichContent.plainText(source) }

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
