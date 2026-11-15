package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeSearchControllerTest {
    private val fixtures = mutableListOf<Fixture>()

    @After fun closeClients() {
        fixtures.forEach { it.controller.close(); it.api.close() }
    }

    @Test fun searchWaitsForDebounceAndKeepsTheOriginalRequestParameters() {
        val f = fixture()
        f.controller.setQuery("  Steam  ")
        f.advance(419)
        assertTrue(f.requests.isEmpty())
        assertFalse(f.controller.loading.value)
        f.advance(1)
        assertEquals(EndpointProvider.search(), f.requests.single().path)
        assertEquals(OfficialRequestParams.search("Steam", 0, 20), f.requests.single().params)
        assertTrue(f.controller.loading.value)
        assertEquals(listOf("Steam"), f.controller.history.value)
        f.finish("first")
        assertEquals(listOf("first"), f.ids())
        assertFalse(f.controller.loading.value)
    }

    @Test fun paginationCannotStealThePendingSearchForANewKeyword() {
        val f = fixture()
        f.search("old", "old-result")
        f.controller.setQuery("new")
        repeat(3) { f.controller.loadMore() }
        assertEquals(1, f.requests.size)
        f.advance(420)
        assertEquals(OfficialRequestParams.search("new", 0, 20), f.requests.last().params)
        assertEquals(2, f.requests.size)
        f.finish("new-result")
        assertEquals(listOf("new-result"), f.ids())
        f.controller.loadMore()
        assertEquals(OfficialRequestParams.search("new", 1, 20), f.requests.last().params)
    }

    @Test fun returningToThePreviousKeywordStillWaitsForItsOwnDebounce() {
        val f = fixture()
        f.search("old", "old-result")
        f.controller.setQuery("new")
        f.advance(200)
        f.controller.setQuery(" old ")
        f.controller.loadMore()
        f.advance(419)
        assertEquals(1, f.requests.size)
        f.advance(1)
        assertEquals(2, f.requests.size)
        assertEquals(OfficialRequestParams.search("old", 0, 20), f.requests.last().params)
        f.finish("replacement")
        assertEquals(listOf("replacement"), f.ids())
    }

    @Test fun changingKeywordRejectsAnAlreadyRunningPaginationResponse() {
        val f = fixture()
        f.search("old", "old-result")
        f.controller.loadMore()
        val oldPage = f.requests.last().callback
        f.controller.setQuery("new")
        oldPage.onSuccess(body("stale-page"))
        assertEquals(listOf("old-result"), f.ids())
        assertFalse(f.controller.loading.value)
        f.advance(420)
        assertEquals(OfficialRequestParams.search("new", 0, 20), f.requests.last().params)
        oldPage.onError("late failure")
        assertTrue(f.controller.loading.value)
        f.finish("new-result")
        assertEquals(listOf("new-result"), f.ids())
    }

    @Test fun clearingQueryCancelsDebounceAndRejectsInFlightResults() {
        val f = fixture()
        f.search("old", "old-result")
        f.controller.loadMore()
        val oldPage = f.requests.last().callback
        f.controller.setQuery("new")
        f.controller.setQuery(" ")
        f.controller.loadMore()
        f.advance(1_000)
        oldPage.onSuccess(body("stale-page"))
        assertEquals(2, f.requests.size)
        assertTrue(f.controller.items.value.isEmpty())
        assertFalse(f.controller.loading.value)
        assertFalse(f.controller.noMore.value)
        f.controller.setQuery("next")
        f.advance(420)
        f.finish("next-result")
        f.controller.loadMore()
        assertEquals(OfficialRequestParams.search("next", 1, 20), f.requests.last().params)
    }

    @Test fun closeCancelsPendingDebounceAndRejectsInFlightCallbacks() {
        val f = fixture()
        f.search("old", "old-result")
        f.controller.loadMore()
        val oldPage = f.requests.last().callback
        f.controller.setQuery("new")
        f.controller.close()
        f.advance(1_000)
        oldPage.onSuccess(body("stale-page"))
        oldPage.onError("late failure")
        assertEquals(2, f.requests.size)
        assertEquals(listOf("old-result"), f.ids())
        assertFalse(f.controller.loading.value)
    }

    @Test fun sessionChangeKeepsQueryButClearsOldResultsAndRestartsFromOffsetZero() {
        val f = fixture()
        f.search("watch", "old-account")
        f.controller.loadMore()
        val oldPage = f.requests.last().callback

        f.controller.invalidateSession()

        assertEquals("watch", f.controller.query.value)
        assertTrue(f.controller.items.value.isEmpty())
        assertFalse(f.controller.loading.value)
        assertFalse(f.controller.noMore.value)
        f.controller.loadMore()
        assertEquals(2, f.requests.size)
        f.advance(420)
        assertEquals(OfficialRequestParams.search("watch", 0, 20), f.requests.last().params)
        oldPage.onSuccess(body("old-page"))
        oldPage.onError("old failure")
        assertTrue(f.controller.items.value.isEmpty())
        assertTrue(f.controller.loading.value)
        f.finish("new-account")
        assertEquals(listOf("new-account"), f.ids())
        f.controller.loadMore()
        assertEquals(OfficialRequestParams.search("watch", 1, 20), f.requests.last().params)
    }

    @Test fun sessionChangeReplacesPendingDebounceAndBlankQueryRemainsIdle() {
        val f = fixture()
        f.controller.setQuery("watch")
        f.advance(200)
        f.controller.invalidateSession()
        f.advance(419)
        assertTrue(f.requests.isEmpty())
        f.advance(1)
        assertEquals(1, f.requests.size)
        f.controller.setQuery("")
        f.controller.invalidateSession()
        f.advance(1_000)
        assertEquals(1, f.requests.size)
        assertEquals("", f.controller.query.value)
        assertFalse(f.controller.loading.value)
    }

    @Test fun filteredFirstPageAdvancesRawOffsetBeforeLoadingVisibleResults() {
        val f = fixture()
        f.session.setBlockKeywords("blocked")
        f.search("watch", "blocked-a", "blocked-b")
        f.advance(0)
        assertEquals(OfficialRequestParams.search("watch", 2, 20), f.requests.last().params)
        assertTrue(f.controller.loading.value)
        repeat(3) { f.controller.loadMore() }
        assertEquals(2, f.requests.size)
        f.finish("visible")
        assertEquals(listOf("visible"), f.ids())
        assertFalse(f.controller.noMore.value)
    }

    @Test fun rawDuplicateRowsStillAdvanceOffsetAndRepeatedPagesStop() {
        val f = fixture()
        f.search("watch", "one", "one", "two")
        assertEquals(listOf("one", "two"), f.ids())
        f.controller.loadMore()
        assertEquals(OfficialRequestParams.search("watch", 3, 20), f.requests.last().params)
        f.finish("one", "one", "two")
        assertTrue(f.controller.noMore.value)
        f.controller.loadMore()
        f.advance(0)
        assertEquals(2, f.requests.size)
    }

    @Test fun emptyPageContinuationHasABudgetButManualLoadCanContinue() {
        val f = fixture()
        f.session.setBlockKeywords("blocked")
        f.search("watch", "blocked-0")
        for (page in 1..4) {
            f.advance(0)
            // Skipped pages keep the loading state, so the page never flashes "no results".
            assertTrue(f.controller.loading.value)
            f.finish("blocked-$page")
        }
        f.advance(0)
        assertEquals(5, f.requests.size)
        assertFalse(f.controller.noMore.value)
        assertFalse(f.controller.loading.value)
        f.controller.loadMore()
        assertEquals(OfficialRequestParams.search("watch", 5, 20), f.requests.last().params)
        f.finish("visible")
        assertEquals(listOf("visible"), f.ids())
    }

    @Test fun closeOrNewQueryCancelsQueuedEmptyPageContinuation() {
        val f = fixture()
        f.session.setBlockKeywords("blocked")
        f.search("watch", "blocked-0")
        f.controller.close()
        f.advance(100)
        assertEquals(1, f.requests.size)
        val other = fixture()
        other.session.setBlockKeywords("blocked")
        other.search("watch", "blocked-0")
        other.controller.setQuery("new")
        other.advance(420)
        assertEquals(OfficialRequestParams.search("new", 0, 20), other.requests.last().params)
        assertEquals(2, other.requests.size)
    }

    @Test fun serverEndFlagPreventsAutomaticEmptyPageRequests() {
        val f = fixture()
        f.session.setBlockKeywords("blocked")
        f.controller.setQuery("watch")
        f.advance(420)
        val body = body("blocked-0")
        body.getJSONObject("result").put("has_more", 0)
        f.requests.last().callback.onSuccess(body)
        f.advance(0)
        assertTrue(f.controller.noMore.value)
        assertEquals(1, f.requests.size)
    }

    @Test fun firstPageFailureIsExplicitAndOnlyAManualRetrySendsAnotherRequest() {
        val f = fixture()
        f.search("old", "old-result")
        f.controller.setQuery("new")
        assertTrue(f.controller.pending.value)
        f.advance(420)
        assertFalse(f.controller.pending.value)
        assertTrue(f.ids().isEmpty())
        f.requests.last().callback.onError("Offline")
        assertEquals("Offline", f.controller.error.value)
        assertFalse(f.controller.loading.value)
        repeat(3) { f.controller.loadMore() }
        assertEquals(2, f.requests.size)
        f.controller.retry()
        assertEquals(OfficialRequestParams.search("new", 0, 20), f.requests.last().params)
        assertEquals("", f.controller.error.value)
        f.finish("new-result")
        assertEquals(listOf("new-result"), f.ids())
    }

    @Test fun appendFailureKeepsResultsAndRetriesTheSameRawOffset() {
        val f = fixture()
        f.search("watch", "one", "two")
        f.controller.loadMore()
        f.requests.last().callback.onError("Offline")
        assertEquals(listOf("one", "two"), f.ids())
        f.controller.retry()
        assertEquals(OfficialRequestParams.search("watch", 2, 20), f.requests.last().params)
        f.finish("three")
        assertEquals(listOf("one", "two", "three"), f.ids())
        assertEquals("", f.controller.error.value)
    }

    @Test fun oldFailureCannotReplaceANewQueryOrItsPendingState() {
        val f = fixture()
        f.controller.setQuery("old")
        f.advance(420)
        val old = f.requests.last().callback
        f.controller.setQuery("new")
        old.onError("stale failure")
        assertEquals("", f.controller.error.value)
        assertTrue(f.controller.pending.value)
        f.controller.close()
        assertFalse(f.controller.pending.value)
        assertEquals("", f.controller.error.value)
    }

    private fun fixture() = Fixture().also(fixtures::add)

    private class Fixture {
        private val context = RuntimeEnvironment.getApplication()
        val session = SessionStore(context).apply { clearSearchHistory(); setBlockKeywords("") }
        val api = ApiClient(session)
        val requests = mutableListOf<Request>()
        val controller = ComposeSearchController(session, api, LocalCache(context),
            Handler(Looper.getMainLooper()), request = { path, params, callback ->
                requests += Request(path, params, callback)
            })

        fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        fun finish(vararg ids: String) = requests.last().callback.onSuccess(body(*ids))
        fun search(keyword: String, vararg ids: String) {
            controller.setQuery(keyword)
            advance(420)
            finish(*ids)
        }
        fun ids() = controller.items.value.map { it.id }
    }

    private data class Request(
        val path: String,
        val params: Map<String, String>,
        val callback: ApiClient.Callback,
    )

    companion object {
        private fun body(vararg ids: String) = JSONObject().put("result", JSONObject().put("links",
            JSONArray().apply { ids.forEach { put(JSONObject().put("linkid", it).put("title", it)) } }))
    }
}
