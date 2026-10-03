package com.ronan.heyboxlite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeCheckinTaskSettingsInputTest {
    @Test fun timeAcceptsOnlyTwentyFourHourHHmm() {
        listOf("00:00", "08:30", "23:59").forEach {
            assertEquals(it, parseCheckinTaskTime(it))
        }
        assertEquals("09:05", parseCheckinTaskTime(" 09:05 "))
        listOf("", "8:30", "08:3", "24:00", "12:60", "-1:00", "12:00:00", "abcd")
            .forEach { assertNull(it, parseCheckinTaskTime(it)) }
    }

    @Test fun offsetAcceptsTheExistingInclusiveBounds() {
        assertEquals(0, parseCheckinTaskOffset("0"))
        assertEquals(720, parseCheckinTaskOffset("720"))
        assertEquals(30, parseCheckinTaskOffset(" 030 "))
        (0..720).forEach { assertEquals(it, parseCheckinTaskOffset(it.toString())) }
    }

    @Test fun offsetRejectsOutOfRangeOrNonIntegralDraftsWithoutClamping() {
        listOf("", " ", "-1", "+30", "721", "1000", "2147483648", "1.5", "30m", "a")
            .forEach { assertNull(it, parseCheckinTaskOffset(it)) }
    }
}
