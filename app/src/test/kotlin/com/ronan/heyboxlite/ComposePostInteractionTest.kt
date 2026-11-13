package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [InteractionApiShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ComposePostInteractionTest {
    @Test fun pendingFavoriteSurvivesFullDocumentAndSuccessUpdatesEveryPostProjection() {
        Fixture().use { f ->
            val preview = post("post")
            val feedCopy = post("post")
            val searchCopy = post("post")
            val savedCopy = post("post")
            f.host.feed.items.value = listOf(feedCopy)
            f.host.search.items.value = listOf(searchCopy)
            f.host.saved.favorites.state.value = ComposeSavedPostsState(listOf(savedCopy))
            f.host.showDetailLoading(preview)

            f.action(preview, ComposeAppCallbacks.ACTION_FAVORITE)
            assertTrue(preview.favorited && feedCopy.favorited && searchCopy.favorited && savedCopy.favorited)
            f.host.showDetailDocument(document(preview))
            val full = f.host.currentDetailItem()!!
            assertNotSame(preview, full)
            assertTrue(full.favorited)
            f.posts.single().callback.onSuccess(JSONObject())

            assertTrue(full.favorited && feedCopy.favorited && searchCopy.favorited && savedCopy.favorited)
            assertTrue(f.actions.linkFavored(document(preview).link))
        }
    }

    @Test fun confirmedFavoriteSurvivesAnotherStaleDocumentAndFailureRollsBackTheReplacement() {
        Fixture().use { f ->
            val preview = post("post")
            f.host.showDetailLoading(preview)
            f.action(preview, ComposeAppCallbacks.ACTION_FAVORITE)
            f.posts.single().callback.onSuccess(JSONObject())
            f.host.showDetailDocument(document(preview))
            val full = f.host.currentDetailItem()!!
            assertTrue(full.favorited)

            f.action(full, ComposeAppCallbacks.ACTION_FAVORITE)
            f.host.showDetailDocument(document(preview))
            val replacement = f.host.currentDetailItem()!!
            assertFalse(replacement.favorited)
            f.posts.last().callback.onError("offline")
            assertTrue(replacement.favorited)
            f.host.showDetailDocument(document(preview))
            assertTrue(f.host.currentDetailItem()!!.favorited)
        }
    }

    @Test fun favoriteFailureAfterProgressiveReplacementRestoresAllCopies() {
        Fixture().use { f ->
            val preview = post("post")
            val otherCopy = post("post")
            f.host.feed.items.value = listOf(otherCopy)
            f.host.showDetailLoading(preview)
            f.action(preview, ComposeAppCallbacks.ACTION_FAVORITE)
            f.host.showDetailDocument(document(preview))
            f.posts.single().callback.onError("offline")
            assertFalse(preview.favorited || otherCopy.favorited || f.host.currentDetailItem()!!.favorited)
        }
    }

    @Test fun followUpdatesSameAuthorAcrossNativeComposeSavedAndRetainedDetailCollections() {
        Fixture().use { f ->
            val clicked = post("clicked")
            val sibling = post("sibling")
            val native = post("native")
            val search = post("search")
            val saved = post("saved")
            val stranger = post("stranger", "other-author")
            f.nativeFeed += native
            f.host.feed.items.value = listOf(clicked, sibling, stranger)
            f.host.search.items.value = listOf(search)
            f.host.saved.history.state.value = ComposeSavedPostsState(listOf(saved))
            f.host.showDetailLoading(post("parent"))
            f.host.showDetailDocument(document(post("parent")))
            val parent = f.host.currentDetailItem()!!
            f.host.navigation.navigate("user_space?user=author")
            f.host.showDetailLoading(post("child"))

            f.action(clicked, ComposeAppCallbacks.ACTION_FOLLOW)
            val copies = listOf(clicked, sibling, native, search, saved, parent, f.host.currentDetailItem()!!)
            assertTrue(copies.all { it.following && it.followPending })
            assertFalse(stranger.following || stranger.followPending)
            f.posts.single().callback.onSuccess(JSONObject())
            assertTrue(copies.all { it.following && !it.followPending })
            f.back("user_space?user=author")
            f.back("detail")
            assertSame(parent, f.host.currentDetailItem())
            assertTrue(parent.following)

            val retained = copies.filterNot { it.id == "child" }
            f.action(sibling, ComposeAppCallbacks.ACTION_FOLLOW)
            assertTrue(retained.all { !it.following && it.followPending })
            f.posts.last().callback.onError("offline")
            assertTrue(retained.all { it.following && !it.followPending })
            assertFalse(stranger.following)
        }
    }

    @Test fun olderWriteCallbacksCannotUndoANewerConfirmedToggle() {
        Fixture().use { f ->
            val item = post("post")
            f.host.feed.items.value = listOf(item)
            f.action(item, ComposeAppCallbacks.ACTION_FAVORITE)
            val old = f.posts.single().callback
            f.action(item, ComposeAppCallbacks.ACTION_FAVORITE)
            val current = f.posts.last().callback
            current.onSuccess(JSONObject())
            val messages = f.messages.toList()
            old.onSuccess(JSONObject())
            old.onError("old failure")
            current.onError("duplicate failure")
            assertFalse(item.favorited)
            assertEquals(messages, f.messages)
        }
    }

    @Test fun laterFeedAndSearchPublicationsUseTheSameConfirmedOwner() {
        Fixture().use { f ->
            val clicked = post("post")
            f.action(clicked, ComposeAppCallbacks.ACTION_FAVORITE)
            f.posts.last().callback.onSuccess(JSONObject())
            f.action(clicked, ComposeAppCallbacks.ACTION_LIKE)
            f.posts.last().callback.onSuccess(JSONObject())
            f.action(clicked, ComposeAppCallbacks.ACTION_FOLLOW)
            f.posts.last().callback.onSuccess(JSONObject())

            f.host.feed.loadInitial()
            f.gets.last { it.path == EndpointProvider.feeds() }.callback.onSuccess(page("post", "feed-sibling"))
            f.drainFeed()
            val feed = f.host.feed.items.value
            assertEquals(listOf("post", "feed-sibling"), feed.map { it.id })
            assertTrue(feed.all { it.following && !it.followPending })
            assertTrue(feed.first().liked && feed.first().favorited)
            assertEquals(11, feed.first().likes)
            assertFalse(feed.last().liked || feed.last().favorited)

            f.host.search.setQuery("query")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(420))
            f.gets.last { it.path == EndpointProvider.search() }.callback.onSuccess(page("post", "search-sibling"))
            val search = f.host.search.items.value
            assertTrue(search.all { it.following && !it.followPending })
            assertTrue(search.first().liked && search.first().favorited)
            assertEquals(11, search.first().likes)
        }
    }

    @Test fun laterRetainedLibraryPublicationsCannotOverwriteConfirmedState() {
        Fixture().use { f ->
            val clicked = post("post")
            f.action(clicked, ComposeAppCallbacks.ACTION_FAVORITE)
            f.posts.last().callback.onSuccess(JSONObject())
            f.action(clicked, ComposeAppCallbacks.ACTION_FOLLOW)
            f.posts.last().callback.onSuccess(JSONObject())
            val library = post("post")
            f.host.saved.favorites.state.value = ComposeSavedPostsState(listOf(library))
            val recent = post("post")
            f.host.readingCenter.state.value = ComposeReadingCenterState(recent, 0, 0, "")
            val offline = post("post")
            f.host.saved.watchLater.state.value = ComposeWatchLaterState(listOf(ComposeOfflineEntry(offline, 0, 0)))
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(listOf(library, recent, offline).all { it.favorited && it.following })
        }
    }

    @Test fun accountSwitchDropsOldCallbacksAndDetachesInteractiveFeedObjects() {
        Fixture().use { f ->
            f.host.setRoute("profile")
            val clicked = post("post")
            f.host.feed.items.value = listOf(clicked)
            f.action(clicked, ComposeAppCallbacks.ACTION_FOLLOW)
            val old = f.posts.single().callback
            f.login("77")
            f.host.invalidateProfile()
            val current = f.host.feed.items.value.single()
            assertNotSame(clicked, current)
            assertFalse(current.following || current.followPending)
            val messages = f.messages.toList()
            old.onSuccess(JSONObject())
            old.onError("old failure")
            assertFalse(current.following || current.followPending)
            assertEquals(messages, f.messages)
            assertNull(f.actions.interactions().following(current.authorId))
        }
    }

    @Test fun nativeAndComposeAuthorResolutionAgreeAndNeverFollowAPostId() {
        Fixture().use { f ->
            val link = JSONObject().put("id", "post-42").put("linkid", "post-42")
                .put("user", JSONObject().put("userid", "author-7"))
            val document = ComposeDetailDocument.parse(JSONObject().put("result",
                JSONObject().put("link", link)), post("post-42"), false)
            assertEquals("author-7", f.actions.authorUserId(link, link.optJSONObject("user")))
            assertEquals("author-7", document.item.authorId)
            assertEquals("", f.actions.authorUserId(JSONObject().put("id", "post-42"), null))
            f.action(document.item, ComposeAppCallbacks.ACTION_FOLLOW)
            assertEquals(OfficialRequestParams.follow("author-7"), f.posts.single().body)
        }
    }

    private class Fixture : Closeable {
        private val activityOwner = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        private val session = SessionStore(activity)
        private val api = ApiClient(session)
        private val cache = LocalCache(activity)
        private val writes = WriteActionClient(api, session, null, null)
        private val tokens = ThemeTokens.of(false, Color.BLACK, Color.GRAY)
        val messages = mutableListOf<String>()
        val nativeFeed = mutableListOf<FeedItem>()
        val requests = Shadow.extract<InteractionApiShadow>(api).requests
        val posts get() = requests.filter { it.body != null }
        val gets get() = requests.filter { it.body == null }
        val host = ComposeAppHost(activityOwner.get(), FrameLayout(activity), session, api, cache,
            activity.handler, ReadingTimeTracker(activity), null, tokens, false, 1f, 1f,
            ComposeCommentActionsTestFixtures.RecordingCallbacks(), ComposeToast {})
        val actions = PostActionController(activity, session, writes, cache, tokens, false,
            object : PostActionController.Host {
                override fun currentLinkId() = host.currentDetailItem()?.id.orEmpty()
                override fun currentLinkHsrc() = ""
                override fun currentDetailItem() = host.currentDetailItem()
                override fun feedItems() = nativeFeed
                override fun searchItems() = emptyList<FeedItem>()
                override fun feedChanged() = host.invalidateFeed()
                override fun showToast(message: String) { messages += message }
            })
        private val bridge = ComposeActivityBridge(activity)

        init {
            login("42")
            activity.session = session
            activity.api = api
            activity.localCache = cache
            activity.postActions = actions
            activity.composeAppHost = host
            host.bindInteractions(actions.interactions())
        }

        fun login(id: String) {
            val cookie = LegacyCookieCrypto(activity).encrypt("heybox_id=$id; pkey=offline-interaction-fixture")
            activity.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE)
                .edit().putString(SecureStrings.encryptedCookieKey(), cookie).commit()
            session.saveLogin(JSONObject().put("heyboxid", id))
            assertTrue(session.isLoggedIn())
        }

        fun action(item: FeedItem, action: Int) {
            // Test request ordering independently of the production two-second submit throttle.
            WriteActionClient::class.java.getDeclaredField("lastSubmitAt").apply {
                isAccessible = true
                setLong(writes, 0)
            }
            bridge.feedAction(item, action)
        }

        fun back(target: String) {
            assertEquals(target, host.navigation.backTarget())
            assertTrue(host.navigation.restoreSavedDetailIfNeeded(target))
            host.navigation.setRoute(target)
        }

        fun drainFeed() {
            val io = ComposeFeedController::class.java.getDeclaredField("io").apply { isAccessible = true }
                .get(host.feed) as ExecutorService
            io.submit {}.get(5, TimeUnit.SECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }

        override fun close() {
            host.close()
            bridge.close()
            writes.close()
            api.close()
            activityOwner.pause().stop().destroy()
        }
    }

    companion object {
        private fun rawPost(id: String, author: String = "author") = JSONObject()
            .put("linkid", id).put("title", id).put("text", "body-$id")
            .put("is_favour", 0).put("is_award_link", 0).put("link_award_num", 10)
            .put("user", JSONObject().put("userid", author).put("is_follow", 0))
        private fun post(id: String, author: String = "author") = FeedItem.from(rawPost(id, author))
        private fun document(item: FeedItem) = ComposeDetailDocument.parse(
            JSONObject().put("result", JSONObject().put("link", rawPost(item.id, item.authorId))), item, false)
        private fun page(vararg ids: String): JSONObject {
            val links = JSONArray()
            ids.forEach { links.put(rawPost(it)) }
            return JSONObject().put("result", JSONObject().put("links", links).put("lastval", "next"))
        }
    }
}

@Implements(value = ApiClient::class, isInAndroidSdk = false)
internal class InteractionApiShadow {
    data class Request(val path: String, val body: Map<String, String>?, val callback: ApiClient.Callback)
    val requests = mutableListOf<Request>()
    @Implementation fun get(path: String, params: Map<String, String>, callback: ApiClient.Callback) {
        requests += Request(path, null, callback)
    }
    @Implementation fun postForm(path: String, query: Map<String, String>, body: Map<String, String>,
        callback: ApiClient.Callback) {
        requests += Request(path, body, callback)
    }
}
