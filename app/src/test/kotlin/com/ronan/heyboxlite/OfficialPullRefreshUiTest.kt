package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class OfficialPullRefreshUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val refreshing = mutableStateOf(false)
    private var requests = 0
    private lateinit var listState: androidx.compose.foundation.lazy.LazyListState

    @Test fun contentFollowsTheDragAndRefreshOccursOnlyOnRelease() {
        showRefresh()
        val initial = top()
        compose.onNodeWithTag("pull-surface").performTouchInput {
            down(Offset(width / 2f, 25f))
            moveBy(Offset(0f, 190f))
        }
        compose.onNodeWithContentDescription("下拉刷新").assertIsDisplayed()
        assertTrue("Cards must move with the pull", top() > initial + 40f)
        assertEquals(0, requests)
        compose.onNodeWithTag("pull-surface").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithTag("pull-surface").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, requests) }
        compose.runOnIdle { refreshing.value = false }
        compose.onNodeWithContentDescription("下拉刷新").assertDoesNotExist()
        assertEquals(initial, top(), 0.5f)
    }

    @Test fun shortPullAndReversedPullDoNotRefresh() {
        showRefresh()
        compose.onNodeWithTag("pull-surface").performTouchInput {
            down(Offset(width / 2f, 25f))
            moveBy(Offset(0f, 40f))
            up()
        }
        assertEquals(0, requests)
        compose.onNodeWithTag("pull-surface").performTouchInput {
            down(Offset(width / 2f, 25f))
            moveBy(Offset(0f, 190f))
            moveBy(Offset(0f, -170f))
            up()
        }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithContentDescription("下拉刷新").assertDoesNotExist()
        // Reversing can fling the list upwards, but must leave its container at rest.
        assertEquals(0f, compose.onNodeWithTag("pull-list").fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }

    @Test fun refreshCompletingInTheSameFrameStillHidesTheHeader() {
        showRefresh(onRequest = {
            refreshing.value = true
            refreshing.value = false
        })
        val initial = top()
        repeat(3) {
            compose.onNodeWithTag("pull-surface").performTouchInput { swipeDown() }
            compose.runOnIdle { assertEquals(it + 1, requests) }
            compose.onNodeWithContentDescription("下拉刷新").assertDoesNotExist()
            assertEquals(initial, top(), 0.5f)
        }
    }

    @Test fun declinedRefreshStillHidesTheHeaderAndAllowsTheNextPull() {
        showRefresh(onRequest = {})
        val initial = top()
        repeat(3) {
            compose.onNodeWithTag("pull-surface").performTouchInput { swipeDown() }
            compose.runOnIdle { assertEquals(it + 1, requests) }
            compose.onNodeWithContentDescription("下拉刷新").assertDoesNotExist()
            assertEquals(initial, top(), 0.5f)
        }
    }

    @Test fun loadingStartingDuringThePullCannotLeaveTheHeaderStuck() {
        val enabled = mutableStateOf(true)
        showRefresh(enabled, onRequest = {})
        val initial = top()
        compose.onNodeWithTag("pull-surface").performTouchInput {
            down(Offset(width / 2f, 25f))
            moveBy(Offset(0f, 190f))
        }
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("pull-surface").performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithContentDescription("下拉刷新").assertDoesNotExist()
        assertEquals(initial, top(), 0.5f)
    }

    @Test fun rejectedRefreshAlsoResetsMaterialOffsetsWithAnimationsEnabled() {
        showRefresh(motionLevel = MotionLevel.REDUCED, onRequest = {})
        val initial = top()
        repeat(3) {
            compose.onNodeWithTag("pull-surface").performTouchInput { swipeDown() }
            compose.runOnIdle { assertEquals(it + 1, requests) }
            compose.onNodeWithContentDescription("下拉刷新").assertDoesNotExist()
            assertEquals(initial, top(), 0.5f)
        }
        compose.onNodeWithTag("pull-surface").performTouchInput {
            down(Offset(width / 2f, 25f))
            moveBy(Offset(0f, 40f))
            up()
        }
        compose.runOnIdle { assertEquals(3, requests) }
        assertEquals(initial, top(), 0.5f)
    }

    @Test fun cancellingAPullDoesNotLeaveTheHeaderAtTheTop() {
        showRefresh()
        val initial = top()
        compose.onNodeWithTag("pull-surface").performTouchInput {
            down(Offset(width / 2f, 25f))
            moveBy(Offset(0f, 190f))
            cancel()
        }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithContentDescription("下拉刷新").assertDoesNotExist()
        assertEquals(initial, top(), 0.5f)
        compose.onNodeWithTag("pull-surface").performTouchInput { swipeDown() }
        compose.runOnIdle {
            assertEquals(1, requests)
            refreshing.value = false
        }
        assertEquals(initial, top(), 0.5f)
    }

    @Test fun scrollingAwayFromTheTopAndInactivePreviewsCannotRefresh() {
        val enabled = mutableStateOf(true)
        showRefresh(enabled)
        compose.onNodeWithTag("pull-list").performScrollToIndex(2)
        compose.onNodeWithTag("pull-surface").performTouchInput {
            down(Offset(width / 2f, 25f))
            moveBy(Offset(0f, 60f))
            up()
        }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("pull-list").performScrollToIndex(0)
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("pull-surface").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun incrementalRoundWatchPullDoesNotFightTheMovingContent() {
        showRefresh(round = true)
        compose.onNodeWithTag("pull-surface").performTouchInput { down(Offset(width / 2f, 25f)) }
        var previous = top()
        repeat(8) {
            compose.onNodeWithTag("pull-surface").performTouchInput {
                advanceEventTime(24)
                moveBy(Offset(0f, 20f))
            }
            val position = top()
            assertTrue("Each incremental move must move the cards downward", position > previous)
            assertTrue("Pull offset must be damped, not jump beyond finger travel", position - previous <= 20f)
            previous = position
            assertEquals(0, requests)
        }
        compose.onNodeWithTag("pull-surface").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun originalPackedAnimationIsParseableAndDifferentFramesAreRendered() {
        val bytes = compose.activity.resources.openRawResource(R.raw.official_pull_refresh).use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals("3637997f64fe111dcf884ef13acdaabfa550ceaff5319aa7de09d784b3efb7fd", hash)
        val result = LottieCompositionFactory.fromZipStreamSync(ZipInputStream(bytes.inputStream()), null)
        assertNull(result.exception)
        val composition = requireNotNull(result.value)
        assertEquals(200, composition.bounds.width())
        assertEquals(200, composition.bounds.height())
        assertEquals(60f, composition.frameRate, 0.01f)
        val drawable = LottieDrawable().apply {
            setComposition(composition)
            setBounds(0, 0, 100, 100)
        }
        fun frame(progress: Float): Pair<android.graphics.Bitmap, IntArray> {
            drawable.progress = progress
            val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
            drawable.draw(android.graphics.Canvas(bitmap))
            val pixels = IntArray(10_000)
            bitmap.getPixels(pixels, 0, 100, 0, 0, 100, 100)
            return bitmap to pixels
        }
        val first = frame(0.2f)
        val second = frame(0.7f)
        assertTrue(first.second.any { it != 0 })
        assertFalse("The original animation must change between frames", first.second.contentEquals(second.second))
        val output = File("build/outputs/ui-regression/official-refresh-frame.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { second.first.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun realByteStringPreservesTheNativeCompatibilitySignatures() {
        val data = byteArrayOf(1, 2, 3)
        val bytes = okio.ByteString.of(*data)
        assertEquals(3, bytes.size())
        assertArrayEquals(data, bytes.toByteArray())
        assertTrue(okhttp3.f0().send(bytes))
        assertNotNull(okhttp3.g0::class.java.getMethod("i", okhttp3.f0::class.java, okio.ByteString::class.java))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun composeHeaderActuallyAnimatesAndPausesOffScreen() {
        val composition = LottieCompositionFactory.fromRawResSync(compose.activity,
            R.raw.official_pull_refresh).value
        assertNotNull(composition)
        compose.mainClock.autoAdvance = false
        val active = mutableStateOf(true)
        refreshing.value = true
        showRefresh(active, MotionLevel.REDUCED)
        compose.mainClock.advanceTimeBy(400)
        val first = renderPixels()
        compose.mainClock.advanceTimeBy(500)
        val second = renderPixels()
        assertFalse("The Compose header must play, not just show one parsed frame", first.contentEquals(second))
        compose.runOnIdle { active.value = false }
        compose.mainClock.advanceTimeBy(32)
        val paused = renderPixels()
        compose.mainClock.advanceTimeBy(1_000)
        assertArrayEquals("An inactive return preview must not run the refresh animation", paused, renderPixels())
    }

    private fun showRefresh(enabled: androidx.compose.runtime.State<Boolean> = mutableStateOf(true),
                            motionLevel: Int = MotionLevel.OFF, round: Boolean = false,
                            onRequest: () -> Unit = { refreshing.value = true }) {
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(round).copy(motionLevel = motionLevel)) {
                OfficialPullRefreshBox(refreshing.value, enabled.value, enabled.value,
                    onRefresh = { requests++; onRequest() },
                    modifier = Modifier.testTag("pull-surface")) {
                    listState = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().testTag("pull-list"), state = listState) {
                        items(5) { index ->
                            Box(Modifier.fillMaxWidth().height(180.dp).testTag("row-$index")) {
                                Text("帖子 $index")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun top(): Float = compose.onNodeWithTag("row-0").fetchSemanticsNode().boundsInRoot.top

    private fun renderPixels(): IntArray = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height,
            android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        IntArray(bitmap.width * bitmap.height).also { pixels ->
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
    }
}
