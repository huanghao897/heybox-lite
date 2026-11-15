package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeFeedNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun reselectReturnsTheRealListToTopBeforeRefreshingExactlyOnce() {
        val list = LazyListState(8, 12)
        val navigation = ComposeFeedNavigation()
        var refreshes = 0
        compose.setContent {
            ObserveFeedNavigation(navigation, list, true) {
                assertEquals(0, list.firstVisibleItemIndex)
                assertEquals(0, list.firstVisibleItemScrollOffset)
                refreshes++
            }
            LazyColumn(state = list) { items(20) { Text("Post $it", Modifier.height(80.dp)) } }
        }
        compose.runOnIdle {
            assertEquals(8, list.firstVisibleItemIndex)
            assertEquals(0, refreshes)
            navigation.reselect()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun inactivePreviewNeverConsumesTheRequestAndRevisitingDoesNotRefreshAgain() {
        val list = LazyListState(6)
        val navigation = ComposeFeedNavigation()
        val active = mutableStateOf(false)
        var refreshes = 0
        compose.setContent {
            ObserveFeedNavigation(navigation, list, active.value) { refreshes++ }
            LazyColumn(state = list) { items(20) { Text("Post $it", Modifier.height(80.dp)) } }
        }
        compose.runOnIdle { navigation.reselect() }
        compose.runOnIdle { assertEquals(0, refreshes); active.value = true }
        compose.runOnIdle { assertEquals(1, refreshes); active.value = false }
        compose.runOnIdle { active.value = true }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun reselectDuringPaginationWaitsAndCoalescesTapsInsteadOfLosingTheRefresh() {
        val list = LazyListState(6)
        val navigation = ComposeFeedNavigation()
        val loading = mutableStateOf(true)
        var refreshes = 0
        compose.setContent {
            ObserveFeedNavigation(navigation, list, true, loading.value) { refreshes++ }
            LazyColumn(state = list) { items(20) { Text("Post $it", Modifier.height(80.dp)) } }
        }
        compose.runOnIdle { navigation.reselect() }
        compose.runOnIdle {
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, refreshes)
            repeat(4) { navigation.reselect() }
        }
        compose.runOnIdle { assertEquals(0, refreshes); loading.value = false }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }
}
