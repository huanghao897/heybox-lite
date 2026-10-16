package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    shadows = [DetailGameCardApiShadow::class, DetailGameCardImageShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeDetailGameCardUiTest : ComposeMembershipUiRegressionHarness() {
    private var api: ApiClient? = null

    @Before fun clearRequests() {
        DetailGameCardApiShadow.requests.clear()
        DetailGameCardImageShadow.urls.clear()
    }

    @After fun closeApi() { api?.close() }

    @Test fun legacyDetailPricesIncludeCurrentOriginalDiscountAndMetadata() {
        showCard(DetailGameCardFixture.discounted())
        assertPricesFit()
        assertLabelFits("PC · 评分 9.2 · 关注 12万")
        assertEquals(TextDecoration.LineThrough, layout("detail-game-original-1077510")
            .layoutInput.style.textDecoration)
        assertTrue(DetailGameCardApiShadow.requests.isEmpty())
    }

    @Test fun absentPriceNeverInventsFreeZeroOrCurrency() {
        showCard(DetailGameCardFixture.base())
        tag("detail-game-prices-1077510").assertDoesNotExist()
        listOf("免费", "0", "¥0", "¥", "正在获取游戏信息").forEach { text(it).assertDoesNotExist() }
        text("星空").assertIsDisplayed()
    }

    @Test fun explicitFreeGameKeepsItsOriginalPriceAndDiscount() {
        showCard(DetailGameCardFixture.priced("0", "19.9", "100").put("is_free", true))
        assertLabelFits("免费")
        assertLabelFits("19.9")
        assertLabelFits("-100%")
        text("0").assertDoesNotExist()
    }

    @Test fun equalOriginalAndZeroDiscountDoNotDuplicateTheCurrentPrice() {
        showCard(DetailGameCardFixture.priced("19.9", "19.9", "0"))
        assertLabelFits("19.9")
        tag("detail-game-original-1077510").assertDoesNotExist()
        tag("detail-game-discount-1077510").assertDoesNotExist()
    }

    @Test fun originalOnlyRemainsStruckThroughWithoutInventingACurrentPrice() {
        showCard(DetailGameCardFixture.base().put("price", JSONObject().put("initial", "298")))
        assertLabelFits("298")
        tag("detail-game-current-1077510").assertDoesNotExist()
        assertEquals(TextDecoration.LineThrough, layout("detail-game-original-1077510")
            .layoutInput.style.textDecoration)
    }

    @Test fun missingPricesAreEnrichedOnceWithoutRecomposingTheArticleParent() {
        val theme = theme()
        val services = services(theme)
        val media = mutableStateOf(ComposeMediaSettings(enabled = false))
        val block = RichContent.Block.gameCard(DetailGameCardFixture.base(), DetailGameCardFixture.ID)
        var articleCompositions = 0
        setScreen(theme) {
            CardContainer {
                SideEffect { articleCompositions++ }
                ComposeDetailGameCard(block, media.value, services)
            }
        }
        text("正在获取游戏信息").assertIsDisplayed()
        assertEquals(1, DetailGameCardApiShadow.requests.size)
        val before = articleCompositions
        finish(0, DetailGameCardFixture.discounted())
        assertPricesFit()
        text("正在获取游戏信息").assertDoesNotExist()
        assertEquals("Game-card completion must not invalidate the article parent", before, articleCompositions)
        compose.runOnIdle { media.value = media.value.copy(gameCardNoImage = true) }
        compose.waitForIdle()
        assertEquals(1, DetailGameCardApiShadow.requests.size)
    }

    @Test fun reenteringTheCardUsesTheExistingCacheAndDoesNotRemainFetching() {
        val theme = theme()
        val services = services(theme)
        val visible = mutableStateOf(true)
        val block = RichContent.Block.gameCard(JSONObject().put("appid", DetailGameCardFixture.ID),
            DetailGameCardFixture.ID)
        setScreen(theme) {
            CardContainer {
                if (visible.value) ComposeDetailGameCard(block, ComposeMediaSettings(enabled = false), services)
            }
        }
        finish(0, DetailGameCardFixture.discounted())
        assertPricesFit()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        tag("detail-game-card-1077510").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        assertPricesFit()
        text("正在获取游戏信息").assertDoesNotExist()
        assertEquals("A cached re-entry must not issue a second official API request",
            1, DetailGameCardApiShadow.requests.size)
    }

    @Test fun reenteringBeforeCompletionSharesTheRequestAndBindsItsPricesToTheNewCard() {
        val theme = theme()
        val services = services(theme)
        val visible = mutableStateOf(true)
        val block = RichContent.Block.gameCard(DetailGameCardFixture.base(), DetailGameCardFixture.ID)
        setScreen(theme) {
            CardContainer {
                if (visible.value) ComposeDetailGameCard(block, ComposeMediaSettings(enabled = false), services)
            }
        }
        text("正在获取游戏信息").assertIsDisplayed()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        text("正在获取游戏信息").assertIsDisplayed()
        assertEquals("A new composition must share the pending request", 1, DetailGameCardApiShadow.requests.size)
        finish(0, DetailGameCardFixture.discounted())
        assertPricesFit()
        text("正在获取游戏信息").assertDoesNotExist()
        assertEquals(1, DetailGameCardApiShadow.requests.size)
    }

    @Test fun staleCallbackCannotBindThePreviousGamesPriceToTheNextCard() {
        val theme = theme()
        val services = services(theme)
        val current = mutableStateOf(RichContent.Block.gameCard(
            JSONObject().put("appid", DetailGameCardFixture.ID), DetailGameCardFixture.ID))
        setScreen(theme) {
            CardContainer { ComposeDetailGameCard(current.value, ComposeMediaSettings(enabled = false), services) }
        }
        compose.runOnIdle {
            current.value = RichContent.Block.gameCard(JSONObject().put("appid", "42"), "42")
        }
        compose.waitForIdle()
        assertEquals(2, DetailGameCardApiShadow.requests.size)
        finish(0, DetailGameCardFixture.discounted())
        tag("detail-game-prices-1077510").assertDoesNotExist()
        text("59.6").assertDoesNotExist()
        text("正在获取游戏信息").assertIsDisplayed()
        finish(1, DetailGameCardFixture.priced("7.5", "10", "25").put("appid", "42"))
        assertLabelFits("7.5")
        tag("detail-game-prices-42").assertIsDisplayed()
        text("正在获取游戏信息").assertDoesNotExist()
    }

    @Test fun failedLoadClearsFetchingWithoutAutomaticRetryOrFakePrice() {
        val theme = theme()
        val services = services(theme)
        val block = RichContent.Block.gameCard(JSONObject().put("appid", DetailGameCardFixture.ID),
            DetailGameCardFixture.ID)
        setScreen(theme) {
            CardContainer { ComposeDetailGameCard(block, ComposeMediaSettings(enabled = false), services) }
        }
        compose.runOnIdle { DetailGameCardApiShadow.requests.single().callback.onError("offline fixture") }
        compose.waitForIdle()
        text("正在获取游戏信息").assertDoesNotExist()
        text("游戏信息暂不可用").assertIsDisplayed()
        tag("detail-game-prices-1077510").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(1, DetailGameCardApiShadow.requests.size)
    }

    @Test fun responseWithoutGameDetailsKeepsTheEmbeddedMetadataAndStopsFetching() {
        val theme = theme()
        val services = services(theme)
        val block = RichContent.Block.gameCard(DetailGameCardFixture.base(), DetailGameCardFixture.ID)
        setScreen(theme) {
            CardContainer { ComposeDetailGameCard(block, ComposeMediaSettings(enabled = false), services) }
        }
        finish(0, JSONObject().put("appid", DetailGameCardFixture.ID))
        text("星空").assertIsDisplayed()
        assertLabelFits("PC · 评分 9.2 · 关注 12万")
        text("正在获取游戏信息").assertDoesNotExist()
        tag("detail-game-prices-1077510").assertDoesNotExist()
        assertEquals(1, DetailGameCardApiShadow.requests.size)
    }

    @Test fun nameOnlyDetailsPreserveEmbeddedFieldsAlsoOnCachedReentry() {
        val theme = theme()
        val services = services(theme)
        val visible = mutableStateOf(true)
        val block = RichContent.Block.gameCard(DetailGameCardFixture.base(), DetailGameCardFixture.ID)
        setScreen(theme) {
            CardContainer {
                if (visible.value) ComposeDetailGameCard(block, ComposeMediaSettings(), services)
            }
        }
        finish(0, JSONObject().put("appid", DetailGameCardFixture.ID).put("name", "Updated"))
        assertLabelFits("PC · 评分 9.2 · 关注 12万")
        description("游戏封面").assertIsDisplayed()
        text("游戏信息暂不可用").assertDoesNotExist()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        text("Updated").assertIsDisplayed()
        assertLabelFits("PC · 评分 9.2 · 关注 12万")
        description("游戏封面").assertIsDisplayed()
        text("正在获取游戏信息").assertDoesNotExist()
        text("游戏信息暂不可用").assertDoesNotExist()
        tag("detail-game-prices-1077510").assertDoesNotExist()
        assertEquals(1, DetailGameCardApiShadow.requests.size)
    }

    @Test fun priceOnlyDetailsEnrichWithoutErasingEmbeddedNameOrCover() {
        val theme = theme()
        val services = services(theme)
        val block = RichContent.Block.gameCard(DetailGameCardFixture.base(), DetailGameCardFixture.ID)
        setScreen(theme) {
            CardContainer { ComposeDetailGameCard(block, ComposeMediaSettings(), services) }
        }
        finish(0, JSONObject().put("appid", DetailGameCardFixture.ID).put("price",
            JSONObject().put("current", "59.6").put("initial", "298").put("discount", "80")))
        assertPricesFit()
        text("星空").assertIsDisplayed()
        assertLabelFits("PC · 评分 9.2 · 关注 12万")
        description("游戏封面").assertIsDisplayed()
    }

    @Test fun gameCardNoImageKeepsAllPricesAndSkipsCoverLoading() {
        showCard(DetailGameCardFixture.discounted(), ComposeMediaSettings(gameCardNoImage = true))
        assertPricesFit()
        description("游戏封面").assertDoesNotExist()
        assertTrue(DetailGameCardImageShadow.urls.isEmpty())
    }

    @Test fun squareCoverAndPricesFitWithoutClipping() = verifyFit()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundCoverAndPricesFitWithoutClipping() = verifyFit()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextWrapsPriceFieldsWithoutLosingAmounts() = verifyFit(largeText = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightRoundLargeTextNoImageKeepsEveryPriceReachable() = verifyFit(largeText = true, noImage = true)

    private fun verifyFit(largeText: Boolean = false, noImage: Boolean = false) {
        val theme = if (largeText) theme(largeLight = true).copy(uiScale = theme().uiScale) else theme()
        showCard(DetailGameCardFixture.discounted(), ComposeMediaSettings(gameCardNoImage = noImage),
            theme, systemFontScale = if (largeText) 1.3f else 1f)
        assertPricesFit()
        if (!noImage) {
            description("游戏封面").assertIsDisplayed()
            assertFalse(DetailGameCardImageShadow.urls.isEmpty())
        }
        for (name in listOf("current", "original", "discount")) {
            val node = reach(tag("detail-game-$name-1077510")).fetchSemanticsNode()
            assertInDisplay(node.boundsInRoot, "game $name", corners = true)
        }
        capture("game-card-prices-fit")
    }

    private fun showCard(
        game: JSONObject,
        media: ComposeMediaSettings = ComposeMediaSettings(enabled = false),
        theme: ComposeThemeState = theme(),
        systemFontScale: Float = 1f,
    ) {
        val block = RichContent.Block.gameCard(game, DetailGameCardFixture.ID)
        setScreen(theme, systemFontScale) {
            CardContainer { ComposeDetailGameCard(block, media, null) }
        }
    }

    @Composable private fun CardContainer(content: @Composable () -> Unit) {
        val theme = LocalHeyboxTheme.current
        Box(Modifier.fillMaxSize().background(theme.background), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (theme.roundScreen) 20.dp else 12.dp)
                .verticalScroll(rememberScrollState())) { content() }
        }
    }

    private fun assertPricesFit() {
        listOf("59.6", "298", "-80%").forEach(::assertLabelFits)
    }

    private fun layout(testTag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        tag(testTag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun services(theme: ComposeThemeState): ComposeServices {
        val activity = compose.activity
        val session = SessionStore(activity)
        val client = ApiClient(session).also { api = it }
        return ComposeServices(activity, session, client, GameDetailClient(client),
            LocalCache(activity), Handler(Looper.getMainLooper()), ReadingTimeTracker(activity),
            null, theme, ComposeToast {})
    }

    private fun finish(index: Int, game: JSONObject) {
        compose.runOnIdle {
            DetailGameCardApiShadow.requests[index].callback.onSuccess(JSONObject().put("result",
                JSONObject().put("base_infos", JSONArray().put(game))))
        }
        compose.waitForIdle()
    }
}

/** Uses the existing GameCardDataTest detail fields and GameDetailClient's actual batch envelope. */
private object DetailGameCardFixture {
    const val ID = "1077510"
    fun base(): JSONObject = JSONObject().put("appid", ID).put("name", "星空")
        .put("image", "https://game-card-ui.invalid/header.jpg")
        .put("platforms", JSONArray().put("steam")).put("score", "9.2").put("follow_num", "12万")
    fun priced(current: String, initial: String, discount: String): JSONObject = base()
        .put("price", JSONObject().put("current", current).put("initial", initial).put("discount", discount))
    fun discounted(): JSONObject = priced("59.6", "298", "80")
}

@Implements(value = ApiClient::class, isInAndroidSdk = false)
internal class DetailGameCardApiShadow {
    @Implementation fun getSigned(path: String, extra: Map<String, String>,
        algorithm: HeyboxSigner.Algorithm, profile: ApiClient.RequestProfile, callback: ApiClient.Callback) {
        assertEquals("/game/get_game_infos/", path)
        assertEquals("link", extra["from"])
        assertEquals(HeyboxSigner.Algorithm.LEGACY, algorithm)
        assertEquals(ApiClient.RequestProfile.WEB, profile)
        requests += Request(extra.getValue("appids"), callback)
    }

    internal data class Request(val appIds: String, val callback: ApiClient.Callback)
    companion object { val requests = mutableListOf<Request>() }
}

@Implements(value = ImageLoader::class, isInAndroidSdk = false)
internal class DetailGameCardImageShadow {
    companion object {
        val urls = mutableListOf<String>()
        @JvmStatic @Implementation fun cachedThumbnail(url: String, targetPx: Int): Bitmap {
            require(url == "https://game-card-ui.invalid/header.jpg")
            urls += url
            return Bitmap.createBitmap(52, 52, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4B6E67.toInt()) }
        }
    }
}
