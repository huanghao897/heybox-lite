package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.airbnb.lottie.LottieCompositionFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w227dp-h227dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfficialRefreshWatchPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun refreshKeepsMovingWhenWatchSystemAnimationsAreDisabled() {
        val original = Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            assertRefreshPlayback(MotionLevel.REDUCED, "round-system-motion-off")
        } finally {
            Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, original)
        }
    }

    @Test fun lowMemoryWatchMotionDefaultDoesNotFreezeRefreshFeedback() {
        assertRefreshPlayback(MotionLevel.OFF, "round-app-motion-off")
    }

    @Test
    @Config(qualifiers = "w240dp-h320dp-mdpi")
    fun squareWatchAlsoAnimatesWithAppAndSystemMotionDisabled() {
        val original = Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            assertRefreshPlayback(MotionLevel.OFF, "square-motion-off", round = false)
        } finally {
            Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, original)
        }
    }

    private fun assertRefreshPlayback(motionLevel: Int, name: String, round: Boolean = true) {
        assertNotNull(LottieCompositionFactory.fromRawResSync(compose.activity, R.raw.official_pull_refresh).value)
        compose.mainClock.autoAdvance = false
        val active = mutableStateOf(true)
        val refreshing = mutableStateOf(true)
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(round).copy(motionLevel = motionLevel)) {
                OfficialPullRefreshBox(refreshing.value, false, active.value, onRefresh = {}) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val first = pixels("$name-first")
        compose.mainClock.advanceTimeBy(500)
        val second = pixels("$name-second")
        assertFalse("Refresh must change actual pixels, not stay on a static frame", first.contentEquals(second))
        compose.runOnIdle { active.value = false }
        compose.mainClock.advanceTimeBy(32)
        val paused = pixels()
        compose.mainClock.advanceTimeBy(700)
        assertArrayEquals("Background/return-preview headers must stop drawing animation frames", paused, pixels())
        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeBy(32)
        val resumed = pixels()
        compose.mainClock.advanceTimeBy(500)
        assertFalse("Returning to a refreshing page must resume playback", resumed.contentEquals(pixels()))
        compose.runOnIdle { refreshing.value = false }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("下拉刷新").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        val hidden = pixels()
        compose.mainClock.advanceTimeBy(700)
        assertArrayEquals("Finished refresh must not keep animating off screen", hidden, pixels())
    }

    private fun pixels(name: String? = null): IntArray = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        if (name != null) {
            val file = File("build/outputs/ui-regression/refresh-$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
    }
}
