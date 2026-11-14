package com.ronan.heyboxlite;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class CrownHapticProviderTest {
    private ActivityController<Activity> controller;
    private SessionStore session;
    private RecordingView view;
    private CrownHapticProvider provider;
    private Field component;
    private Object previous;

    @Before public void setUp() throws Exception {
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        session = new SessionStore(activity);
        session.setCrownHapticsEnabled(true);
        view = new RecordingView(activity);
        provider = new CrownHapticProvider(activity, session);
        SystemClock.sleep(100);
        component = CnWearOverlay.class.getDeclaredField("activeComponent");
        component.setAccessible(true);
        previous = component.get(null);
    }

    @After public void tearDown() throws Exception {
        component.set(null, previous);
        controller.pause().stop().destroy();
    }

    @Test public void legacyFailureDoesNotSendAnUnrelatedFallbackWaveform() throws Exception {
        use("OppoLegacyHaptics");
        provider.performScrollTick(view);
        assertEquals(0, view.calls);
    }

    @Test public void vendorMotorPermissionIsDeclaredWithoutRuntimePrompt() throws Exception {
        Activity activity = controller.get();
        String[] permissions = activity.getPackageManager().getPackageInfo(
                activity.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertNotNull(permissions);
        assertTrue(java.util.Arrays.asList(permissions).contains(android.Manifest.permission.VIBRATE));
    }

    @Test public void modernOppoUsesItsNativeTickOnlyOnce() throws Exception {
        use("OppoModernHaptics");
        provider.performScrollTick(view);
        assertEquals(1, view.calls);
        assertEquals(12, view.constant);
    }

    @Test public void xiaomiUsesItsCrownTickRatherThanTheGenericClockTick() throws Exception {
        use("XiaomiHaptics");
        provider.performScrollTick(view);
        assertEquals(1, view.calls);
        assertEquals(18, view.constant);
    }

    @Test public void unsupportedPlatformsKeepSystemFeedback() throws Exception {
        use("BaseOverlay");
        provider.performScrollTick(view);
        assertEquals(1, view.calls);
        assertEquals(4, view.constant);
    }

    @Test public void disabledSettingNeverCallsTheVendorOrSystem() throws Exception {
        use("OppoModernHaptics");
        session.setCrownHapticsEnabled(false);
        provider.performScrollTick(view);
        assertEquals(0, view.calls);
    }

    @Test public void rapidRepeatedEventsKeepTheExistingFeedbackRateLimit() throws Exception {
        use("OppoModernHaptics");
        provider.performScrollTick(view);
        provider.performScrollTick(view);
        assertEquals(1, view.calls);
        SystemClock.sleep(40);
        provider.performScrollTick(view);
        assertEquals(2, view.calls);
    }

    private void use(String name) throws Exception {
        Constructor<?> constructor = Class.forName(
                "cc.star0.wear.lib.cnwearoverlay.runtime." + name).getDeclaredConstructor();
        constructor.setAccessible(true);
        component.set(null, constructor.newInstance());
    }

    private static final class RecordingView extends View {
        int calls;
        int constant;

        RecordingView(Activity activity) { super(activity); }

        @Override public boolean performHapticFeedback(int value) {
            calls++;
            constant = value;
            return true;
        }
    }
}
