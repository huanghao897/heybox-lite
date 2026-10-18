package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.webkit.WebView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowWebView;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, instrumentedPackages = "com.ronan.heyboxlite",
        shadows = {ApiSessionLifecycleTest.Cookies.class, WriteTokenProviderFailureTest.UnavailableWebView.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class WriteTokenProviderFailureTest {
    private Activity activity;
    private ApiClient api;
    private WriteTokenProvider provider;
    private final List<String> errors = new ArrayList<>();
    private final WriteTokenProvider.Callback callback = new WriteTokenProvider.Callback() {
        @Override public void onReady() { throw new AssertionError("Unavailable WebView cannot produce a token"); }
        @Override public void onError(String message) { errors.add(message); }
    };

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE)
                .edit().clear().commit();
        ApiSessionLifecycleTest.Cookies.failures = 0;
        UnavailableWebView.linkageFailure = false;
        SessionStore session = new SessionStore(activity);
        api = new ApiClient(session);
        provider = new WriteTokenProvider(activity, session, api);
    }

    @After public void close() {
        provider.close();
        shadowOf(Looper.getMainLooper()).idle();
        api.close();
        activity.finish();
    }

    @Test public void missingProviderFailsOnceWithoutRetainingPendingWork() {
        assertInitializationFailure();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        assertEquals(1, errors.size());

        provider.refresh(callback);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("A failed fetch must not leave the next request stuck", 2, errors.size());
    }

    @Test public void brokenNativeProviderAlsoUsesTheErrorCallback() {
        UnavailableWebView.linkageFailure = true;
        assertInitializationFailure();
    }

    private void assertInitializationFailure() {
        provider.refresh(callback);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, errors.size());
        assertEquals("当前系统的 WebView 不可用，暂时无法完成此操作", errors.get(0));
        assertFalse(ReflectionHelpers.getField(provider, "fetching"));
        assertNull(ReflectionHelpers.getField(provider, "webView"));
        assertNull(ReflectionHelpers.getField(provider, "timeoutTask"));
        assertEquals(0, ((List<?>) ReflectionHelpers.getField(provider, "pending")).size());
    }

    @Implements(WebView.class)
    public static class UnavailableWebView extends ShadowWebView {
        static boolean linkageFailure;

        @Implementation protected void __constructor__(Context context) {
            if (linkageFailure) throw new UnsatisfiedLinkError("fixture unavailable");
            throw new UnsupportedOperationException("fixture unavailable");
        }
    }
}
