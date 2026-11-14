package cc.star0.wear.lib.cnwearoverlay.runtime;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class CnWearOverlayRuntimeTest {
    private Field component;
    private Object previous;

    @Before public void saveComponent() throws Exception {
        component = CnWearOverlay.class.getDeclaredField("activeComponent");
        component.setAccessible(true);
        previous = component.get(null);
    }

    @After public void restoreComponent() throws Exception {
        component.set(null, previous);
    }

    @Test public void inactivePlatformPreservesInputAndNativeHaptics() throws Exception {
        component.set(null, new BaseOverlay());
        RecordingView view = new RecordingView(RuntimeEnvironment.getApplication());
        assertFalse(CnWearOverlay.isActive());
        assertNull(CnWearOverlay.rotaryConstants(Object.class));
        assertTrue(CnWearOverlay.performHapticFeedback(view, 4));
        assertEquals(4, view.constant);
        MotionEvent original = event(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE, 1f, 1);
        try {
            assertSame(original, CnWearOverlay.rotaryEvent(original));
            CnWearOverlay.recycleRotaryEvent(original, original);
            assertEquals(InputDevice.SOURCE_MOUSE, original.getSource());
        } finally { original.recycle(); }
    }

    @Test public void legacyOppoConvertsAnUnnamedMouseCrownExactlyOnce() throws Exception {
        component.set(null, new OppoLegacyHaptics());
        assertTrue(CnWearOverlay.isOppoLegacy());
        assertTrue(CnWearOverlay.isActive());
        assertEquals(12, CnWearOverlay.getScrollTick());
        MotionEvent original = event(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE, -.25f, 1);
        MotionEvent mapped = CnWearOverlay.rotaryEvent(original);
        try {
            assertNotSame(original, mapped);
            assertEquals(0x00400000, mapped.getSource());
            assertEquals(-.25f, mapped.getAxisValue(26), 0f);
            assertEquals(original.getDownTime(), mapped.getDownTime());
            assertEquals(original.getEventTime(), mapped.getEventTime());
            assertEquals(original.getDeviceId(), mapped.getDeviceId());
            assertSame(mapped, CnWearOverlay.rotaryEvent(mapped));
            assertEquals(InputDevice.SOURCE_MOUSE, original.getSource());
            assertEquals(0f, original.getAxisValue(26), 0f);
        } finally {
            CnWearOverlay.recycleRotaryEvent(original, mapped);
            original.recycle();
        }
    }

    @Test public void modernOppoDoesNotConvertAnUnknownMouse() throws Exception {
        component.set(null, new OppoModernHaptics());
        assertTrue(CnWearOverlay.isOppoModern());
        MotionEvent original = event(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE, .5f, 1);
        try { assertSame(original, CnWearOverlay.rotaryEvent(original)); }
        finally { original.recycle(); }
    }

    @Test public void legacyConversionRejectsInvalidAxesTouchAndMultiplePointers() throws Exception {
        component.set(null, new OppoLegacyHaptics());
        assertUnchanged(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE, 0f, 1);
        assertUnchanged(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE, Float.NaN, 1);
        assertUnchanged(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE, Float.POSITIVE_INFINITY, 1);
        assertUnchanged(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_TOUCHSCREEN, 1f, 1);
        assertUnchanged(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE, 1f, 1);
        assertUnchanged(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE, 1f, 2);
        assertUnchanged(MotionEvent.ACTION_SCROLL, 0x00400000, 1f, 1);
    }

    @Test public void xiaomiRemapsOnlyItsFrequentConstantsAndPreservesFlags() throws Exception {
        component.set(null, new XiaomiHaptics());
        RecordingView view = new RecordingView(RuntimeEnvironment.getApplication());
        assertEquals(18, CnWearOverlay.getScrollTick());
        for (int input : new int[]{4, 27}) {
            assertTrue(CnWearOverlay.performHapticFeedback(view, input, 3));
            assertEquals(26, view.constant);
            assertEquals(3, view.flags);
        }
        assertTrue(CnWearOverlay.performHapticFeedback(view, 18));
        assertEquals(18, view.constant);
        assertTrue(CnWearOverlay.performRotaryHapticFeedback(view, 4));
        assertEquals(4, view.constant);
    }

    @Test public void legacyDetachedViewDoesNotFallBackToTheGenericVibration() throws Exception {
        component.set(null, new OppoLegacyHaptics());
        RecordingView view = new RecordingView(RuntimeEnvironment.getApplication());
        assertFalse(CnWearOverlay.performHapticFeedback(view, 12));
        assertEquals(0, view.calls);
        assertTrue(CnWearOverlay.performHapticFeedback(view, 4));
        assertEquals(4, view.constant);
    }

    private void assertUnchanged(int action, int source, float axis, int pointers) {
        MotionEvent original = event(action, source, axis, pointers);
        try { assertSame(original, CnWearOverlay.rotaryEvent(original)); }
        finally { original.recycle(); }
    }

    private static MotionEvent event(int action, int source, float axis, int count) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int index = 0; index < count; index++) {
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = index;
            coordinates[index] = new MotionEvent.PointerCoords();
            coordinates[index].setAxisValue(MotionEvent.AXIS_VSCROLL, axis);
        }
        return MotionEvent.obtain(1, 20, action, count, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, source, 0);
    }

    private static final class RecordingView extends View {
        int constant;
        int flags;
        int calls;

        RecordingView(Context context) { super(context); }

        @Override public boolean performHapticFeedback(int value) {
            constant = value;
            calls++;
            return true;
        }

        @Override public boolean performHapticFeedback(int value, int options) {
            flags = options;
            return performHapticFeedback(value);
        }
    }
}
