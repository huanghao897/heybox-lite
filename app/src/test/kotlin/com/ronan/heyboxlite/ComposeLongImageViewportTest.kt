package com.ronan.heyboxlite

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class ComposeLongImageViewportTest {
    @Test fun veryTallImageScrollsWithoutMakingTheLayoutImageSized() {
        val viewport = ComposeLongImageViewport()
        viewport.layout(Size(240f, 320f), 8, 16000, false, 12f, Offset(120f, 160f))
        assertEquals(2880f, viewport.imageWidth, 0f)
        assertFalse(viewport.canPull(-1f))
        viewport.pan(Offset(0f, -10_000_000f))
        assertTrue(viewport.position.y > 262142f)
        assertTrue(viewport.canPull(-1f))
        viewport.pan(Offset(0f, 10_000_000f))
        assertEquals(0f, viewport.position.y, 0f)
        assertTrue(viewport.canPull(1f))
    }

    @Test fun zoomKeepsTheImagePointUnderTheFingersAndPanReachesTheEnd() {
        val viewport = ComposeLongImageViewport()
        val size = Size(240f, 320f)
        viewport.layout(size, 640, 12000, false, 1f, Offset.Zero)
        viewport.pan(Offset(0f, -500f))
        viewport.layout(size, 640, 12000, false, 2f, Offset(120f, 160f))
        assertEquals(1160f, viewport.position.y, 0f)
        assertEquals(120f, viewport.position.x, 0f)
        viewport.pan(Offset(-20f, -30f))
        assertEquals(Offset(140f, 1190f), viewport.position)
        assertFalse(viewport.canPull(1f))
        viewport.layout(size, 640, 12000, false, 1f, Offset(120f, 160f))
        assertEquals(0f, viewport.position.x, 0f)
    }

    @Test fun roundViewportCanBringMaskedImageEdgesIntoTheCenter() {
        val viewport = ComposeLongImageViewport()
        viewport.layout(Size(227f, 227f), 640, 12000, true, 2f, Offset(113.5f, 113.5f))
        viewport.pan(Offset(10000f, 10000f))
        assertTrue(viewport.position.x < 0f)
        assertTrue(viewport.position.y < 0f)
        viewport.layout(Size(227f, 227f), 640, 12000, true, 1f, Offset(113.5f, 113.5f))
        assertEquals(Offset.Zero, viewport.position)
    }
}
