package com.ronan.heyboxlite

import android.app.Application
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class ComposeFeedCardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepareRenderer() { EmojiRenderer.clear() }
    @After fun clearRenderer() { EmojiRenderer.clear() }

    @Test fun actionAndThemeUpdatesDoNotReparseContentAndKeepLiveFeedItemCallbacks() {
        val preload = preload("Game One")
        val item = post("Title [inline-test]", "Summary [inline-test]", preload)
        val theme = mutableStateOf(composePreviewTheme(false))
        val favorite = mutableStateOf(false)
        val cached = mutableStateOf(false)
        val actions = mutableListOf<Pair<FeedItem, FeedAction>>()
        var opened: FeedItem? = null
        compose.setContent {
            HeyboxComposeTheme(theme.value) {
                ComposeFeedCard(item, theme.value, true, "current-user", { opened = it },
                    { post, action -> actions += post to action }, Modifier.testTag("card"),
                    favorite = favorite.value, cached = cached.value)
            }
        }
        compose.waitForIdle()
        val initialReads = preload.reads
        assertTrue("Initial rendering must parse the preload", initialReads > 0)
        val titleView = compose.runOnIdle { richView("Title [inline-test]") }
        val descriptionView = compose.runOnIdle { richView("Summary [inline-test]") }
        val renderedTitle = compose.runOnIdle { titleView.text }
        val renderedDescription = compose.runOnIdle { descriptionView.text }
        repeat(5) { index ->
            compose.runOnIdle {
                item.likes = 37 + index
                item.liked = true
                item.following = true
                item.followPending = true
                item.favorited = true
                favorite.value = true
                cached.value = true
                theme.value = theme.value.copy(panelElevated = Color(0xFF303030 + index))
            }
            compose.onNodeWithText(Format.commentLikeCount(37 + index)).assertIsDisplayed()
            compose.onNodeWithContentDescription("\u53d6\u6d88\u70b9\u8d5e").assertIsDisplayed()
            compose.onNodeWithContentDescription("\u53d6\u6d88\u5173\u6ce8").assertIsNotEnabled()
            compose.onNodeWithContentDescription("\u53d6\u6d88\u6536\u85cf").assertIsDisplayed()
            compose.onNodeWithContentDescription("\u79fb\u9664\u7f13\u5b58").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("Recomposition must not scan the same preload again", initialReads, preload.reads)
                assertSame(renderedTitle, titleView.text)
                assertSame(renderedDescription, descriptionView.text)
            }
        }
        compose.runOnIdle {
            item.followPending = false
            theme.value = theme.value.copy(panelElevated = Color.Black)
        }
        compose.onNodeWithContentDescription("\u53d6\u6d88\u70b9\u8d5e").performClick()
        compose.onNodeWithContentDescription("\u53d6\u6d88\u5173\u6ce8").performClick()
        compose.onNodeWithTag("card").performClick()
        compose.runOnIdle {
            assertEquals(listOf(FeedAction.LIKE, FeedAction.FOLLOW), actions.map { it.second })
            assertTrue(actions.all { it.first === item })
            assertSame(item, opened)
            assertEquals(initialReads, preload.reads)
        }
    }

    @Test fun replacingTheSamePostIdInvalidatesTextAndPreloadPresentation() {
        val firstPreload = preload("Game One")
        val nextPreload = preload("Game Two")
        val current = mutableStateOf(post("First title [inline-test]", "First summary [inline-test]", firstPreload))
        val theme = composePreviewTheme(false)
        compose.setContent {
            HeyboxComposeTheme(theme) {
                ComposeFeedCard(current.value, theme, true, "", {}, null)
            }
        }
        compose.onNodeWithText("Game One").assertIsDisplayed()
        val titleView = compose.runOnIdle { richView("First title [inline-test]") }
        val firstText = compose.runOnIdle { titleView.text }
        compose.runOnIdle { current.value = post("Second title [inline-test]", "Second summary [inline-test]", nextPreload) }
        compose.onNodeWithText("Game Two").assertIsDisplayed()
        compose.onNodeWithText("Game One").assertDoesNotExist()
        compose.runOnIdle {
            assertSame(titleView, richView("Second title [inline-test]"))
            assertNotSame(firstText, titleView.text)
            assertEquals("Second summary [inline-test]", richView("Second summary [inline-test]").text.toString())
            assertTrue(nextPreload.reads > 0)
        }
    }

    @Test fun ordinaryFeedTextUsesComposeWithoutCreatingNativeRichTextViews() {
        val current = mutableStateOf(post("Plain title", "Plain summary", JSONObject()))
        val theme = composePreviewTheme(false)
        compose.setContent {
            HeyboxComposeTheme(theme) {
                ComposeFeedCard(current.value, theme, true, "", {}, null)
            }
        }
        compose.onNodeWithText("Plain title").assertIsDisplayed()
        compose.onNodeWithText("Plain summary").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(richViews(compose.activity.window.decorView).isEmpty())
            current.value = post("Rich [inline-test]", "Still plain", JSONObject())
        }
        compose.onNodeWithText("Still plain").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Rich [inline-test]", richView("Rich [inline-test]").text.toString())
            assertEquals(1, richViews(compose.activity.window.decorView).size)
            current.value = post("Plain again", "Plain summary", JSONObject())
        }
        compose.onNodeWithText("Plain again").assertIsDisplayed()
        compose.runOnIdle { assertTrue(richViews(compose.activity.window.decorView).isEmpty()) }
    }

    @Test fun aPreloadWithNoGamesAlsoCachesItsEmptyResult() {
        val preload = CountingPreload().apply { put("text", "A normal post") }
        val item = post("Title", "Summary", preload)
        val theme = mutableStateOf(composePreviewTheme(false))
        compose.setContent {
            HeyboxComposeTheme(theme.value) {
                ComposeFeedCard(item, theme.value, true, "", {}, null)
            }
        }
        compose.waitForIdle()
        val initialReads = preload.reads
        assertTrue(initialReads > 0)
        repeat(5) { index ->
            compose.runOnIdle { theme.value = theme.value.copy(panel = Color(0xFF303030 + index)) }
            compose.runOnIdle { assertEquals(initialReads, preload.reads) }
        }
    }

    @Test fun androidViewUpdatesSkipEqualBindingsButHonorFontScaleAndThemeChanges() {
        val revision = mutableStateOf(0)
        val density = mutableStateOf(Density(1f, 1f))
        val dark = mutableStateOf(false)
        val color = mutableStateOf(Color.White)
        val lineHeight = mutableStateOf(18.sp)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides density.value) {
                ComposeRichText("Scaled text", dark.value, color.value, Color.Blue,
                    modifier = Modifier.testTag("text-${revision.value}"),
                    fontSize = 12.sp, lineHeight = lineHeight.value)
            }
        }
        compose.waitForIdle()
        val view = compose.runOnIdle { richView("Scaled text") }
        val initial = compose.runOnIdle { view.text }
        repeat(5) {
            compose.runOnIdle { revision.value++ }
            compose.runOnIdle { assertSame(initial, view.text) }
        }
        compose.runOnIdle {
            color.value = Color.Red
            lineHeight.value = 20.sp
        }
        compose.runOnIdle {
            assertSame(initial, view.text)
            assertEquals(android.graphics.Color.RED, view.currentTextColor)
        }
        compose.runOnIdle { density.value = Density(1f, 2f) }
        val scaled = compose.runOnIdle {
            assertEquals(24f, view.textSize, 0.01f)
            assertNotSame(initial, view.text)
            view.text
        }
        compose.runOnIdle { dark.value = true }
        compose.runOnIdle { assertNotSame(scaled, view.text) }
    }

    private fun richView(source: String): ComposeRichTextView =
        richViews(compose.activity.window.decorView).single { it.tag == source }

    private fun richViews(root: View): List<ComposeRichTextView> = when (root) {
        is ComposeRichTextView -> listOf(root)
        is ViewGroup -> (0 until root.childCount).flatMap { richViews(root.getChildAt(it)) }
        else -> emptyList()
    }

    private fun post(title: String, summary: String, preload: JSONObject) = FeedItem.from(
        JSONObject().put("linkid", "same-post-id").put("title", title).put("description", summary)
            .put("user", JSONObject().put("username", "Author").put("userid", "author-id"))
            .put("communityPostPreload", preload),
    )

    private fun preload(name: String) = CountingPreload().apply {
        put("game", JSONObject().put("appid", "42").put("name", name))
    }

    private class CountingPreload : JSONObject() {
        var reads = 0
        override fun opt(key: String): Any? {
            reads++
            return super.opt(key)
        }
    }
}
