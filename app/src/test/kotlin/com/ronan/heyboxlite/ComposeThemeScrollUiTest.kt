package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class ComposeThemeScrollUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun crownEventsDoNotRecomposePaletteConsumersAndRealThemeChangesStillDo() {
        val state = mutableStateOf(composePreviewTheme(false))
        var paletteBindings = 0
        var rotaryBindings = 0
        var seenText = Color.Transparent
        var seenRequest: ComposeRotaryRequest? = null
        val onPalette: (ComposeThemeState) -> Unit = { palette -> paletteBindings++; seenText = palette.text }
        val onRotary: (ComposeRotaryRequest?) -> Unit = { request -> rotaryBindings++; seenRequest = request }
        compose.setContent {
            HeyboxComposeTheme(state.value) {
                PaletteProbe(onPalette)
                RotaryProbe(onRotary)
            }
        }
        val initial = compose.runOnIdle { paletteBindings }
        repeat(20) { index ->
            compose.runOnIdle {
                state.value = state.value.copy(rotaryRequest = ComposeRotaryRequest(index + 1, 12))
            }
        }
        compose.runOnIdle {
            assertEquals("A crown tick is input, not a theme change", initial, paletteBindings)
            assertTrue(rotaryBindings >= 21)
            assertEquals(ComposeRotaryRequest(20, 12), seenRequest)
            state.value = state.value.copy(text = Color.Black)
        }
        compose.runOnIdle {
            assertTrue(paletteBindings > initial)
            assertEquals(Color.Black, seenText)
        }
    }

    @Test fun watchPagesStillConsumeEachCrownRequestOnce() {
        val state = mutableStateOf(composePreviewTheme(false))
        compose.setContent {
            HeyboxComposeTheme(state.value) {
                WatchPage("Settings", null) {
                    repeat(12) { index -> Text("Item $index", Modifier.height(50.dp).testTag("row-$index")) }
                }
            }
        }
        val before = rowTop()
        compose.runOnIdle {
            state.value = state.value.copy(rotaryRequest = ComposeRotaryRequest(1, 48))
        }
        assertEquals(before - 48, rowTop(), 0.5f)
        compose.runOnIdle {
            state.value = state.value.copy(text = Color.Black)
        }
        assertEquals("Theme refresh must not replay an old crown tick", before - 48, rowTop(), 0.5f)
        compose.runOnIdle {
            state.value = state.value.copy(rotaryRequest = ComposeRotaryRequest(2, 48))
        }
        assertEquals(before - 96, rowTop(), 0.5f)
    }

    private fun rowTop(): Float = compose.onNodeWithTag("row-3").fetchSemanticsNode().boundsInRoot.top
}

@Composable
private fun PaletteProbe(onBind: (ComposeThemeState) -> Unit) {
    val state = LocalHeyboxTheme.current
    SideEffect { onBind(state) }
    Box { Text("Palette", color = state.text) }
}

@Composable
private fun RotaryProbe(onBind: (ComposeRotaryRequest?) -> Unit) {
    val request = LocalHeyboxRotaryRequest.current
    SideEffect { onBind(request) }
}
