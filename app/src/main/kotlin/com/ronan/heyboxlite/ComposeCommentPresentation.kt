package com.ronan.heyboxlite

import org.json.JSONArray
import org.json.JSONObject

/** Parsing does not annotate or otherwise mutate the server's comment payload. */
internal data class ComposeCommentThreadData(
    val root: JSONObject,
    val replies: List<JSONObject>,
    val expected: Int,
    val cy: Boolean,
) {
    val rootId: String get() = CommentData.commentId(root)
    val initialVisibleCount: Int get() = minOf(CommentReplyPaging.PAGE_SIZE, replies.size)

    companion object {
        fun from(group: JSONObject): ComposeCommentThreadData {
            val array = group.optJSONArray("comment")
            val root = array?.optJSONObject(0) ?: group
            val replies = buildList {
                if (array != null) for (index in 1 until array.length()) {
                    array.optJSONObject(index)?.let(::add)
                }
            }.sortedBy { CommentData.commentTime(it) }
            return ComposeCommentThreadData(root, replies,
                maxOf(root.optInt("child_num"), group.optInt("child_num"), replies.size),
                CommentData.isCyComment(root) || (!root.has("is_cy") && CommentData.isCyComment(group)))
        }
    }
}

internal fun composeOrderedComments(comments: List<JSONObject>, latest: Boolean): List<JSONObject> {
    val groups = JSONArray().apply { comments.forEach(::put) }
    return CommentOrder.sorted(groups, latest)
}

internal fun composeCommentKeys(comments: List<JSONObject>): List<String> {
    val ids = comments.map { CommentData.commentId(it.optJSONArray("comment")?.optJSONObject(0) ?: it) }
    val counts = ids.groupingBy { it }.eachCount()
    return ids.mapIndexed { index, id ->
        if (id.isNotBlank() && counts[id] == 1) "comment-$id" else "comment-$id-$index"
    }
}
