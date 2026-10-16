package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeRoundLayoutPagesUiTest : ComposeRoundLayoutHarness() {
    @Test fun squareReadingCenterKeepsTheLastDestinationTouchable() = verifyReadingCenter()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192ReadingCenterKeepsTheLastDestinationTouchable() = verifyReadingCenter()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227ReadingCenterKeepsTheLastDestinationTouchable() = verifyReadingCenter()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundReadingCenterUsesTheSameCircularGeometry() = verifyReadingCenter(forceRound = true)

    private fun verifyReadingCenter(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val post = FeedItem.from(JSONObject().put("linkid", "round-fixture").put("title", "Round post"))
        var historyClicks = 0
        showLayout(services) {
            ComposeReadingCenterScreen(
                ComposeReadingCenterState(post, 3, 0, "5 min", true), false, services,
                {}, {}, {}, { historyClicks++ }, {},
            )
        }
        assertBackAnchor()
        assertPaintedLabelInsideDisplay("\u9605\u8bfb\u4e2d\u5fc3")
        touchReachable("\u5386\u53f2\u8bb0\u5f55")
        compose.runOnIdle { assertEquals(1, historyClicks) }
    }

    @Test fun squareFavoriteFolderListKeepsItsLastCardTouchable() = verifyFavoriteFolders()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192FavoriteFolderListKeepsItsLastCardTouchable() = verifyFavoriteFolders()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227FavoriteFolderListKeepsItsLastCardTouchable() = verifyFavoriteFolders()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundFavoriteFolderListDoesNotUseSquareHitBounds() = verifyFavoriteFolders(forceRound = true)

    private fun verifyFavoriteFolders(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val folders = (1..8).map { ComposeFavoriteFolder(it.toString(), "Folder $it", it) }
        val opened = mutableListOf<ComposeFavoriteFolder>()
        showLayout(services) {
            ComposeFavoritesScreen(
                ComposeSavedPostsState(loaded = true), folders, ComposeFavoriteTab.FOLDERS,
                false, "", services, {}, {}, opened::add, { _, _ -> }, {}, {}, {}, rememberLazyListState(),
            )
        }
        assertBackAnchor()
        assertPaintedLabelInsideDisplay("\u6211\u7684\u6536\u85cf")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToIndex(folders.lastIndex)
        touchReachable("Folder 8")
        compose.runOnIdle { assertEquals(listOf(folders.last()), opened) }
    }

    @Test fun squareSearchHistoryKeepsClearAndLastQueryTouchable() = verifySearch()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192SearchHistoryKeepsClearAndLastQueryTouchable() = verifySearch()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227SearchHistoryKeepsClearAndLastQueryTouchable() = verifySearch()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundSearchHistoryUsesCircularTouchCenters() = verifySearch(forceRound = true)

    private fun verifySearch(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val queries = listOf("First", "Second", "Third", "Last")
        val selected = mutableListOf<String>()
        var clearClicks = 0
        showLayout(services) {
            ComposeSearchScreen("", emptyList(), false, true, services, selected::add, {}, {}, {},
                observeLoadMore = false, history = queries, onClearHistory = { clearClicks++ })
        }
        assertBackAnchor()
        assertPaintedLabelInsideDisplay("\u641c\u7d22")
        val clear = circleReach(compose.onNodeWithContentDescription("\u6e05\u7a7a\u641c\u7d22\u5386\u53f2"))
        assertTouchable(clear, "clear search history")
        clear.performTouchInput { click() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToIndex(queries.size)
        touchReachable("Last")
        compose.runOnIdle {
            assertEquals(listOf("Last"), selected)
            assertEquals(1, clearClicks)
        }
    }

    @Test fun squareLeaderboardLetsTheLastNameReachTheReadableArea() = verifyLeaderboard()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192LeaderboardLetsTheLastNameReachTheReadableArea() = verifyLeaderboard()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227LeaderboardLetsTheLastNameReachTheReadableArea() = verifyLeaderboard()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundLeaderboardKeepsItsLastNameInsideTheCircle() = verifyLeaderboard(forceRound = true)

    private fun verifyLeaderboard(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val entries = (1..8).map { CheckinLeaderboard.Entry(it, "R$it", it, "$it d", "R") }
        showLayout(services) {
            ComposeCheckinLeaderboardContent(ComposeCheckinLeaderboardState(
                data = CheckinLeaderboard.Data(entries, emptyList())), {}, {})
        }
        assertBackAnchor()
        circleReach("R8")
        assertPaintedLabelInsideDisplay("R8")
        assertPaintedLabelInsideDisplay("8 d")
        assertBackAnchor()
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundMembershipRedeemCanReachAndSubmitItsAction() {
        val services = layoutServices(forceRound = true)
        var submissions = 0
        showLayout(services) {
            ComposeMembershipRedeemScreen(ComposeMembershipUiState(redeemCode = "FIXTURE"),
                {}, {}, { submissions++ })
        }
        assertBackAnchor()
        touchReachable("\u786e\u8ba4\u5151\u6362")
        compose.runOnIdle { assertEquals(1, submissions) }
    }
}
