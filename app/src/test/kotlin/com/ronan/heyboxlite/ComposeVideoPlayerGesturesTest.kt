package com.ronan.heyboxlite

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeVideoPlayerGesturesTest {
    @Test fun singleTapIsConfirmedAndDoubleTapDoesNotAlsoToggleChrome() {
        val fixture = Fixture()
        fixture.tap()
        assertEquals(0, fixture.taps)
        advance(ViewConfiguration.getDoubleTapTimeout() + 20L)
        assertEquals(1, fixture.taps)
        fixture.tap()
        advance(70)
        fixture.tap()
        advance(ViewConfiguration.getDoubleTapTimeout() + 20L)
        assertEquals(1, fixture.doubles)
        assertEquals(1, fixture.taps)
    }

    @Test fun realLongPressEndsExactlyOnceOnReleaseOrCancellation() {
        listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL).forEach { end ->
            val fixture = Fixture()
            fixture.down()
            advance(longPressDelay())
            assertEquals(1, fixture.starts)
            assertEquals(0, fixture.ends)
            fixture.send(end)
            assertEquals(1, fixture.ends)
            fixture.send(MotionEvent.ACTION_CANCEL)
            fixture.gestures.pause()
            assertEquals(1, fixture.ends)
            advance(400)
            assertEquals(0, fixture.taps)
            fixture.gestures.close()
        }
    }

    @Test fun preferenceDisabledAndRejectedFastForwardNeverProduceAFalseEnd() {
        val disabled = Fixture(longPress = false)
        disabled.down()
        advance(longPressDelay())
        disabled.send(MotionEvent.ACTION_UP)
        assertEquals(0, disabled.starts)
        assertEquals(0, disabled.ends)
        val unprepared = Fixture(accepted = false)
        unprepared.down()
        advance(longPressDelay())
        unprepared.send(MotionEvent.ACTION_UP)
        assertEquals(1, unprepared.starts)
        assertEquals(0, unprepared.ends)
    }

    @Test fun pauseRestoresFastForwardAndResumeAcceptsFreshTouchesOnly() {
        val fixture = Fixture()
        fixture.down()
        advance(longPressDelay())
        fixture.gestures.pause()
        assertEquals(1, fixture.ends)
        assertFalse(fixture.send(MotionEvent.ACTION_UP))
        fixture.gestures.resume()
        fixture.tap()
        advance(ViewConfiguration.getDoubleTapTimeout() + 20L)
        assertEquals(1, fixture.taps)
        assertEquals(1, fixture.ends)
    }

    @Test fun closeCancelsDeferredSingleTapAndLongPressEvenIfResumeIsCalled() {
        val tap = Fixture()
        tap.tap()
        tap.gestures.close()
        tap.gestures.resume()
        advance(1_000)
        assertEquals(0, tap.taps)
        assertFalse(tap.send(MotionEvent.ACTION_DOWN))
        val press = Fixture()
        press.down()
        press.gestures.close()
        advance(longPressDelay())
        assertEquals(0, press.starts)
        assertEquals(0, press.ends)
    }

    @Test fun closeWhileFastForwardingRestoresPlaybackBeforeInvalidatingGestures() {
        val fixture = Fixture()
        fixture.down()
        advance(longPressDelay())
        fixture.gestures.close()
        fixture.gestures.close()
        assertFalse(fixture.send(MotionEvent.ACTION_UP))
        assertEquals(1, fixture.starts)
        assertEquals(1, fixture.ends)
    }

    private class Fixture(longPress: Boolean = true, accepted: Boolean = true) {
        var taps = 0
        var doubles = 0
        var starts = 0
        var ends = 0
        private var downAt = SystemClock.uptimeMillis()
        val gestures = ComposeVideoPlayerGestures(
            ApplicationProvider.getApplicationContext<Application>(), longPress,
            { taps++ }, { doubles++ }, { starts++; accepted }, { ends++ })

        fun down() { downAt = SystemClock.uptimeMillis(); send(MotionEvent.ACTION_DOWN) }
        fun tap() { down(); send(MotionEvent.ACTION_UP) }
        fun send(action: Int): Boolean {
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, 80f, 100f, 0)
            return try { gestures.onTouch(event) } finally { event.recycle() }
        }
    }

    private fun longPressDelay() = ViewConfiguration.getLongPressTimeout().toLong() +
        ViewConfiguration.getTapTimeout() + 20
    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
}
