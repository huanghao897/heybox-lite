@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ronan.heyboxlite

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OfficialPullRefreshStateTest {
    @Test fun alreadyHiddenStateDoesNotWaitForAnAnimationFrame() = runBlocking {
        val state = OfficialPullRefreshState()
        repeat(3) {
            // No frame clock: an unnecessary animation would suspend or fail here.
            state.animateToHidden()
            assertEquals(0f, state.distanceFraction, 0f)
            assertFalse(state.isAnimating)
        }
    }

    @Test fun alreadyVisibleThresholdAlsoCompletesWithoutAnAnimationFrame() = runBlocking {
        val state = OfficialPullRefreshState()
        state.snapTo(1f)
        state.animateToThreshold()
        assertEquals(1f, state.distanceFraction, 0f)
        assertFalse(state.isAnimating)
    }
}
