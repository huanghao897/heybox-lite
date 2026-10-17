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
        assertEquals(OfficialRequestParams.search("new", 20, 20), f.requests.last().params)
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
        assertEquals(OfficialRequestParams.search("next", 20, 20), f.requests.last().params)
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
        assertEquals(OfficialRequestParams.search("watch", 20, 20), f.requests.last().params)
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

    private fun fixture() = Fixture().also(fixtures::add)

    private class Fixture {
        private val context = RuntimeEnvironment.getApplication()
        private val session = SessionStore(context).apply { clearSearchHistory() }
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
