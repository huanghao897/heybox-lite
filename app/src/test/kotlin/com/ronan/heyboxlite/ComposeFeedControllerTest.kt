package com.ronan.heyboxlite

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class ComposeFeedControllerTest {
    @Test fun initialLoadKeepsRefreshFeedbackHiddenAndUsesOriginalParameters() {
        val fixture = Fixture()
        fixture.controller.loadInitial()
        assertTrue(fixture.controller.loading.value)
        assertFalse(fixture.controller.refreshing.value)
        assertEquals(EndpointProvider.feeds(), fixture.requests.single().path)
        assertEquals(OfficialRequestParams.feed(1, null, "", true), fixture.requests.single().params)
        fixture.finish(response("first", "1", "2"))
        assertEquals(listOf("1", "2"), fixture.ids())
        assertFalse(fixture.controller.loading.value)
    }

    @Test fun callbackDoesNotParseFilterOrPublishUntilTheWorkerRuns() {
        val fixture = Fixture()
        var responseReads = 0
        val body = object : JSONObject() {
            override fun optJSONObject(key: String): JSONObject? {
                assertTrue("Feed parsing must run inside the worker", fixture.worker.running)
                responseReads++
                return super.optJSONObject(key)
            }
        }.put("result", JSONObject().put("links", JSONArray()
            .put(JSONObject().put("linkid", "1").put("title", "A post"))))
        fixture.controller.refresh()
        fixture.requests.single().callback.onSuccess(body)
        assertEquals(0, responseReads)
        assertEquals(0, fixture.filterReads)
        assertTrue(fixture.controller.items.value.isEmpty())
        assertTrue(fixture.controller.presentations.value.isEmpty())
        fixture.worker.runNext()
        assertTrue(responseReads > 0)
        assertEquals(1, fixture.filterReads)
        assertTrue(fixture.controller.items.value.isEmpty())
        assertTrue(fixture.controller.loading.value)
        fixture.publishNext()
        assertEquals(listOf("1"), fixture.ids())
        assertEquals("A post", fixture.controller.presentations.value.values.single().title)
        assertFalse(fixture.controller.refreshing.value)
        assertEquals(1, fixture.saved.size)
    }

    @Test fun paginationMergesWithoutReplacingExistingItemsAndHasOneRequestAtATime() {
        val fixture = Fixture()
        fixture.controller.loadInitial()
        fixture.finish(response("first", "1", "2"))
        val firstItem = fixture.controller.items.value.first()
        val firstPresentation = fixture.controller.presentations.value[firstItem]
        firstItem.likes = 27
        fixture.controller.loadMore()
        repeat(5) {
            fixture.controller.loadMore()
            fixture.controller.refresh()
        }
        assertEquals(2, fixture.requests.size)
        assertEquals(OfficialRequestParams.feed(0, 1, "first", false), fixture.requests.last().params)
        fixture.finish(response("second", "2", "3"))
        assertEquals(listOf("1", "2", "3"), fixture.ids())
        assertSame(firstItem, fixture.controller.items.value.first())
        assertSame(firstPresentation, fixture.controller.presentations.value[firstItem])
        assertEquals(fixture.controller.items.value.toSet(), fixture.controller.presentations.value.keys)
        assertEquals(27, fixture.controller.items.value.first().likes)
        assertFalse(fixture.controller.loadingMore.value)
        assertFalse(fixture.controller.noMore.value)
        fixture.controller.loadMore()
        assertEquals(OfficialRequestParams.feed(0, 0, "second", false), fixture.requests.last().params)
    }

    @Test fun filteringAndDeduplicationKeepTheFeedContract() {
        val fixture = Fixture()
        fixture.keywords = listOf("blocked")
        fixture.controller.refresh()
        val links = JSONArray()
            .put(JSONObject().put("linkid", "1").put("title", "blocked item"))
            .put(JSONObject().put("linkid", "2").put("title", "Allowed"))
            .put(JSONObject().put("linkid", "2").put("title", "Repeated"))
        fixture.finish(JSONObject().put("result", JSONObject().put("links", links)))
        assertEquals(listOf("2"), fixture.ids())
        assertEquals(fixture.controller.items.value.toSet(), fixture.controller.presentations.value.keys)
        assertFalse(fixture.controller.refreshing.value)
    }

    @Test fun emptyRefreshKeepsTheListEvenWhenItWasRestoredWhileTheRequestRan() {
        val fixture = Fixture()
        fixture.cached = listOf(item("cached"))
        fixture.controller.restoreCache()
        fixture.controller.refresh()
        fixture.worker.runNext()
        fixture.publishNext()
        val cached = fixture.controller.items.value
        fixture.finish(response("empty"))
        assertSame(cached, fixture.controller.items.value)
        assertEquals(listOf("没有获取到新内容，已保留原列表"), fixture.messages)
        assertFalse(fixture.controller.refreshing.value)
    }

    @Test fun duplicatePageStopsPrefetchButRefreshCanRestartIt() {
        val fixture = Fixture()
        fixture.controller.loadInitial()
        fixture.finish(response("first", "1"))
        fixture.controller.loadMore()
        fixture.finish(response("duplicate", "1"))
        assertTrue(fixture.controller.noMore.value)
        fixture.controller.loadMore()
        assertEquals(2, fixture.requests.size)
        fixture.controller.refresh()
        assertFalse(fixture.controller.noMore.value)
        fixture.finish(response("new", "2"))
        assertEquals(listOf("2"), fixture.ids())
    }

    @Test fun requestFailureReleasesRefreshAndKeepsTheCurrentFeed() {
        val fixture = Fixture()
        fixture.controller.loadInitial()
        fixture.finish(response("first", "1"))
        val before = fixture.controller.items.value
        fixture.controller.refresh()
        fixture.requests.last().callback.onError("offline")
        assertSame(before, fixture.controller.items.value)
        assertFalse(fixture.controller.loading.value)
        assertFalse(fixture.controller.refreshing.value)
        assertEquals("offline", fixture.controller.error.value)
        fixture.controller.refresh()
        assertEquals(3, fixture.requests.size)
    }

    @Test fun parseFailureReleasesRefreshAndAllowsRetryWithoutLosingTheFeed() {
        val fixture = Fixture()
        fixture.controller.loadInitial()
        fixture.finish(response("first", "1"))
        val before = fixture.controller.items.value
        fixture.controller.refresh()
        val malformed = object : JSONObject() {
            override fun optJSONObject(key: String): JSONObject? = throw IllegalArgumentException("bad response")
        }
        fixture.finish(malformed)
        assertSame(before, fixture.controller.items.value)
        assertFalse(fixture.controller.loading.value)
        assertFalse(fixture.controller.refreshing.value)
        assertEquals("内容解析失败，请重试", fixture.controller.error.value)
        fixture.controller.refresh()
        assertEquals(3, fixture.requests.size)
    }

    @Test fun paginationErrorRetriesWithTheSameCursorAndDoesNotSetNoMore() {
        val fixture = Fixture()
        fixture.controller.loadInitial()
        fixture.finish(response("first", "1"))
        fixture.controller.loadMore()
        val params = fixture.requests.last().params
        fixture.requests.last().callback.onError("offline")
        assertFalse(fixture.controller.loadingMore.value)
        assertFalse(fixture.controller.noMore.value)
        fixture.controller.loadMore()
        assertEquals(params, fixture.requests.last().params)
    }

    @Test fun closeRejectsLateNetworkAndWorkerResultsAndCannotRestart() {
        val fixture = Fixture()
        fixture.controller.refresh()
        val request = fixture.requests.single()
        request.callback.onSuccess(response("first", "1"))
        fixture.worker.runNext()
        fixture.controller.close()
        fixture.publishNext()
        request.callback.onSuccess(response("late", "2"))
        request.callback.onError("late failure")
        fixture.controller.restoreCache()
        fixture.controller.loadInitial()
        fixture.controller.refresh()
        fixture.controller.loadMore()
        assertTrue(fixture.controller.items.value.isEmpty())
        assertTrue(fixture.controller.presentations.value.isEmpty())
        assertFalse(fixture.controller.refreshing.value)
        assertFalse(fixture.controller.loading.value)
        assertTrue(fixture.worker.isShutdown)
        assertTrue(fixture.worker.tasks.isEmpty())
        assertTrue(fixture.saved.isEmpty())
        assertEquals(1, fixture.requests.size)
    }

    @Test fun gamePresentationIsPreparedOnTheWorkerAndRetainedAcrossPagination() {
        val fixture = Fixture()
        var reads = 0
        val preload = object : JSONObject() {
            override fun opt(key: String): Any? {
                assertTrue("Game display data must be prepared on the worker", fixture.worker.running)
                reads++
                return super.opt(key)
            }
        }.put("game", JSONObject().put("appid", "42").put("name", "Game"))
        fixture.controller.loadInitial()
        fixture.finish(JSONObject().put("result", JSONObject().put("lastval", "first")
            .put("links", JSONArray().put(JSONObject().put("linkid", "1").put("title", "Title")
                .put("communityPostPreload", preload)))))
        assertTrue(reads > 0)
        val before = reads
        val item = fixture.controller.items.value.single()
        val presentation = requireNotNull(fixture.controller.presentations.value[item])
        assertEquals("Game", requireNotNull(presentation.game).name)
        fixture.controller.loadMore()
        fixture.finish(response("next", "2"))
        assertEquals(before, reads)
        assertSame(presentation, fixture.controller.presentations.value[item])
        fixture.controller.refresh()
        fixture.finish(response("reset", "3"))
        assertFalse(fixture.controller.presentations.value.containsKey(item))
    }

    private class Fixture {
        val worker = QueuedExecutor()
        val publications = ArrayDeque<() -> Unit>()
        val requests = ArrayList<Request>()
        val saved = ArrayList<List<FeedItem>>()
        val messages = ArrayList<String>()
        var cached: List<FeedItem> = emptyList()
        var keywords: List<String> = emptyList()
        var filterReads = 0
        val controller = ComposeFeedController(
            request = { path, params, callback -> requests += Request(path, params, callback) },
            readCache = { assertTrue(worker.running); cached },
            writeCache = { assertFalse(worker.running); saved += it },
            blockedKeywords = { assertTrue(worker.running); filterReads++; keywords },
            io = worker,
            publish = { publications += it },
            onMessage = messages::add,
        )

        fun finish(body: JSONObject) {
            requests.last().callback.onSuccess(body)
            worker.runNext()
            publishNext()
        }

        fun publishNext() = publications.removeFirst().invoke()
        fun ids(): List<String> = controller.items.value.map { it.id }
    }

    private class QueuedExecutor : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        var running = false
        private var stopped = false
        override fun execute(command: Runnable) {
            check(!stopped)
            tasks += command
        }
        fun runNext() {
            running = true
            try { tasks.removeFirst().run() } finally { running = false }
        }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> {
            stopped = true
            return tasks.toMutableList().also { tasks.clear() }
        }
        override fun isShutdown(): Boolean = stopped
        override fun isTerminated(): Boolean = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
    }

    private data class Request(val path: String, val params: Map<String, String>, val callback: ApiClient.Callback)

    private fun item(id: String): FeedItem = FeedItem.from(JSONObject().put("linkid", id).put("title", "A post"))
    private fun response(cursor: String, vararg ids: String): JSONObject = JSONObject().put(
        "result", JSONObject().put("lastval", cursor).put("links", JSONArray().apply {
            ids.forEach { put(JSONObject().put("linkid", it).put("title", "A post")) }
        }),
    )
}
