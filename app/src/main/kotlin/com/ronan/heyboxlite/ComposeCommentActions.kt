package com.ronan.heyboxlite

import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

internal data class ComposeCommentEditorState(
    val postId: String,
    val replyId: String,
    val rootId: String,
    val recipient: String,
    val text: String,
    val sending: Boolean = false,
    val error: String = "",
) {
    val draftKey: String get() = "$postId:${replyId.ifBlank { "root" }}"
}

/** Comments publish snapshots; no native widget is used as the owner of a request. */
internal class ComposeCommentActions(
    private val navigation: ComposeNavigationState,
    private val session: SessionStore,
    private val callbacks: ComposeAppCallbacks,
    private val message: (String) -> Unit,
) {
    val editor = mutableStateOf<ComposeCommentEditorState?>(null)
    private var closed = false
    private var editorSerial = 0L
    private val draftEditors = mutableMapOf<String, Long>()

    fun close() { closed = true; dismiss() }

    fun edit(comment: JSONObject?, rootId: String) {
        if (closed) return
        if (!session.isLoggedIn()) { message("登录后可发表评论"); return }
        val post = navigation.detail.value ?: return
        val replyId = comment?.let(CommentData::commentId).orEmpty()
        val draft = ComposeCommentEditorState(post.item.id, replyId,
            rootId.ifBlank { comment?.let(CommentData::commentRootId).orEmpty() },
            comment?.optJSONObject("user")?.optString("username").orEmpty(), "")
        editorSerial++
        draftEditors[draft.draftKey] = editorSerial
        editor.value = draft.copy(text = session.commentDraft(draft.draftKey))
    }

    fun changeText(text: String) {
        val before = editor.value ?: return
        if (before.sending) return
        editor.value = before.copy(text = text, error = "")
        session.setCommentDraft(before.draftKey, text)
    }

    fun dismiss() { editorSerial++; editor.value = null }

    fun send() {
        val before = editor.value ?: return
        if (closed || before.sending || before.text.isBlank() || navigation.route.value != "detail" ||
            navigation.detail.value?.item?.id != before.postId) return
        val key = navigation.detail.value?.entryKey ?: return
        val requestEditor = editorSerial
        val account = session.userId()
        editor.value = before.copy(sending = true, error = "")
        callbacks.createComment(before.text.trim(), before.rootId.ifBlank { null },
            before.replyId.ifBlank { null }, object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) {
                    if (closed || account != session.userId()) return
                    if (draftEditors[before.draftKey] == requestEditor &&
                        session.commentDraft(before.draftKey) == before.text) {
                        session.setCommentDraft(before.draftKey, "")
                    }
                    navigation.updateDetail(key) { latest ->
                        latest.copy(refreshPending = true,
                            replyStates = if (before.rootId.isBlank()) latest.replyStates else
                                latest.replyStates + (before.rootId to ComposeReplyLoadState()))
                    }
                    if (!current(key)) return
                    if (editorSerial == requestEditor) {
                        editor.value = null
                        message("评论已发送")
                    }
                    if (editor.value == null) navigation.detail.value?.item?.let(callbacks::openDetail)
                }
                override fun onError(error: String) {
                    if (account != session.userId() || !current(key) || editorSerial != requestEditor) return
                    editor.value = before.copy(error = "发送失败：$error")
                }
            })
    }

    fun like(comment: JSONObject) {
        val state = navigation.detail.value ?: return
        val id = CommentData.commentId(comment)
        if (id.isBlank() || id in state.pendingLikes || !session.isLoggedIn()) {
            if (!session.isLoggedIn()) message("登录后可点赞评论")
            return
        }
        val oldLiked = CommentData.commentLiked(comment)
        val oldCount = CommentData.commentLikes(comment)
        val liked = !oldLiked
        val count = (oldCount + if (liked) 1 else -1).coerceAtLeast(0)
        val account = session.userId()
        navigation.detail.value = composeUpdateCommentLike(state, id, liked, count).copy(pendingLikes = state.pendingLikes + id)
        callbacks.setCommentLike(id, liked, object : ApiClient.Callback {
            override fun onSuccess(body: JSONObject) { finish(null) }
            override fun onError(error: String) { finish(error) }
            private fun finish(error: String?) {
                if (closed || account != session.userId()) return
                navigation.updateDetail(state.entryKey) { latest ->
                    (if (error == null) latest else composeUpdateCommentLike(latest, id, oldLiked, oldCount))
                        .copy(pendingLikes = latest.pendingLikes - id)
                }
                if (error != null && current(state.entryKey)) message("评论点赞失败：$error")
            }
        })
    }

    fun replies(root: JSONObject) {
        val state = navigation.detail.value ?: return
        val id = CommentData.commentId(root)
        if (id.isBlank()) return
        val before = state.replyStates[id] ?: ComposeReplyLoadState()
        val account = session.userId()
        if (before.loading || !before.hasMore) return
        val pending = before.copy(loading = true, error = "")
        setReply(id, pending)
        callbacks.requestReplies(id, before.cursor, object : ApiClient.Callback {
            override fun onSuccess(body: JSONObject) {
                if (!ownsRequest()) return
                val result = body.optJSONObject("result")
                val cursor = result?.optString("lastval").orEmpty()
                val replies = composeExtractReplies(result, id)
                navigation.appendDetailReplies(id, replies, state.entryKey)
                setReply(id, ComposeReplyLoadState(cursor = cursor,
                    hasMore = result != null && Json.truthy(result, "has_more") &&
                        cursor.isNotBlank() && cursor != before.cursor), state.entryKey)
            }
            override fun onError(error: String) {
                if (ownsRequest()) setReply(id, before.copy(error = error), state.entryKey)
            }
            private fun ownsRequest(): Boolean = !closed && account == session.userId() &&
                navigation.detailForEntry(state.entryKey)?.replyStates?.get(id) === pending
        })
    }

    private fun setReply(id: String, reply: ComposeReplyLoadState,
                         entryKey: Long = navigation.detail.value?.entryKey ?: -1) {
        navigation.updateDetail(entryKey) { value ->
            value.copy(replyStates = value.replyStates + (id to reply))
        }
    }

    private fun current(key: Long): Boolean = !closed && navigation.route.value == "detail" &&
        navigation.detail.value?.entryKey == key
}

internal fun composeExtractReplies(result: JSONObject?, rootId: String): List<JSONObject> {
    val source = result?.let { Json.firstArray(it, "comments", "comment", "list", "sub_comments") }
        ?: return emptyList()
    return buildList {
        fun addReply(value: JSONObject?) {
            if (value != null && CommentData.commentId(value) != rootId) add(value)
        }
        for (index in 0 until source.length()) {
            val value = source.optJSONObject(index) ?: continue
            val nested = value.optJSONArray("comment")
            if (nested == null) addReply(value) else {
                for (child in 0 until nested.length()) addReply(nested.optJSONObject(child))
            }
        }
    }
}
