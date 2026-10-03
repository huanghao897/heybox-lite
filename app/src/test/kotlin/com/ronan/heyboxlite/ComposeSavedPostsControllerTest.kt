package com.ronan.heyboxlite

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ComposeSavedPostsControllerTest {
    @Test fun creationAndGuestEntryNeverSendRequests() {
        val fixture = Fixture()
        assertTrue(fixture.requests.isEmpty())
        fixture.account = ""
        fixture.open()
        assertTrue(fixture.tasks.isEmpty())
        assertEquals(ComposeSavedPostsState(), fixture.controller.state.value)
    }

    @Test fun diskRestoreIsQueuedAndNetworkUsesTheOriginalParams() {
        val fixture = Fixture()
        fixture.cached = listOf(item("cached"))
        fixture.open()
        assertTrue(fixture.controller.state.value.loading)
        assertTrue(fixture.requests.isEmpty())
        fixture.tasks.removeFirst().invoke()
        assertTrue(fixture.requests.isEmpty())
        fixture.ui.removeFirst().invoke()
        assertEquals("cached", fixture.controller.state.value.items.single().id)
        assertEquals(EndpointProvider.favoriteLinks(), fixture.requests.single().path)
        assertEquals(OfficialRequestParams.favorites("folder-a", 0, 30), fixture.requests.single().params)
    }

    @Test fun repeatedEntryWhileLoadingSharesARequest() {
        val fixture = Fixture()
        repeat(3) { fixture.open() }
        assertEquals(1, fixture.tasks.size)
        fixture.flush()
        assertEquals(1, fixture.requests.size)
    }

    @Test fun successfulEmptyCollectionIsRememberedOnReturn() {
        val fixture = Fixture()
        fixture.open()
        fixture.flush()
        fixture.requests.single().callback.onSuccess(body())
        fixture.open()
        fixture.flush()
        assertEquals(1, fixture.requests.size)
        assertTrue(fixture.controller.state.value.loaded)
        assertFalse(fixture.controller.state.value.loading)
    }

    @Test fun networkFailureKeepsOfflineContentsAndExposesAnError() {
        val fixture = Fixture()
        fixture.cached = listOf(item("cached"))
        fixture.open()
        fixture.flush()
        fixture.requests.single().callback.onError("offline")
        assertEquals("cached", fixture.controller.state.value.items.single().id)
        assertEquals("offline", fixture.controller.state.value.error)
        assertFalse(fixture.controller.state.value.loading)
        assertFalse(fixture.controller.state.value.loaded)
    }

    @Test fun retryKeepsTheVisibleListUntilTheNewReply() {
        val fixture = Fixture()
        fixture.open()
        fixture.flush()
        fixture.requests.single().callback.onSuccess(body("first"))
        fixture.open(force = true)
        assertEquals("first", fixture.controller.state.value.items.single().id)
        assertTrue(fixture.controller.state.value.loading)
        fixture.flush()
        fixture.requests.last().callback.onSuccess(body("updated"))
        assertEquals("updated", fixture.controller.state.value.items.single().id)
    }

    @Test fun freshReturnUsesCacheAndAStaleExplicitEntryRefreshesWithoutPolling() {
        val fixture = Fixture()
        fixture.open()
        fixture.flush()
        fixture.requests.single().callback.onSuccess(body("existing"))
        fixture.clock = 59_000L
        fixture.open()
        fixture.flush()
        assertEquals(1, fixture.requests.size)
        fixture.clock = 60_000L
        assertEquals(1, fixture.requests.size)
        fixture.open()
        fixture.flush()
        assertEquals(2, fixture.requests.size)
        assertEquals("existing", fixture.controller.state.value.items.single().id)
    }

    @Test fun folderChangeCannotBeOverwrittenByAnOldReply() {
        val fixture = Fixture()
        fixture.open()
        fixture.flush()
        val first = fixture.requests.single()
        fixture.open("folder-b")
        fixture.flush()
        first.callback.onSuccess(body("stale"))
        assertTrue(fixture.controller.state.value.items.isEmpty())
        fixture.requests.last().callback.onSuccess(body("correct"))
        assertEquals("correct", fixture.controller.state.value.items.single().id)
    }

    @Test fun accountChangeSeparatesDiskKeysAndRejectsOldResponses() {
        val fixture = Fixture()
        fixture.open()
        fixture.flush()
        val old = fixture.requests.single()
        fixture.account = "different-account"
        fixture.open()
        fixture.flush()
        assertNotEquals(fixture.cacheKeys[0], fixture.cacheKeys[1])
        old.callback.onSuccess(body("old-account"))
        assertTrue(fixture.controller.state.value.items.isEmpty())
        fixture.requests.last().callback.onSuccess(body("new-account"))
        assertEquals("new-account", fixture.controller.state.value.items.single().id)
    }

    @Test fun logoutAndCloseRejectInFlightCallbacks() {
        val fixture = Fixture()
        fixture.open()
        fixture.flush()
        fixture.account = ""
        fixture.requests.single().callback.onSuccess(body("private"))
        assertEquals(ComposeSavedPostsState(), fixture.controller.state.value)
        fixture.account = "42"
        fixture.open()
        fixture.flush()
        fixture.controller.close()
        val before = fixture.controller.state.value
        fixture.requests.last().callback.onSuccess(body("late"))
        fixture.requests.last().callback.onError("late")
        fixture.open(force = true)
        assertSame(before, fixture.controller.state.value)
        assertEquals(2, fixture.requests.size)
    }

    @Test fun queuedDiskReplyAfterCloseCannotStartNetworking() {
        val fixture = Fixture()
        fixture.open()
        fixture.controller.close()
        fixture.flush()
        assertTrue(fixture.requests.isEmpty())
    }

    @Test fun cacheFailureStillAllowsOnlineReadAndSaveFailureDoesNotRemoveContent() {
        val fixture = Fixture()
        fixture.cacheError = true
        fixture.open()
        fixture.flush()
        assertEquals(1, fixture.failures)
        fixture.requests.single().callback.onSuccess(body("available"))
        assertTrue(fixture.saved.isEmpty())
        fixture.flush()
        assertEquals(2, fixture.failures)
        assertEquals("available", fixture.controller.state.value.items.single().id)
        assertTrue(fixture.controller.state.value.loaded)
    }

    @Test fun savedListsApplyTheSameFilterToDiskAndNetwork() {
        val fixture = Fixture()
        fixture.cached = listOf(item("allowed"), item("blocked"))
        fixture.open()
        fixture.flush()
        assertEquals(listOf("allowed"), fixture.controller.state.value.items.map { it.id })
        fixture.requests.single().callback.onSuccess(body("new-allowed", "blocked"))
        assertEquals(listOf("new-allowed"), fixture.controller.state.value.items.map { it.id })
        fixture.flush()
        assertEquals(listOf("new-allowed"), fixture.saved.single().second.map { it.id })
    }

    private class Fixture {
        var account = "42"
        var cached = emptyList<FeedItem>()
        var cacheError = false
        var failures = 0
        var clock = 0L
        val tasks = ArrayDeque<() -> Unit>()
        val ui = ArrayDeque<() -> Unit>()
        val requests = ArrayList<Request>()
        val cacheKeys = ArrayList<String>()
        val saved = ArrayList<Pair<String, List<FeedItem>>>()
        val controller = ComposeSavedPostsController(
            path = EndpointProvider.favoriteLinks(), accountId = { account },
            request = { path, params, callback -> requests.add(Request(path, params, callback)) },
            readCache = { key ->
                cacheKeys.add(key)
                if (cacheError) throw IllegalStateException("disk unavailable")
                cached
            },
            writeCache = { key, items ->
                if (cacheError) throw IllegalStateException("disk unavailable")
                saved.add(key to items)
            },
            filter = { items -> items.filter { it.id != "blocked" } },
            background = { tasks.add(it) }, publish = { ui.add(it) },
            cacheFailure = { failures++ }, markFavorites = true,
            nowMillis = { clock },
        )

        fun open(folder: String = "folder-a", force: Boolean = false) =
            controller.load(folder, OfficialRequestParams.favorites(folder, 0, 30), force)

        fun flush() {
            while (tasks.isNotEmpty() || ui.isNotEmpty()) {
                while (tasks.isNotEmpty()) tasks.removeFirst().invoke()
                while (ui.isNotEmpty()) ui.removeFirst().invoke()
            }
        }
    }

    private data class Request(val path: String, val params: Map<String, String>, val callback: ApiClient.Callback)
    private fun item(id: String) = FeedItem.from(JSONObject().put("linkid", id))
    private fun body(vararg ids: String) = JSONObject().put("result", JSONObject().put("links",
        JSONArray().apply { ids.forEach { put(JSONObject().put("linkid", it)) } }))
}
