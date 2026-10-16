package com.ronan.heyboxlite

import org.json.JSONObject

/** Parsed detail data. No native page or comment Views are constructed here. */
internal data class ComposeDetailDocument(
    val body: JSONObject,
    val item: FeedItem,
    val content: List<RichContent.Block>,
    val videos: List<VideoData>,
    val comments: List<JSONObject>,
    val hsrc: String,
    val authCode: String,
    val diagnostics: String,
    val useImagePager: Boolean = false,
) {
    val link: JSONObject? get() = body.optJSONObject("result")?.optJSONObject("link")

    companion object {
        @JvmStatic
        fun parse(source: JSONObject, fallback: FeedItem, playGif: Boolean): ComposeDetailDocument {
            val body = DetailResponseNormalizer.normalize(source)
            val result = body.optJSONObject("result")
            val link = result?.optJSONObject("link")
            val content = DetailContentParser().resolve(link, fallback.description,
                link?.optJSONArray("imgs"), fallback)
            val groups = result?.optJSONArray("comments")
            val comments = buildList {
                if (groups != null) for (index in 0 until groups.length()) {
                    groups.optJSONObject(index)?.let(::add)
                }
            }
            val item = composeDetailItem(link, fallback)
            return ComposeDetailDocument(body, if (item.id.isBlank()) fallback else item,
                content.blocks, content.videos, comments,
                Json.first(item.hsrc, fallback.hsrc),
                Json.first(link?.optString("auth_code"), result?.optString("auth_code"), body.optString("auth_code")),
                DetailDiagnostics.build("detail", fallback.id, playGif, body, fallback,
                    link, link?.optJSONArray("imgs"), groups, content.blocks, content.videos),
                useImagePager = content.useImagePager)
        }
    }
}

private fun composeDetailItem(link: JSONObject?, fallback: FeedItem): FeedItem {
    if (link == null) return fallback
    val merged = fallback.toJson()
    val likeKeys = arrayOf("is_award_link", "is_award", "liked", "is_liked", "has_award", "award_state", "like_state")
    val favoriteKeys = arrayOf("is_favour", "is_favor", "is_fav", "favored", "has_favour", "has_favor")
    arrayOf(arrayOf("link_award_num", "like_num", "award_num", "award_count", "up_num", "up"),
        arrayOf("comment_num", "comment_count", "reply_num", "reply_count", "comments"),
        arrayOf("create_time", "created_at", "publish_time", "post_time", "time"),
        arrayOf("description", "summary", "brief", "text", "content"))
        .forEach { keys -> if (keys.any(link::has)) keys.forEach(merged::remove) }
    if (likeKeys.any(link::has)) merged.remove("is_liked")
    if (favoriteKeys.any(link::has)) merged.remove("is_favour")
    if (arrayOf("is_article", "link_style", "link_tag", "content_type").any(link::has)) merged.remove("is_article")
    if (arrayOf("h_src", "hsrc", "share_url").any(link::has)) merged.remove("h_src")
    val user = link.optJSONObject("user")
    if (user != null) {
        val fallbackUser = merged.optJSONObject("user") ?: JSONObject()
        arrayOf(arrayOf("username", "nickname", "name"),
            arrayOf("userid", "user_id", "heybox_id", "heyboxid", "uid", "account_id"),
            arrayOf("avatar", "avartar"), arrayOf("is_follow", "is_following", "follow_status"))
            .forEach { keys -> if (keys.any(user::has)) keys.forEach(fallbackUser::remove) }
        user.keys().forEach { fallbackUser.put(it, user.opt(it)) }
        merged.put("user", fallbackUser)
    }
    link.keys().forEach { if (it != "user") merged.put(it, link.opt(it)) }
    if (merged.optString("linkid").isBlank()) merged.put("linkid", fallback.id)
    return FeedItem.from(merged).also {
        if (likeKeys.any(link::has)) it.liked = Json.truthy(link, *likeKeys)
    }
}

internal data class ComposeReplyLoadState(
    val loading: Boolean = false,
    val error: String = "",
    val cursor: String = "",
    val hasMore: Boolean = true,
)
