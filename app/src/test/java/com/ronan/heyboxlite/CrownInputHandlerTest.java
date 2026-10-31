package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w240dp-h320dp-mdpi")
public class CrownInputHandlerTest {
    private ActivityController<Activity> activityController;
    private Activity activity;
    private SessionStore session;
    private final RecordingHost host = new RecordingHost();

    @Before
    public void setUp() {
        activityController = Robolectric.buildActivity(Activity.class).setup();
        activity = activityController.get();
        session = new SessionStore(activity);
        session.setCrownHapticsEnabled(false);
    }

    @After
    public void tearDown() {
        activityController.pause().stop().destroy();
    }

    @Test
    public void persistsCustomSpeedAndUsesItForRotaryEventsAfterReload() {
        session.setCrownScrollSpeed(CrownScrollController.SLIDER_MAX_SPEED_PERCENT);
        CrownInputHandler baseline = new CrownInputHandler(activity, session, host);
        assertTrue(rotate(baseline, -1.0f));
        int sliderDistance = host.distance;
        SharedPreferences prefs = preferences();

        for (int speed : new int[]{501, 100_000, Integer.MAX_VALUE}) {
            session.setCrownScrollSpeed(speed);
            assertEquals(speed, prefs.getInt("crown_scroll_speed", 0));
            SessionStore restored = new SessionStore(activity);
            assertEquals(speed, restored.crownScrollSpeed());
            CrownInputHandler handler = new CrownInputHandler(activity, restored, host);
            int step = Math.round(12 * activity.getResources().getDisplayMetrics().density
                    * (restored.uiScale() / 100.0f));

            assertTrue(rotate(handler, -1.0f));
            assertEquals(new CrownScrollController().distance(-1.0f, step, speed,
                    CrownDeviceProfile.scrollAxisGain(activity)), host.distance);
            assertTrue("Custom speed must reach scrolling, not just storage",
                    host.distance > sliderDistance);
            assertTrue(rotate(handler, 1.0f));
            assertTrue(host.distance < -sliderDistance);
        }
    }

    @Test
    public void readsExistingAboveRangePreferencesWithoutChangingThem() {
        assertTrue(preferences().edit().putInt("crown_scroll_speed", 501).commit());

        assertEquals(501, new SessionStore(activity).crownScrollSpeed());
        assertEquals(501, preferences().getInt("crown_scroll_speed", 0));
    }

    @Test
    public void retainsTheExistingMinimumForSavedAndLoadedSpeeds() {
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 1}) {
            session.setCrownScrollSpeed(invalid);
            assertEquals(CrownScrollController.MIN_SPEED_PERCENT, session.crownScrollSpeed());
            assertTrue(preferences().edit().putInt("crown_scroll_speed", invalid).commit());
            assertEquals(CrownScrollController.MIN_SPEED_PERCENT,
                    new SessionStore(activity).crownScrollSpeed());
        }
    }

    @Test
    public void disablingCrownStillLeavesEventsToTheSystem() {
        session.setCrownScrollSpeed(Integer.MAX_VALUE);
        session.setCrownScrollEnabled(false);
        CrownInputHandler handler = new CrownInputHandler(activity, session, host);

        assertFalse(rotate(handler, -1.0f));
        assertEquals(0, host.distance);
    }

    private SharedPreferences preferences() {
        return activity.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE);
    }

    private boolean rotate(CrownInputHandler handler, float axis) {
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.setAxisValue(MotionEvent.AXIS_SCROLL, axis);
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_SCROLL, 1,
                new MotionEvent.PointerProperties[]{pointer},
                new MotionEvent.PointerCoords[]{coordinates},
                0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0);
        try {
            return handler.handle(event);
        } finally {
            event.recycle();
        }
    }

    private static final class RecordingHost implements CrownInputHandler.Host {
        int distance;

        @Override public String screen() { return "feed"; }
        @Override public View detailScrollTarget() { return null; }
        @Override public View feedScrollTarget() { return null; }
        @Override public View searchScrollTarget() { return null; }
        @Override public View contentRoot() { return null; }
        @Override public boolean scrollCompose(int distance) {
            this.distance = distance;
            return true;
        }
    }
}
