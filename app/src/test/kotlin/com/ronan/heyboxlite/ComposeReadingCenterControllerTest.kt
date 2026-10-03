package com.ronan.heyboxlite

import org.junit.Assert.*
import org.junit.Test

class ComposeReadingCenterControllerTest {
    @Test fun refreshKeepsCachedSnapshotAndDoesNotDuplicateInitialWork() {
        val fixture = Fixture()
        repeat(3) { fixture.controller.open() }
        assertEquals(1, fixture.callbacks.size)
        fixture.callbacks.single().onLoaded(ReadingCenterLoader.Snapshot(null, 5, 120L))
        fixture.controller.open()
        assertEquals(5, fixture.controller.state.value!!.watchLaterCount)
        fixture.callbacks.last().onError()
        assertEquals(5, fixture.controller.state.value!!.watchLaterCount)
        assertFalse(fixture.controller.loading.value)
    }

    @Test fun closedControllerRejectsLateSnapshots() {
        val fixture = Fixture()
        fixture.controller.open()
        fixture.controller.close()
        fixture.callbacks.single().onLoaded(ReadingCenterLoader.Snapshot(null, 5, 120L))
        fixture.controller.open()
        assertNull(fixture.controller.state.value)
        assertEquals(1, fixture.callbacks.size)
        assertTrue(fixture.closed)
    }

    private class Fixture {
        var closed = false
        val callbacks = ArrayList<ReadingCenterLoader.Callback>()
        val controller = ComposeReadingCenterController(
            loadSnapshot = { callbacks.add(it) },
            mapSnapshot = { ComposeReadingCenterState.from(it, "today") },
            closeLoader = { closed = true },
        )
    }
}
