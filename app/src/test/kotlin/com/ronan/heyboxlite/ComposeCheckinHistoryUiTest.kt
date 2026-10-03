package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeCheckinHistoryUiTest : ComposeMembershipUiRegressionHarness() {
    private lateinit var state: MutableState<ComposeCheckinUiState>
    private val opened = mutableListOf<CheckinHistory.Entry>()
    private var retries = 0
    private var backs = 0
    private var detailBacks = 0

    @Test fun archivedHistoryDisplaysFinishedTimeActualRewardsAndItsLastFailedRow() {
        show()
        listOf("\u7b7e\u5230\u6210\u529f", "09-29 08:03", "\u76d2\u5e01 +17 \u00b7 \u7ecf\u9a8c +93", "\u5b8c\u6210 2 \u9879\u4efb\u52a1",
            "\u7b7e\u5230\u5931\u8d25", "09-30 23:51").forEach(::assertLabelFits)
        text("09-29 07:55").assertDoesNotExist()
        val last = text("09-30 23:51").fetchSemanticsNode()
        val labels = last.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertFalse("A failed historical row must not inherit the previous row's rewards",
            labels.any { "\u76d2\u5e01" in it || "\u7ecf\u9a8c" in it || "\u5b8c\u6210 2" in it })
        tapReachable("09-30 23:51")
        compose.runOnIdle {
            assertEquals(listOf(4002L), opened.map { it.id })
            assertSame(requireNotNull(state.value.history).entries.last(), opened.single())
        }
        text("\u7b7e\u5230\u8be6\u60c5").assertExists()
        text("\u7b7e\u5230\u5931\u8d25").assertExists()
        text("Archived failure; no rewards supplied").assertExists()
        text("\u76d2\u5e01 +17 \u00b7 \u7ecf\u9a8c +93").assertDoesNotExist()
    }

    @Test fun unknownResultAndMissingTimeDoNotAcquireRewardsOrCompletedTasks() {
        val unknown = CheckinHistory.parse(JSONObject().put("items", JSONArray()
            .put(JSONObject().put("id", 9999).put("status", "provider_unknown").put("started_at", ""))))
        show(ComposeCheckinUiState(history = unknown))
        text("\u6682\u65e0\u7ed3\u679c").assertExists()
        text("\u65f6\u95f4\u672a\u77e5").assertExists()
        text("\u7b7e\u5230\u6210\u529f").assertDoesNotExist()
        compose.onNodeWithText("\u76d2\u5e01", substring = true).assertDoesNotExist()
        compose.onNodeWithText("\u7ecf\u9a8c", substring = true).assertDoesNotExist()
        compose.onNodeWithText("\u5b8c\u6210", substring = true).assertDoesNotExist()
        tapReachable("\u65f6\u95f4\u672a\u77e5")
        compose.onAllNodesWithText("\u672a\u63d0\u4f9b\u7ed3\u679c", useUnmergedTree = true).assertCountEquals(4)
        text("\u7b7e\u5230\u6210\u529f").assertDoesNotExist()
    }

    @Test fun loadingEmptyAndFailedHistoryHaveDistinctStatesAndRetryNeverFabricatesEntries() {
        show(ComposeCheckinUiState(historyLoading = true))
        text("\u6b63\u5728\u540c\u6b65\u7b7e\u5230\u8bb0\u5f55").assertExists()
        text("\u6682\u65e0\u7b7e\u5230\u8bb0\u5f55").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(historyLoading = false, history = MembershipUiFixtures.emptyHistory())
        }
        text("\u6682\u65e0\u7b7e\u5230\u8bb0\u5f55").assertExists()
        compose.runOnIdle { state.value = state.value.copy(historyError = "Offline history failed") }
        text("\u6682\u65e0\u7b7e\u5230\u8bb0\u5f55").assertDoesNotExist()
        assertLabelFits("Offline history failed")
        tapReachable("\u91cd\u65b0\u8bfb\u53d6")
        compose.runOnIdle {
            assertEquals(1, retries)
            assertTrue(requireNotNull(state.value.history).entries.isEmpty())
            assertTrue(opened.isEmpty())
        }
    }

    @Test fun aRefreshErrorKeepsOldRowsAndTheirIdentities() {
        show(ComposeCheckinUiState(history = MembershipUiFixtures.history(),
            historyLoading = true, historyError = "Offline refresh failed"))
        text("\u7b7e\u5230\u6210\u529f").assertExists()
        text("\u7b7e\u5230\u5931\u8d25").assertExists()
        text("\u6b63\u5728\u540c\u6b65\u7b7e\u5230\u8bb0\u5f55").assertDoesNotExist()
        assertLabelFits("Offline refresh failed")
        tapReachable("\u91cd\u65b0\u8bfb\u53d6")
        tapReachable("09-29 08:03")
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(listOf(4001L), opened.map { it.id })
            assertEquals(2, requireNotNull(state.value.history).entries.size)
            assertEquals("Offline refresh failed", state.value.historyError)
        }
    }

    @Test fun archivedDetailShowsMixedTaskResultsOnceAndKeepsTheServerSummaryAndExtraFields() {
        val history = MembershipUiFixtures.history()
        show(ComposeCheckinUiState(history = history, selectedHistoryEntry = history.entries.first()))
        listOf("\u4efb\u52a1\u5b8c\u6210\u60c5\u51b5", "\u57fa\u7840\u7b7e\u5230", "\u5206\u4eab\u5e16\u5b50", "\u5206\u4eab\u6e38\u620f", "\u5206\u4eab\u8bc4\u4ef7",
            "Archived server summary; no request was made",
            "Server detail \u00b7 Archived final field").forEach(::assertLabelFits)
        text("\u5b8c\u6210 2 \u9879\u4efb\u52a1").assertExists()
        compose.onAllNodesWithText("\u672a\u63d0\u4f9b\u7ed3\u679c", useUnmergedTree = true).assertCountEquals(1)
        text("\u5206\u4eab\u5e16\u5b50 \u00b7 \u5df2\u5b8c\u6210").assertDoesNotExist()
        text("\u5206\u4eab\u6e38\u620f\u8be6\u60c5 \u00b7 \u5931\u8d25").assertDoesNotExist()
        assertBackReachable()
        text("\u7b7e\u5230\u8bb0\u5f55").assertExists()
        compose.runOnIdle {
            assertEquals(1, detailBacks)
            assertTrue(opened.isEmpty())
            assertNull(state.value.selectedHistoryEntry)
        }
    }

    @Test fun failedDetailDoesNotInventFourSuccessfulTaskResults() {
        val history = MembershipUiFixtures.history()
        show(ComposeCheckinUiState(history = history, selectedHistoryEntry = history.entries.last()))
        text("\u7b7e\u5230\u5931\u8d25").assertExists()
        compose.onAllNodesWithText("\u672a\u63d0\u4f9b\u7ed3\u679c", useUnmergedTree = true).assertCountEquals(4)
        compose.onNodeWithText("\u5b8c\u6210 ", substring = true).assertDoesNotExist()
        text("\u7b7e\u5230\u6210\u529f").assertDoesNotExist()
        assertLabelFits("Server detail \u00b7 Archived failure field")
        capture("failed-detail-last-field")
    }

    @Test fun missingDetailHasAReachableBackAndNoInventedTasks() {
        setScreen { ComposeCheckinHistoryDetailScreen(null) { backs++ } }
        text("\u8bb0\u5f55\u6682\u4e0d\u53ef\u7528").assertExists()
        text("\u57fa\u7840\u7b7e\u5230").assertDoesNotExist()
        text("\u7b7e\u5230\u6210\u529f").assertDoesNotExist()
        assertBackReachable()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun squareHistoryLastEntryAndDetailAreUsable() = verifyLayout()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227HistoryLastEntryAndDetailAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192HistoryLastEntryAndDetailAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextLightHistoryAndDetailDoNotOverlap() = verifyLayout(largeLight = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundLargeTextLightHistoryAndDetailKeepTheirLastItemsReachable() = verifyLayout(largeLight = true)

    private fun verifyLayout(largeLight: Boolean = false) {
        show(largeLight = largeLight)
        assertLabelFits("09-29 08:03")
        assertLabelFits("\u76d2\u5e01 +17 \u00b7 \u7ecf\u9a8c +93")
        capture("archived-first-row")
        assertLabelFits("\u7b7e\u5230\u5931\u8d25")
        tapReachable("09-30 23:51")
        capture("archived-failed-detail")
        listOf("\u57fa\u7840\u7b7e\u5230", "\u5206\u4eab\u5e16\u5b50", "\u5206\u4eab\u6e38\u620f", "\u5206\u4eab\u8bc4\u4ef7").forEach(::assertLabelFits)
        capture("detail-task-results")
        assertLabelFits("Server detail \u00b7 Archived failure field")
        capture("detail-last-field")
        assertBackReachable()
        text("\u7b7e\u5230\u8bb0\u5f55").assertExists()
        assertBackReachable()
        compose.runOnIdle {
            assertEquals(listOf(4002L), opened.map { it.id })
            assertEquals(1, detailBacks)
            assertEquals(1, backs)
            assertEquals(0, retries)
            assertNull(state.value.selectedHistoryEntry)
            assertEquals(2, requireNotNull(state.value.history).entries.size)
        }
    }

    private fun show(
        initial: ComposeCheckinUiState = ComposeCheckinUiState(history = MembershipUiFixtures.history()),
        largeLight: Boolean = false,
    ) {
        state = mutableStateOf(initial)
        setScreen(theme(largeLight), systemFontScale = if (largeLight) 1.25f else 1f) {
            val entry = state.value.selectedHistoryEntry
            if (entry == null) {
                ComposeCheckinHistoryScreen(state.value, { backs++ }, { retries++ }, {
                    opened += it
                    state.value = state.value.copy(selectedHistoryEntry = it)
                })
            } else {
                ComposeCheckinHistoryDetailScreen(entry) {
                    detailBacks++
                    state.value = state.value.copy(selectedHistoryEntry = null)
                }
            }
        }
    }
}
