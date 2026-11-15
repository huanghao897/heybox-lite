package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.URLEncoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeContentLinkNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var host: ComposeAppHost
    private var api: ApiClient? = null
    private val external = mutableListOf<String>()
    private val posts = mutableListOf<FeedItem>()
    private val navigated = mutableListOf<String>()

    @After fun close() {
        if (::host.isInitialized) compose.runOnUiThread { host.close() }
        api?.close()
    }

    @Test fun rootPostLinkUsesTheNormalDetailLoaderAndPreservesTheParent() {
        show("https://api.xiaoheihe.cn/bbs/app/api/web/share?link_id=987&h_src=comment")
        val entry = host.currentDetailEntryKey()
        clickLink()
        compose.runOnIdle {
            assertEquals(listOf("987"), posts.map { it.id })
            assertEquals("comment", posts.single().hsrc)
            assertTrue(external.isEmpty())
            assertNotEquals(entry, host.currentDetailEntryKey())
            backToParent(entry)
        }
    }

    @Test fun nestedReplyPostLinkKeepsItsPrefixOffsetsAndUsesInternalDetail() {
        show(protocol("openLink", "link", JSONObject().put("linkid", "Ab123")), reply = true)
        clickLink()
        compose.runOnIdle {
            assertEquals(listOf("Ab123"), posts.map { it.id })
            assertTrue(external.isEmpty())
        }
    }

    @Test fun legacyCommunityPostLinkOpensThePostInsteadOfItsSectionOrSharer() {
        show("https://www.xiaoheihe.cn/community/1067/list/987?heybox_id=42")
        clickLink()
        compose.runOnIdle {
            assertEquals(listOf("987"), posts.map { it.id })
            assertTrue(external.isEmpty())
        }
    }

    @Test fun officialMentionWrapperOpensTheProfileAndCanReturnToTheSameThread() {
        show("https://api.xiaoheihe.cn/open_inapp/#" + protocol("openUser", "user_id", "42"))
        val entry = host.currentDetailEntryKey()
        clickLink()
        compose.runOnIdle {
            assertEquals(listOf("user_space?user=42"), navigated)
            assertTrue(posts.isEmpty())
            assertTrue(external.isEmpty())
            backToParent(entry)
        }
    }

    @Test fun nestedReplyMentionWithoutTheWebWrapperStillOpensTheProfile() {
        show(protocol("openUser", "user_id", "43"), reply = true)
        clickLink()
        compose.runOnIdle {
            assertEquals(listOf("user_space?user=43"), navigated)
            assertTrue(external.isEmpty())
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundScreenBluePostLinkStillUsesInternalNavigation() {
        show("https://h5.xiaoheihe.cn/app/bbs/link/987", round = true)
        clickLink()
        compose.runOnIdle {
            assertEquals("987", posts.single().id)
            assertTrue(external.isEmpty())
        }
    }

    @Test fun lotteryAndOtherWebLinksKeepTheirExternalBrowserBehavior() {
        val url = "https://www.xiaoheihe.cn/lottery?link_id=987"
        show(url)
        clickLink()
        compose.runOnIdle {
            assertEquals(listOf(url), external)
            assertTrue(posts.isEmpty())
            assertTrue(navigated.isEmpty())
        }
    }

    private fun show(url: String, reply: Boolean = false, round: Boolean = false) {
        lateinit var services: ComposeServices
        compose.runOnUiThread {
            val activity = compose.activity
            val session = SessionStore(activity).apply {
                setNoImage(true)
                setRememberDetailScroll(false)
            }
            val client = ApiClient(session).also { api = it }
            val cache = LocalCache(activity)
            val handler = Handler(Looper.getMainLooper())
            val reading = ReadingTimeTracker(activity)
            val callbacks = object : ComposeAppCallbacks by
                ComposeCommentActionsTestFixtures.RecordingCallbacks() {
                override fun openDetail(item: FeedItem) {
                    posts += item
                    host.showDetailLoading(item)
                }
                override fun navigate(route: String) { navigated += route }
            }
            host = ComposeAppHost(activity, FrameLayout(activity), session, client, cache,
                handler, reading, null, ThemeTokens.of(true, 0, 0), round, 1f, 1f,
                callbacks, ComposeToast {})
            services = ComposeServices(activity, session, client, GameDetailClient(client), cache,
                handler, reading, null, composeThemeState(ThemeTokens.of(true, 0, 0), 1f, 1f, round),
                ComposeToast {})
            host.showDetailLoading(FeedItem.from(JSONObject().put("linkid", "parent")))
            val rich = "<a href='$url'>LinkTarget</a>"
            val root = ComposeCommentActionsTestFixtures.comment("root")
                .put("text", if (reply) "Parent body" else rich)
            val child = ComposeCommentActionsTestFixtures.comment("reply").put("text", rich)
            val group = if (reply) ComposeCommentActionsTestFixtures.thread(root, child)
                else ComposeCommentActionsTestFixtures.thread(root)
            host.navigation.detail.value = host.navigation.detail.value!!.copy(
                comments = listOf(group), loading = false, refreshPending = false)
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { external += uri }
            }) {
                // Keep the rendered route fixed to avoid unrelated profile API calls in this offline fixture.
                ComposeRouteScreen(host, "detail", services, active = true)
            }
        }
        compose.onNodeWithTag("compose-detail-list")
            .performScrollToNode(hasText("LinkTarget", substring = true))
    }

    private fun clickLink() {
        val node = compose.onNodeWithText("LinkTarget", substring = true, useUnmergedTree = true)
        val layout = node.layout()
        val index = layout.layoutInput.text.text.indexOf("LinkTarget") + 2
        val position = layout.getBoundingBox(index).center
        node.performTouchInput { click(position) }
        compose.waitForIdle()
    }

    private fun backToParent(entry: Long) {
        val target = host.navigation.backTarget()
        assertEquals("detail", target)
        assertTrue(host.navigation.restoreSavedDetailIfNeeded(target))
        assertEquals(entry, host.currentDetailEntryKey())
        assertEquals("root", ComposeCommentThreadData.from(host.navigation.detail.value!!.comments.single()).rootId)
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun protocol(type: String, field: String, value: Any): String = "heybox://" +
        URLEncoder.encode(JSONObject().put("protocol_type", type).put(field, value).toString(), "UTF-8")
}
