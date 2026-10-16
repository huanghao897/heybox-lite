package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue

internal object ComposeCommentActionsTestFixtures {
    class Fixture(
        comments: List<JSONObject> = listOf(ComposeCommentActionsTestFixtures.thread(
            ComposeCommentActionsTestFixtures.comment("root"))),
        loggedIn: Boolean = true,
    ) {
        private val context = ApplicationProvider.getApplicationContext<Application>()
        private val preferences = context.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE)
        val session: SessionStore
        val navigation = ComposeNavigationState()
        val callbacks = RecordingCallbacks()
        val messages = mutableListOf<String>()
        val actions: ComposeCommentActions
        val detail: ComposeDetailState get() = requireNotNull(navigation.detail.value)
        val editor: ComposeCommentEditorState get() = requireNotNull(actions.editor.value)

        init {
            preferences.edit().clear().commit()
            session = SessionStore(context)
            if (loggedIn) {
                // A valid synthetic legacy cookie keeps auth independent of platform KeyStore support.
                val cookie = LegacyCookieCrypto(context).encrypt("heybox_id=42; pkey=offline-comment-fixture")
                preferences.edit().putString(SecureStrings.encryptedCookieKey(), cookie).commit()
                session.saveLogin(JSONObject().put("heyboxid", "42"))
                assertTrue("The offline fixture must exercise the authenticated action path", session.isLoggedIn())
            }
            actions = ComposeCommentActions(navigation, session, callbacks, messages::add)
            enter("post", comments)
        }

        fun enter(postId: String, comments: List<JSONObject>) {
            navigation.navigate("feed")
            navigation.showDetailLoading(FeedItem.from(JSONObject().put("linkid", postId).put("title", postId)))
            navigation.detail.value = detail.copy(comments = comments, loading = false)
        }

        fun findComment(id: String): JSONObject {
            for (group in detail.comments) {
                val array = group.optJSONArray("comment")
                if (array == null && CommentData.commentId(group) == id) return group
                if (array != null) for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    if (CommentData.commentId(value) == id) return value
                }
            }
            error("Missing fixture comment $id")
        }

        fun rowIds(): List<String> {
            val group = detail.comments.first()
            val array = group.optJSONArray("comment") ?: return listOf(CommentData.commentId(group))
            return (0 until array.length()).map { CommentData.commentId(array.getJSONObject(it)) }
        }
    }

    data class LikeRequest(val id: String, val liked: Boolean, val callback: ApiClient.Callback)
    data class RepliesRequest(val id: String, val cursor: String, val callback: ApiClient.Callback)
    data class SendRequest(val text: String, val rootId: String?, val replyId: String?,
        val callback: ApiClient.Callback)

    class RecordingCallbacks : ComposeAppCallbacks {
        val likes = mutableListOf<LikeRequest>()
        val replies = mutableListOf<RepliesRequest>()
        val sends = mutableListOf<SendRequest>()
        val opened = mutableListOf<FeedItem>()

        override fun setCommentLike(id: String, liked: Boolean, callback: ApiClient.Callback) {
            likes += LikeRequest(id, liked, callback)
        }
        override fun requestReplies(id: String, cursor: String, callback: ApiClient.Callback) {
            replies += RepliesRequest(id, cursor, callback)
        }
        override fun createComment(text: String, rootId: String?, replyId: String?, callback: ApiClient.Callback) {
            sends += SendRequest(text, rootId, replyId, callback)
        }
        override fun openDetail(item: FeedItem) { opened += item }
        override fun navigate(route: String) = Unit
        override fun back() = Unit
        override fun backTo(route: String) = Unit
        override fun requestImage(url: String) = Unit
        override fun requestVideo(video: VideoData) = Unit
        override fun feedAction(item: FeedItem, action: Int) = Unit
        override fun likeComment(comment: JSONObject) { error("Must use the real like request callback") }
        override fun replyComment(comment: JSONObject) { error("Must use the Compose editor") }
        override fun loadReplies(comment: JSONObject) { error("Must use the real reply request callback") }
        override fun writeComment(item: FeedItem) { error("Must use the Compose editor") }
        override fun loginCompleted() = Unit
        override fun settingsChanged() = Unit
        override fun runSettingsAction(action: String) = Unit
    }

    fun comment(id: String, liked: Boolean = false, likes: Int = 0): JSONObject = JSONObject()
        .put("commentid", id).put("text", "Body $id")
        .put("is_support", if (liked) 1 else 0).put("comment_award_num", likes)
        .put("user", JSONObject().put("userid", "author-$id").put("username", "Author $id"))

    fun thread(root: JSONObject, vararg replies: JSONObject): JSONObject {
        val rows = JSONArray().put(root)
        replies.forEach { rows.put(it) }
        return JSONObject().put("comment", rows)
    }

    fun replyPage(cursor: String, hasMore: Boolean, vararg replies: JSONObject): JSONObject {
        val rows = JSONArray()
        replies.forEach { rows.put(it) }
        return JSONObject().put("result", JSONObject().put("lastval", cursor)
            .put("has_more", if (hasMore) 1 else 0).put("comments", rows))
    }
}
