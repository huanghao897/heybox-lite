package com.ronan.heyboxlite

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ComposeFeedActionRowsTest {
    @Test fun feedActionsWrapBeforeTheirMeasuredWidthsExceedTheWatch() {
        assertEquals(listOf(listOf(0), listOf(1)), composeFeedActionRows(listOf(82f, 82f), 120f))
        assertEquals(listOf(listOf(0, 1), listOf(2)),
            composeFeedActionRows(listOf(48f, 32f, 48f), 90f))
        assertEquals(listOf(listOf(0)), composeFeedActionRows(listOf(140f), 100f))
    }
}
