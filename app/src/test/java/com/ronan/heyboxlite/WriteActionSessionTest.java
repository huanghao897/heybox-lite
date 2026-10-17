package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, instrumentedPackages = "com.ronan.heyboxlite",
        shadows = {WriteActionSessionTest.Requests.class, WriteActionSessionTest.Tokens.class,
                ApiSessionLifecycleTest.Cookies.class})
public class WriteActionSessionTest {
    private Activity activity;
    private SessionStore session;
    private ApiClient api;
    private WriteActionClient actions;
    private Requests requests;
    private Tokens tokens;
    private final AtomicInteger successes = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private final ApiClient.Callback callback = new ApiClient.Callback() {
        @Override public void onSuccess(JSONObject body) { successes.incrementAndGet(); }
        @Override public void onError(String message) { errors.incrementAndGet(); }
    };

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE)
                .edit().clear().commit();
        ApiSessionLifecycleTest.Cookies.failures = 0;
        session = new SessionStore(activity);
        login("42");
        api = new ApiClient(session);
        WriteTokenProvider provider = new WriteTokenProvider(activity, session, api);
        requests = Shadow.extract(api);
        tokens = Shadow.extract(provider);
        actions = new WriteActionClient(api, session, provider, null);
    }

    @After public void close() {
        actions.close();
        api.close();
        activity.finish();
    }

    @Test public void delayedTokenCannotRetryAnActionAfterAccountSwitch() throws Exception {
        startTokenRefresh();
        session.clearSession();
        login("84");

        tokens.pending.get(0).onReady();

        assertEquals(1, requests.pending.size());
        assertEquals(0, successes.get());
        assertEquals(0, errors.get());
    }

    @Test public void delayedTokenFailureDoesNotReachTheNextAccount() throws Exception {
        startTokenRefresh();
        session.clearSession();
        login("84");

        tokens.pending.get(0).onError("old token failure");

        assertEquals(1, requests.pending.size());
        assertEquals(0, errors.get());
    }

    @Test public void sameAccountTokenRefreshRetriesAndDeliversTheResult() throws Exception {
        startTokenRefresh();

        tokens.pending.get(0).onReady();
        assertEquals(2, requests.pending.size());
        requests.pending.get(1).onSuccess(new JSONObject());

        assertEquals(1, successes.get());
        assertEquals(0, errors.get());
    }

    @Test public void originalResponseCannotStartTokenRefreshAfterSwitchingAccounts() throws Exception {
        actions.like("post", "", true, callback);
        session.clearSession();
        login("84");

        requests.pending.get(0).onError("lack_token");

        assertEquals(0, tokens.pending.size());
        assertEquals(0, errors.get());
    }

    private void startTokenRefresh() {
        actions.like("post", "", true, callback);
        assertEquals(1, requests.pending.size());
        requests.pending.get(0).onError("lack_token");
        assertEquals(1, tokens.pending.size());
    }

    private void login(String id) throws Exception {
        session.saveCookie(SecureStrings.userHeyboxId() + "=" + id + "; "
                + SecureStrings.userPkey() + "=fixture-key");
        session.saveLogin(new JSONObject().put("heyboxid", id));
    }

    @Implements(value = ApiClient.class, isInAndroidSdk = false)
    public static final class Requests {
        final List<ApiClient.Callback> pending = new ArrayList<>();

        @Implementation protected void postForm(String path, Map<String, String> query,
                                               Map<String, String> body, ApiClient.Callback callback) {
            pending.add(callback);
        }
    }

    @Implements(value = WriteTokenProvider.class, isInAndroidSdk = false)
    public static final class Tokens {
        final List<WriteTokenProvider.Callback> pending = new ArrayList<>();

        @Implementation protected void refresh(WriteTokenProvider.Callback callback) {
            pending.add(callback);
        }
    }
}
