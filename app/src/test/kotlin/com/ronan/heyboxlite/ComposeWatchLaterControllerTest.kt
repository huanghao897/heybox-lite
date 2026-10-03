package com.ronan.heyboxlite

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ComposeWatchLaterControllerTest {
    @Test fun sizesAndRemovalAreComputedOnlyInBackgroundTasks() {
        val fixture = Fixture()
        fixture.controller.refresh()
        assertEquals(0, fixture.reads)
        fixture.flush()
        assertEquals(1, fixture.reads)
        val entry = fixture.controller.state.value.entries.single()
        assertEquals(420L, entry.bytes)
        fixture.controller.remove(entry)
        assertTrue(fixture.removed.isEmpty())
        assertEquals(entry, fixture.controller.state.value.entries.single())
        fixture.flush()
        assertEquals(listOf("post"), fixture.removed)
        assertTrue(fixture.controller.state.value.entries.isEmpty())
    }

    @Test fun repeatedTapsShareOneLoadAndErrorsKeepVisibleData() {
        val fixture = Fixture()
        repeat(3) { fixture.controller.refresh() }
        assertEquals(1, fixture.tasks.size)
        fixture.flush()
        fixture.fail = true
        fixture.controller.refresh()
        fixture.flush()
        assertFalse(fixture.controller.state.value.loading)
        assertEquals("post", fixture.controller.state.value.entries.single().item.id)
        assertFalse(fixture.controller.state.value.error.isEmpty())
    }

    @Test fun closeDiscardsQueuedUiResultsAndFurtherRequests() {
        val fixture = Fixture()
        fixture.controller.refresh()
        fixture.controller.close()
        fixture.flush()
        fixture.controller.refresh()
        assertTrue(fixture.controller.state.value.entries.isEmpty())
        assertEquals(1, fixture.reads)
        assertTrue(fixture.tasks.isEmpty())
    }

    private class Fixture {
        var reads = 0
        var fail = false
        val removed = ArrayList<String>()
        val tasks = ArrayDeque<() -> Unit>()
        val ui = ArrayDeque<() -> Unit>()
        val entry = ComposeOfflineEntry(FeedItem.from(JSONObject().put("linkid", "post")), 420L, 1L)
        val controller = ComposeWatchLaterController(
            readEntries = {
                reads++
                if (fail) throw IllegalStateException()
                if (removed.isEmpty()) listOf(entry) else emptyList()
            },
            remove = { removed.add(it) }, background = { tasks.add(it) }, publish = { ui.add(it) },
            reportFailure = {},
        )
        fun flush() {
            while (tasks.isNotEmpty()) tasks.removeFirst().invoke()
            while (ui.isNotEmpty()) ui.removeFirst().invoke()
        }
    }
}
