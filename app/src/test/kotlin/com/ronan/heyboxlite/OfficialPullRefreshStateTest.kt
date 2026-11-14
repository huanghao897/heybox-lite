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

    @Test fun motionOffReleaseHidesAPartialPullWithoutWaitingForAFrame() = runBlocking {
        val state = OfficialPullRefreshState().apply { animateTransitions = false }
        state.snapTo(0.4f)
        // Material awaits animateToHidden before letting the child fling.
        // A zero-duration tween still needs a frame clock, unlike a snap.
        state.animateToHidden()
        assertEquals(0f, state.distanceFraction, 0f)
        assertFalse(state.isAnimating)
    }

    @Test fun motionOffRefreshThresholdDoesNotWaitForAFrame() = runBlocking {
        val state = OfficialPullRefreshState().apply { animateTransitions = false }
        state.snapTo(1.2f)
        state.animateToThreshold()
        assertEquals(1f, state.distanceFraction, 0f)
        assertFalse(state.isAnimating)
    }
}
