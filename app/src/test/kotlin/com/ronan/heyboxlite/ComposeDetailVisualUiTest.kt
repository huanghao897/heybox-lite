package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    shadows = [DetailVisualApiShadow::class, DetailVisualImageShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeDetailVisualUiTest : ComposeMembershipUiRegressionHarness() {
    @get:Rule(order = 3) val failures = ErrorCollector()
    private lateinit var api: ApiClient
    private lateinit var listState: LazyListState
    private lateinit var item: FeedItem
    private val actions = mutableListOf<ComposeDetailAction>()
    private val openedImages = mutableListOf<String>()
    private val openedUsers = mutableListOf<String>()
    private var backs = 0

    @After fun closeApi() {
        if (::api.isInitialized) api.close()
    }

    @Test fun square240DetailAndCommentsStayReachable() = verifyScreen()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227DetailAndCommentsStayInsideTheCircle() = verifyScreen(expectTwoRows = true)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextDetailAndCommentsDoNotOverflow() = verifyScreen(largeText = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightRound192DetailAndCommentsStayInsideTheCircle() =
        verifyScreen(lightMode = true, expectTwoRows = true)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun lightNarrowLargeTextDetailAndCommentsDoNotOverflow() =
        verifyScreen(largeText = true, lightMode = true)

    private fun verifyScreen(
        largeText: Boolean = false,
        lightMode: Boolean = false,
        expectTwoRows: Boolean = largeText,
    ) {
        val base = theme()
        val palette = if (lightMode) theme(largeLight = true).copy(
            uiScale = base.uiScale, textScale = base.textScale) else base
        val theme = palette.let { if (largeText) it.copy(textScale = 1.4f) else it }
        showDetail(theme, if (largeText) 1.3f else 1f)
        // Keep independent sections running so one geometry failure does not lose later screenshots.
        checkSection("detail-header") {
            assertLabelFits(DetailVisualFixture.TITLE)
            val author = compose.onNode(hasText("Ronan") and
                !hasAnyAncestor(hasTestTag("comment-root")), useUnmergedTree = true)
            assertLabelFits("Ronan", author)
            assertLabelFits("Lv.18")
            assertLabelFits(DetailVisualFixture.SECTION)
            tap(description("\u5173\u6ce8\u4f5c\u8005"))
            assertTrue(actions.single() is ComposeDetailAction.FollowAuthor)
            description("\u8fd4\u56de").assertDoesNotExist()
            assertEquals(0, backs)
        }
        checkSection("detail-inline") {
            scrollTo("body-0")
            assertInlineFits(DetailVisualFixture.BODY_TEXT, attachments = 2)
        }
        checkSection("detail-actions") { assertPostActions(theme, expectTwoRows) }
        checkSection("comments-sort") {
            assertSortOrder(latest = false, expected = listOf("root", "hot", "new"))
            assertSortOrder(latest = true, expected = listOf("root", "new", "hot"))
            scrollTo("comment-heading")
            assertLabelFits("\u8bc4\u8bba 3")
            assertLabelFits("\u70ed\u95e8")
            assertLabelFits("\u6700\u65b0")
        }
        checkSection("comments-metadata") { assertCommentMetadata(theme) }
        if (largeText) checkSection("comments-author-single-line") {
            scrollTo("comment-root")
            val author = compose.onNode(hasText("Ronan") and
                hasAnyAncestor(hasTestTag("comment-root")), useUnmergedTree = true)
            assertLabelFits("Ronan", author)
            assertEquals("Short root author must not wrap as Rona/n on the narrow screen",
                1, layout(author).lineCount)
        }
        checkSection("comments-inline") {
            scrollTo("comment-root")
            assertInlineFits(DetailVisualFixture.COMMENT_TEXT, attachments = 3)
        }
        checkSection("comments-image-replies") { assertReplyAndImageControls() }
    }

    private fun showDetail(theme: ComposeThemeState, fontScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val session = SessionStore(context).apply {
            setNoImage(false)
            setPlayGif(true)
            setRememberDetailScroll(false)
            setDoubleTapCommentReply(true)
        }
        api = ApiClient(session)
        DetailVisualApiShadow.paths.clear()
        DetailVisualImageShadow.animatedUrls.clear()
        EmojiStore.register(DetailVisualFixture.EMOJI, DetailVisualFixture.EMOJI_URL,
            DetailVisualFixture.EMOJI_URL)
        EmojiStore.load(api) {}
        lateinit var response: JSONObject
        api.get(EndpointProvider.linkTree(), mapOf("link_id" to "visual-detail"),
            object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) { response = body.getJSONObject("result") }
                override fun onError(message: String) { error(message) }
            })
        val link = response.getJSONObject("link")
        item = FeedItem.from(link)
        val comments = mutableStateOf(response.getJSONArray("comments").let { array ->
            List(array.length()) { array.getJSONObject(it) }
        })
        val replies = mutableStateOf(mapOf("root" to ComposeReplyLoadState(hasMore = true)))
        val content = listOf(RichContent.Block.text(RichContent.commentText(response.getString("body"))))
        listState = LazyListState()
        val services = ComposeServices(compose.activity, session, api, GameDetailClient(api),
            LocalCache(context), Handler(Looper.getMainLooper()), ReadingTimeTracker(context),
            null, theme, ComposeToast {})
        setScreen(theme, systemFontScale = fontScale) {
            ComposeDetailScreen(item, content, comments = comments.value, loading = false,
                services = services, onBack = { backs++ }, onOpenImage = openedImages::add,
                onOpenVideo = { error("No video in the detail visual fixture") },
                onCommentAction = { action ->
                    actions += action
                    if (action is ComposeDetailAction.OpenCommentImage) {
                        val gallery = composeCommentGallery(action.comment, action.url)
                        openedImages += gallery.urls[gallery.index]
                    }
                    if (action is ComposeDetailAction.LoadReplies) {
                        assertEquals("root", CommentData.commentId(action.root))
                        replies.value = mapOf("root" to ComposeReplyLoadState(loading = true))
                        api.get(EndpointProvider.subComments(), mapOf("root_id" to "root"),
                            object : ApiClient.Callback {
                                override fun onSuccess(body: JSONObject) {
                                    comments.value = comments.value.map { group ->
                                        if (group.getJSONArray("comment").getJSONObject(0)
                                                .getString("commentid") != "root") group
                                        else JSONObject(group.toString()).apply {
                                            val extra = body.getJSONArray("replies")
                                            repeat(extra.length()) { getJSONArray("comment").put(extra.getJSONObject(it)) }
                                        }
                                    }
                                    replies.value = mapOf("root" to ComposeReplyLoadState(hasMore = false))
                                }
                                override fun onError(message: String) {
                                    replies.value = mapOf("root" to ComposeReplyLoadState(error = message))
                                }
                            })
                    }
                }, onOpenUser = { id, _, _ -> openedUsers += id },
                listState = listState, link = link, replyStates = replies.value)
        }
        assertTrue(DetailVisualApiShadow.paths.contains(EndpointProvider.linkTree()))
    }

    private fun assertPostActions(theme: ComposeThemeState, expectTwoRows: Boolean) {
        scrollTo("post-actions")
        reach(tag("detail-actions"))
        val labels = listOf("\u70b9\u8d5e\u5e16\u5b50", "\u6536\u85cf", "\u7f13\u5b58\u5e16\u5b50", "\u8bc4\u8bba")
        val bounds = labels.map { windowBounds(description(it).fetchSemanticsNode()) }
        bounds.forEachIndexed { index, rect ->
            assertInDisplay(rect, labels[index], corners = true)
            assertTrue("Post action must remain compact: ${labels[index]}", rect.height <= 48f * theme.uiScale)
            bounds.drop(index + 1).forEach { other ->
                assertFalse("Post action controls overlap: $rect / $other",
                    minOf(rect.right, other.right) - maxOf(rect.left, other.left) > 0.5f &&
                        minOf(rect.bottom, other.bottom) - maxOf(rect.top, other.top) > 0.5f)
            }
        }
        val rows = bounds.chunked(if (expectTwoRows) 2 else 4)
        rows.forEach { row ->
            assertTrue("Post actions must use ${if (expectTwoRows) "two columns" else "one row"}: $bounds",
                row.maxOf { it.bottom } - row.minOf { it.top } <= 48f * theme.uiScale)
            row.zipWithNext().forEach { (left, right) ->
                assertTrue("Post actions must stay side by side within each row", left.right <= right.left + 0.5f)
                assertEquals("Post action row centers differ", left.center.y, right.center.y, 1f)
            }
        }
        if (expectTwoRows) {
            assertTrue("Narrow actions must use two distinct rows",
                rows.first().maxOf { it.bottom } <= rows.last().minOf { it.top } + 0.5f)
            rows.first().zip(rows.last()).forEach { (top, bottom) ->
                assertEquals("Narrow action columns must align", top.left, bottom.left, 1f)
                assertEquals("Narrow action column widths differ", top.right, bottom.right, 1f)
            }
        }
        capture(if (expectTwoRows) "detail-four-actions-2x2" else "detail-four-actions-row")
        val visibleLabels = listOf("128", "", "\u7f13\u5b58", "3")
        labels.zip(visibleLabels).forEach { (action, label) ->
            if (label.isNotBlank()) {
                assertLabelFits(label)
                reach(description(action))
                val control = windowBounds(description(action).fetchSemanticsNode())
                val iconBounds = windowBounds(compose.onNode(hasContentDescription(action), useUnmergedTree = true)
                    .fetchSemanticsNode())
                val labelBounds = windowBounds(text(label, unmerged = true).fetchSemanticsNode())
                assertEquals("Action icon and label must share a row: $action", iconBounds.center.y, labelBounds.center.y, 1f)
                assertTrue("Action icon overlaps its label: $action", iconBounds.right <= labelBounds.left + 0.5f)
                listOf(iconBounds, labelBounds).forEach { content ->
                    assertInDisplay(content, "action content $action", corners = true)
                    assertTrue("Action content is clipped by its control: $action; $content / $control",
                        content.left >= control.left - 0.5f && content.top >= control.top - 0.5f &&
                            content.right <= control.right + 0.5f && content.bottom <= control.bottom + 0.5f)
                }
            }
        }
        labels.forEach { tap(description(it)) }
        assertEquals(listOf(ComposeDetailAction.LikePost(item), ComposeDetailAction.FavoritePost(item),
            ComposeDetailAction.CachePost(item), ComposeDetailAction.WriteComment(item)),
            actions.filter { it is ComposeDetailAction.LikePost || it is ComposeDetailAction.FavoritePost ||
                it is ComposeDetailAction.CachePost || it is ComposeDetailAction.WriteComment })
    }

    private fun assertSortOrder(latest: Boolean, expected: List<String>) {
        scrollTo("comment-heading")
        tapReachable(if (latest) "\u6700\u65b0" else "\u70ed\u95e8")
        val indices = expected.map { id ->
            scrollTo("comment-$id")
            compose.runOnIdle { listState.layoutInfo.visibleItemsInfo.single { it.key == "comment-$id" }.index }
        }
        assertTrue("Comment sort must change the actual lazy item order: $expected / $indices",
            indices.zipWithNext().all { (first, second) -> first < second })
    }

    private fun assertCommentMetadata(theme: ComposeThemeState) {
        scrollTo("comment-root")
        val inRoot = hasAnyAncestor(hasTestTag("comment-root"))
        val name = compose.onNode(hasText("Ronan") and inRoot, useUnmergedTree = true)
        assertLabelFits("Ronan", name)
        val like = compose.onNode(hasContentDescription("\u70b9\u8d5e\u8bc4\u8bba") and inRoot)
        val likeBounds = windowBounds(like.fetchSemanticsNode())
        val rootBounds = windowBounds(tag("comment-root").fetchSemanticsNode())
        assertInDisplay(windowBounds(name.fetchSemanticsNode()), "comment author", corners = true)
        assertInDisplay(likeBounds, "comment like", corners = true)
        for (value in listOf("Ronan", "\u4f5c\u8005", "Lv.24")) {
            val label = compose.onNode(hasText(value) and inRoot, useUnmergedTree = true)
            val bounds = windowBounds(label.fetchSemanticsNode())
            assertTrue("Author metadata collides with the right-aligned like: $value",
                bounds.left >= rootBounds.left && bounds.right <= rootBounds.right + 0.5f &&
                    (bounds.right <= likeBounds.left + 0.5f || bounds.bottom <= likeBounds.top + 0.5f))
        }
        assertEquals("Like count should stay right aligned", rootBounds.right, likeBounds.right, 1f)
        assertTrue("Like control must stay compact", likeBounds.width <= 56f * theme.uiScale + 1f)
        capture("comments-author-level-like")
        listOf("\u7f6e\u9876", "\u4f5c\u8005", "Lv.24", "12", "Shanghai").forEach(::assertLabelFits)
        tap(like)
        assertEquals("root", (actions.last() as ComposeDetailAction.LikeComment).comment.getString("commentid"))
        val avatar = reach(description("Ronan\u7684\u5934\u50cf"))
        tap(avatar)
        assertTrue(openedUsers.isEmpty())
        avatar.performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(listOf("author-id"), openedUsers)
    }

    private fun assertReplyAndImageControls() {
        scrollTo("comment-root")
        val rootImage = compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("comment-root")) and
            !hasContentDescription("\u70b9\u8d5e\u8bc4\u8bba") and !hasContentDescription("Ronan\u7684\u5934\u50cf"))
        tap(rootImage)
        assertEquals("root", (actions.last() as ComposeDetailAction.OpenCommentImage).comment.getString("commentid"))
        assertEquals(DetailVisualFixture.ORIGINAL, openedImages.last())
        val replyImage = compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("comment-reply-1")))
        reach(replyImage)
        capture("comments-gif-control")
        assertLabelFits("GIF")
        tap(replyImage)
        assertEquals("reply-1", (actions.last() as ComposeDetailAction.OpenCommentImage).comment.getString("commentid"))
        assertEquals(DetailVisualFixture.GIF_ORIGINAL, openedImages.last())
        assertTrue(DetailVisualImageShadow.animatedUrls.contains(DetailVisualFixture.GIF_ORIGINAL))
        val relation = "Iris \u56de\u590d Mira：Reply body 1  ${Format.relativeTime(1)} · Hangzhou"
        assertLabelFits(relation)
        val reply = reach(text(relation))
        assertInDisplay(windowBounds(reply.fetchSemanticsNode()), "reply gesture", corners = true)
        reply.performTouchInput { doubleClick() }
        compose.waitForIdle()
        val emitted = actions.last() as ComposeDetailAction.Reply
        assertEquals("root", emitted.rootId)
        assertEquals("reply-1", emitted.comment.getString("commentid"))
        tapReachable("\u5c55\u5f00 5 \u6761\u56de\u590d")
        assertEquals(1, actions.filterIsInstance<ComposeDetailAction.LoadReplies>().size)
        assertTrue(DetailVisualApiShadow.paths.contains(EndpointProvider.subComments()))
        val expandedReply = "Iris \u56de\u590d Mira：Reply body 6  ${Format.relativeTime(6)} · Hangzhou"
        assertLabelFits(expandedReply)
        capture("comments-expanded-replies")
        tapReachable("\u6536\u8d77\u56de\u590d")
        text(expandedReply).assertDoesNotExist()
    }

    private fun assertInlineFits(value: String, attachments: Int) {
        val node = reach(text(value, unmerged = true))
        val result = layout(node)
        assertEquals(value, result.layoutInput.text.text)
        assertFalse("Rich text lost its last characters: $value", result.multiParagraph.didExceedMaxLines)
        assertEquals(value.length, result.getLineEnd(result.lineCount - 1))
        assertEquals("Inline emoji/game/CY attachments missing", attachments,
            result.placeholderRects.filterNotNull().size)
        assertTrue("Inline game link lost its link color",
            result.layoutInput.text.spanStyles.any { it.item.color == theme().link })
        val origin = node.fetchSemanticsNode().positionInWindow
        for (line in 0 until result.lineCount) {
            assertFalse(result.isLineEllipsized(line))
            val bounds = Rect(result.getLineLeft(line), result.getLineTop(line),
                result.getLineRight(line), result.getLineBottom(line))
            assertTrue("Rich text overflows its paragraph: $bounds",
                bounds.left >= -0.5f && bounds.right <= result.size.width + 0.5f &&
                    bounds.bottom <= result.size.height + 0.5f)
            assertInDisplay(bounds.translate(origin), "rich text line $line", corners = true)
        }
        result.placeholderRects.filterNotNull().forEach { bounds ->
            assertTrue("Inline attachment is clipped: $bounds", bounds.left >= 0 && bounds.top >= 0 &&
                bounds.right <= result.size.width && bounds.bottom <= result.size.height)
            assertInDisplay(bounds.translate(origin), "inline attachment", corners = true)
        }
        assertVisibleTextDoesNotOverlap()
    }

    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun scrollTo(key: String) {
        tag("compose-detail-list").performScrollToKey(key)
        compose.waitForIdle()
    }

    private fun tap(node: SemanticsNodeInteraction) {
        reach(node)
        assertInDisplay(windowBounds(node.fetchSemanticsNode()), "touch target", corners = true)
        node.performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun checkSection(step: String, assertions: () -> Unit) {
        failures.checkSucceeds { assertions() }
        failures.checkSucceeds { capture(step) }
    }

    private fun windowBounds(node: SemanticsNode) = Rect(node.positionInWindow.x, node.positionInWindow.y,
        node.positionInWindow.x + node.size.width, node.positionInWindow.y + node.size.height)
}

/** Only the I/O boundaries are shadowed; all detail and comment composables remain real. */
@Implements(value = ApiClient::class, isInAndroidSdk = false)
internal class DetailVisualApiShadow {
    @Implementation fun get(path: String, extra: Map<String, String>, callback: ApiClient.Callback) {
        paths += path
        val response = when (path) {
            EndpointProvider.linkTree() -> DetailVisualFixture.detail()
            EndpointProvider.emojis() -> JSONObject().put("result", JSONObject().put("emojis", JSONArray()
                .put(JSONObject().put("code", DetailVisualFixture.EMOJI).put("url", DetailVisualFixture.EMOJI_URL))))
            EndpointProvider.subComments() -> {
                assertEquals("root", extra["root_id"])
                JSONObject().put("replies", JSONArray().apply { (2..6).forEach { put(DetailVisualFixture.reply(it)) } })
            }
            else -> error("Unexpected API request in offline visual test: $path")
        }
        callback.onSuccess(response)
    }

    @Implementation fun getSigned(path: String, extra: Map<String, String>,
        algorithm: HeyboxSigner.Algorithm, profile: ApiClient.RequestProfile, callback: ApiClient.Callback) =
        get(path, extra, callback)

    companion object { val paths = mutableListOf<String>() }
}

@Implements(value = ImageLoader::class, isInAndroidSdk = false)
internal class DetailVisualImageShadow {
    companion object {
        val animatedUrls = mutableListOf<String>()

        @JvmStatic @Implementation fun cachedThumbnail(url: String, targetPx: Int): Bitmap = bitmap(url)

        @JvmStatic @Implementation fun loadCancellable(url: String, targetPx: Int,
            callback: ImageLoader.Callback): ImageLoadRequest =
            ImageLoadRequest.deliver(Handler(Looper.getMainLooper()), bitmap(url), callback)

        @JvmStatic @Implementation fun cachedExactOriginal(url: String): Bitmap = bitmap(url)

        @JvmStatic @Implementation fun loadExactOriginalCancellable(url: String, targetPx: Int,
            callback: ImageLoader.Callback): ImageLoadRequest =
            ImageLoadRequest.deliver(Handler(Looper.getMainLooper()), bitmap(url), callback)

        @JvmStatic @Implementation fun intoMeasuredStable(view: ImageView, url: String,
            targetPx: Int, callback: ImageLoader.IntoBitmapCallback) {
            val bitmap = bitmap(url)
            view.setImageBitmap(bitmap)
            callback.onComplete(true, bitmap)
        }

        @JvmStatic @Implementation fun intoGif(view: ImageView, url: String) {
            animatedUrls += url
            view.setImageBitmap(bitmap(url))
        }

        // Synthetic test swatches only; the .invalid host never supplies real media.
        private fun bitmap(url: String): Bitmap {
            require(url.startsWith(DetailVisualFixture.HOST)) { "Unexpected visual image URL: $url" }
            return Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (url.contains("emoji")) 0xFFE56AB3.toInt() else 0xFF427E72.toInt())
                Canvas(this).drawRect(8f, 6f, 40f, 26f, Paint().apply { color = 0xFFD6DDD7.toInt() })
            }
        }
    }
}

private object DetailVisualFixture {
    const val HOST = "https://detail-ui.invalid/"
    const val TITLE = "\u8155\u4e0a\u8bc4\u6d4b"
    const val SECTION = "\u6e38\u620f"
    const val EMOJI = "cube_detail_wave"
    const val EMOJI_URL = HOST + "emoji.png"
    const val ORIGINAL = HOST + "original.jpg?original=1"
    const val GIF_ORIGINAL = HOST + "original.gif?original=1"
    const val BODY_TEXT = "\u6b63\u6587 [cube_detail_wave] \uFFFCGame"
    const val COMMENT_TEXT = "Cy \u8bc4\u8bba [cube_detail_wave] \uFFFCGame"

    private fun mixed(prefix: String) = "$prefix [$EMOJI] " +
        "<a data-link-type=\"game\" data-game-id=\"620\">Game</a>"

    private fun user(name: String, id: String, level: Int = 0) = JSONObject()
        .put("username", name).put("userid", id).put("avatar", HOST + "avatar-$id.png").put("level", level)

    fun reply(index: Int) = JSONObject().put("commentid", "reply-$index").put("create_at", index)
        .put("ip_location", "Hangzhou")
        .put("text", "Reply body $index").put("replyid", "other-reply")
        .put("replyuser", JSONObject().put("username", "Mira")).put("user", user("Iris", "reply-user-$index"))

    fun detail(): JSONObject {
        val root = JSONObject().put("commentid", "root").put("child_num", 6).put("top_comment", 1)
            .put("comment_award_num", 12).put("ip_location", "Shanghai").put("is_cy", 1)
            .put("text", mixed("\u8bc4\u8bba")).put("user", user("Ronan", "author-id", 24))
            .put("imgs", JSONArray().put(JSONObject().put("thumb_url", HOST + "preview.jpg").put("original", ORIGINAL)))
        val reply = reply(1).put("imgs", JSONArray().put(JSONObject()
            .put("thumb_url", HOST + "gif-preview.jpg").put("original", GIF_ORIGINAL)))
        fun group(id: String, likes: Int, time: Int) = JSONObject().put("comment", JSONArray()
            .put(JSONObject().put("commentid", id).put("text", "$id comment").put("create_at", time)
                .put("comment_award_num", likes).put("user", user(id, "$id-user"))))
        val link = JSONObject().put("linkid", "visual-detail").put("title", TITLE)
            .put("user", user("Ronan", "author-id", 18)).put("comment_num", 3).put("link_award_num", 128)
            .put("topics", JSONArray().put(JSONObject().put("name", SECTION)))
        return JSONObject().put("result", JSONObject().put("link", link).put("body", mixed("\u6b63\u6587"))
            .put("comments", JSONArray().put(JSONObject().put("comment", JSONArray().put(root).put(reply)))
                .put(group("hot", 87, 10)).put(group("new", 2, 20))))
    }
}
