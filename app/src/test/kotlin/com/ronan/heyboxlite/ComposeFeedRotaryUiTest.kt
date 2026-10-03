package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class ComposeFeedRotaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var api: ApiClient? = null

    @After fun close() { api?.close() }

    @Test fun crownBurstsScrollTheFeedWithoutRecomposingTheSurfaceOrLosingDistance() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val session = SessionStore(context).apply { setNoImage(true) }
        val client = ApiClient(session).also { api = it }
        val services = ComposeServices(compose.activity, session, client, GameDetailClient(client),
            LocalCache(context), Handler(Looper.getMainLooper()), ReadingTimeTracker(context), null,
            composePreviewTheme(false).copy(motionLevel = MotionLevel.OFF), ComposeToast {})
        val posts = (0 until 12).map { index ->
            FeedItem.from(JSONObject().put("linkid", "$index").put("title", "Title $index")
                .put("description", "Summary $index"))
        }
        val input = ComposeFeedRotaryInput()
        val listState = LazyListState()
        var surfaceBindings = 0
        compose.setContent {
            SideEffect { surfaceBindings++ }
            Box(Modifier.size(240.dp, 320.dp)) {
                ComposeFeedScreen(posts, false, false, true, services, {}, {}, {}, {}, { _, _ -> },
                    listState = listState, rotaryInput = input)
            }
        }
        val before = compose.runOnIdle { surfaceBindings }
        compose.runOnIdle { repeat(20) { input.offer(1) } }
        compose.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(20, listState.firstVisibleItemScrollOffset)
            assertEquals("Crown input must not invalidate the screen services/theme", before, surfaceBindings)
        }
        compose.runOnIdle { repeat(10) { input.offer(-1) } }
        compose.runOnIdle {
            assertEquals(10, listState.firstVisibleItemScrollOffset)
            assertEquals(before, surfaceBindings)
        }
    }

    @Test fun pendingDistanceAccumulatesSignedTicksAndCanBeClearedBeforeLeavingTheFeed() {
        val input = ComposeFeedRotaryInput()
        input.offer(15)
        input.offer(20)
        input.offer(-5)
        assertEquals(30f, input.takeDistance(), 0f)
        assertEquals(0f, input.takeDistance(), 0f)
        input.offer(Int.MAX_VALUE)
        input.offer(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE.toLong().times(2).toFloat(), input.takeDistance(), 0f)
        input.offer(100)
        input.clear()
        assertEquals(0f, input.takeDistance(), 0f)
    }

    @Test fun aFingerDragCannotPermanentlyStopCrownInput() {
        val input = ComposeFeedRotaryInput()
        val state = LazyListState()
        val dragging = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            ObserveFeedRotaryInput(input, state)
            LazyColumn(state = state, modifier = Modifier.size(240.dp, 320.dp)) {
                items(10) { index -> Text("Item $index", Modifier.height(100.dp)) }
            }
        }
        compose.runOnIdle {
            scope.launch {
                state.scroll(MutatePriority.UserInput) {
                    dragging.complete(Unit)
                    release.await()
                }
            }
        }
        compose.runOnIdle {
            assertTrue(dragging.isCompleted)
            input.offer(20)
        }
        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemScrollOffset)
            release.complete(Unit)
        }
        compose.runOnIdle { input.offer(20) }
        compose.runOnIdle { assertEquals(20, state.firstVisibleItemScrollOffset) }
    }
}
