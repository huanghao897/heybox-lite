package com.ronan.heyboxlite

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ComposeThemeStateTest {
    @Test fun copiedThemeRecalculatesStatusAndSwitchColoursInBothDirections() {
        val dark = composePreviewTheme(false)
        val light = dark.copy(dark = false)
        assertNotEquals(dark.danger, light.danger)
        assertNotEquals(dark.success, light.success)
        assertNotEquals(dark.switchTrack, light.switchTrack)
        assertEquals(Color.White, light.onDanger)
        assertEquals(Color.White, light.onSuccess)
        assertEquals(Color.White, light.switchThumb)
        val restored = light.copy(dark = true)
        assertEquals(dark.danger, restored.danger)
        assertEquals(dark.success, restored.success)
        assertEquals(dark.onDanger, restored.onDanger)
        assertEquals(dark.onSuccess, restored.onSuccess)
        assertEquals(dark.switchTrack, restored.switchTrack)
        assertEquals(dark.switchThumb, restored.switchThumb)
    }
}
