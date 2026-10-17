package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Looper;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, instrumentedPackages = "com.ronan.heyboxlite",
        shadows = ApiSessionLifecycleTest.Cookies.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class WriteTokenProviderSessionTest {
    private Activity activity;
    private SessionStore session;
    private ApiClient api;
    private WriteTokenProvider provider;
    private final AtomicInteger successes = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE)
                .edit().clear().commit();
        ApiSessionLifecycleTest.Cookies.failures = 0;
        session = new SessionStore(activity);
        login("42");
        api = new ApiClient(session);
        provider = new WriteTokenProvider(activity, session, api);
        provider.refresh(new WriteTokenProvider.Callback() {
            @Override public void onReady() { successes.incrementAndGet(); }
            @Override public void onError(String message) { errors.incrementAndGet(); }
        });
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ReflectionHelpers.getField(provider, "fetching"));
    }

    @After public void close() {
        provider.close();
        shadowOf(Looper.getMainLooper()).idle();
        api.close();
        activity.finish();
    }

    @Test public void lateDeviceTokenDoesNotEnterTheNextAccountAndReleasesItsWebView() throws Exception {
        session.clearSession();
        login("84");

        deliverDeviceToken();
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(session.hasCookieValue(SecureStrings.xXhhTokenId()));
        assertEquals(0, successes.get());
        assertEquals(0, errors.get());
        assertReleased();
    }

    @Test public void postedTokenSuccessDoesNotReachTheNextAccount() throws Exception {
        deliverDeviceToken();
        assertTrue(session.hasCookieValue(SecureStrings.xXhhTokenId()));
        session.clearSession();
        login("84");

        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(session.hasCookieValue(SecureStrings.xXhhTokenId()));
        assertEquals(0, successes.get());
        assertReleased();
    }

    @Test public void currentAccountAcceptsTheTokenAndReleasesItsWebView() {
        deliverDeviceToken();
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(session.hasCookieValue(SecureStrings.xXhhTokenId()));
        assertEquals(1, successes.get());
        assertEquals(0, errors.get());
        assertReleased();
    }

    private void deliverDeviceToken() {
        String token = "B" + String.join("", java.util.Collections.nCopies(88, "x"));
        ReflectionHelpers.callInstanceMethod(provider, "handleDeviceId", from(String.class, token));
    }

    private void assertReleased() {
        assertFalse(ReflectionHelpers.getField(provider, "fetching"));
        assertNull(ReflectionHelpers.getField(provider, "webView"));
        assertNull(ReflectionHelpers.getField(provider, "timeoutTask"));
    }

    private void login(String id) throws Exception {
        session.saveCookie(SecureStrings.userHeyboxId() + "=" + id + "; "
                + SecureStrings.userPkey() + "=fixture-key");
        session.saveLogin(new JSONObject().put("heyboxid", id));
    }
}
