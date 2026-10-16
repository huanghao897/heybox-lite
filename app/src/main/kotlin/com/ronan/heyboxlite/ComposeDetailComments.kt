package com.ronan.heyboxlite

import org.json.JSONArray
import org.json.JSONObject

internal data class ComposeCommentLikeState(val liked: Boolean, val count: Int)

/** A later body/game-card response must not undo replies or likes already displayed. */
internal fun composeMergeDetailComments(
    incoming: List<JSONObject>,
    previous: ComposeDetailState,
    preview: Boolean,
): List<JSONObject> {
    val oldGroups = previous.comments.associateBy { ComposeCommentThreadData.from(it).rootId }
    val groups = if (preview && incoming.isEmpty()) previous.comments else incoming
    return groups.map { group ->
        val thread = ComposeCommentThreadData.from(group)
        val old = oldGroups[thread.rootId]?.let(ComposeCommentThreadData::from)
        val comments = mutableListOf(thread.root).apply { addAll(thread.replies) }
        if (old != null && thread.rootId in previous.replyStates) {
            val known = comments.mapTo(mutableSetOf(), CommentData::commentId)
            old.replies.forEach { reply ->
                val id = CommentData.commentId(reply)
                if (id.isNotBlank() && known.add(id)) comments += reply
            }
        }
        JSONObject().apply {
            group.keys().forEach { if (it != "comment") put(it, group.opt(it)) }
            put("comment", JSONArray().apply {
                comments.forEach { comment ->
                    val like = previous.likeOverrides[CommentData.commentId(comment)]
                    put(if (like == null) comment else composeLikedComment(comment, like.liked, like.count))
                }
            })
        }
    }
}

internal fun composeUpdateCommentLike(state: ComposeDetailState, id: String,
                                     liked: Boolean, count: Int): ComposeDetailState =
    state.copy(likeOverrides = state.likeOverrides + (id to ComposeCommentLikeState(liked, count)),
        comments = state.comments.map { group ->
            val comments = group.optJSONArray("comment")
            if (comments == null) {
                if (CommentData.commentId(group) == id) composeLikedComment(group, liked, count) else group
            } else {
                var changed = false
                val next = JSONArray()
                for (index in 0 until comments.length()) {
                    val comment = comments.optJSONObject(index)
                    if (comment != null && CommentData.commentId(comment) == id) {
                        changed = true
                        next.put(composeLikedComment(comment, liked, count))
                    } else next.put(comments.opt(index))
                }
                if (changed) JSONObject(group.toString()).put("comment", next) else group
            }
        })

private fun composeLikedComment(comment: JSONObject, liked: Boolean, count: Int): JSONObject =
    JSONObject(comment.toString()).put("is_support", if (liked) 1 else 0)
        .put("comment_award_num", count.coerceAtLeast(0))
