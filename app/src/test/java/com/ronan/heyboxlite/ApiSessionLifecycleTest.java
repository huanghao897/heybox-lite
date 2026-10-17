package com.ronan.heyboxlite;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class,
        shadows = ApiSessionLifecycleTest.Cookies.class,
        instrumentedPackages = "com.ronan.heyboxlite")
@LooperMode(LooperMode.Mode.PAUSED)
public class ApiSessionLifecycleTest {
    private Context context;
    private SessionStore session;
    private final List<ApiClient> clients = new ArrayList<>();

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE)
                .edit().clear().commit();
        Cookies.failures = 0;
        Cookies.decryptCalls = 0;
        session = new SessionStore(context);
    }

    @After public void closeClients() {
        for (ApiClient client : clients) client.close();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void retriesAfterTemporaryKeyFailureInTheSameStore() throws Exception {
        login("42");
        SessionStore reader = new SessionStore(context);
        Cookies.failures = 1;

        assertEquals("", reader.getCookie());
        assertTrue(reader.getCookie().contains("fixture-key"));
        assertEquals(2, Cookies.decryptCalls);
        assertTrue(reader.isLoggedIn());
    }

    @Test public void partialResponseDoesNotOverwriteCookieWhileKeyIsUnavailable() throws Exception {
        login("42");
        SessionStore reader = new SessionStore(context);
        Cookies.failures = 1;
        long generation = reader.sessionGeneration();

        assertEquals(generation, reader.mergeCookies(
                Collections.singletonList("extra=new"), generation));

        assertTrue(reader.getCookie().contains("fixture-key"));
        assertFalse(reader.getCookie().contains("extra=new"));
    }

    @Test public void logoutRejectsResponseCookiesAcrossStoreInstances() throws Exception {
        login("42");
        long generation = session.sessionGeneration();
        new SessionStore(context).clearSession();

        assertEquals(-1L, session.mergeCookies(headers("42"), generation));
        assertEquals("", session.getCookie());
        assertEquals("", session.userId());
        assertFalse(session.isLoggedIn());
    }

    @Test public void loginReplacementRejectsOldResponseAcrossStoreInstances() throws Exception {
        login("42");
        long generation = session.sessionGeneration();
        SessionStore other = new SessionStore(context);
        other.saveCookie(cookie("84"));
        other.saveLogin(new JSONObject().put("heyboxid", "84"));

        assertEquals(-1L, session.mergeCookies(headers("42"), generation));
        assertEquals("84", session.userId());
        assertEquals("84", SessionCookieCodec.userId(session.getCookie()));
    }

    @Test public void identityChangingResponseInvalidatesOtherPendingResponsesImmediately() throws Exception {
        login("42");
        long generation = session.sessionGeneration();

        long responseGeneration = session.mergeCookies(headers("84"), generation);

        assertTrue(responseGeneration > generation);
        assertTrue(session.isCurrentSession(responseGeneration));
        assertEquals(-1L, session.mergeCookies(headers("42"), generation));
        assertEquals("84", SessionCookieCodec.userId(session.getCookie()));
    }

    @Test public void concurrentSameAccountCookieUpdatesRetainBothValues() throws Exception {
        login("42");
        long generation = session.sessionGeneration();
        SessionStore other = new SessionStore(context);
        CountDownLatch start = new CountDownLatch(1);
        Thread first = mergeThread(session, generation, "first=one", start);
        Thread second = mergeThread(other, generation, "second=two", start);
        first.start();
        second.start();
        start.countDown();
        first.join(5000);
        second.join(5000);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(generation, session.sessionGeneration());
        assertEquals("one", SessionCookieCodec.value(session.getCookie(), "first"));
        assertEquals("two", SessionCookieCodec.value(session.getCookie(), "second"));
    }

    @Test public void inFlightResponseCannotRestoreLoggedOutSession() throws Exception {
        login("42");
        Response response = new Response(headers("42"), false);
        AtomicInteger callbacks = request(response, null);
        await(response.started);

        new SessionStore(context).clearSession();
        response.release.countDown();
        await(response.finished);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, callbacks.get());
        assertFalse(session.isLoggedIn());
        assertEquals("", session.getCookie());
    }

    @Test public void postedSuccessCannotRestoreLoginAfterLogout() throws Exception {
        login("42");
        Response response = new Response(headers("42"), true);
        AtomicInteger callbacks = request(response, () -> saveLogin("42"));
        await(response.finished);

        new SessionStore(context).clearSession();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, callbacks.get());
        assertFalse(session.isLoggedIn());
    }

    @Test public void postedErrorDoesNotReachTheNextSession() throws Exception {
        login("42");
        ApiRequestDispatcher dispatcher = new ApiRequestDispatcher(session);
        AtomicInteger errors = new AtomicInteger();
        try {
            dispatcher.postError(new ApiClient.Callback() {
                @Override public void onSuccess(JSONObject body) { fail("unexpected success"); }
                @Override public void onError(String message) { errors.incrementAndGet(); }
            }, "old request", session.sessionGeneration());
            new SessionStore(context).clearSession();
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals(0, errors.get());
        } finally {
            dispatcher.close();
        }
    }

    @Test public void identityChangingLoginResponseStillReachesSaveLogin() throws Exception {
        login("42");
        Response response = new Response(headers("84"), true);
        AtomicInteger callbacks = request(response, () -> saveLogin("84"));
        await(response.finished);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, callbacks.get());
        assertEquals("84", session.userId());
        assertEquals("84", SessionCookieCodec.userId(session.getCookie()));
    }

    @Test public void sameAccountTokenRotationPreservesOtherPendingRequests() throws Exception {
        login("42");
        long generation = session.sessionGeneration();
        Response first = new Response(Collections.singletonList(
                SecureStrings.xXhhTokenId() + "=rotated"), false);
        Response second = new Response(Collections.singletonList("extra=kept"), false);
        AtomicInteger firstCallbacks = request(first, null);
        AtomicInteger secondCallbacks = request(second, null);
        await(first.started);
        await(second.started);

        first.release.countDown();
        await(first.finished);
        second.release.countDown();
        await(second.finished);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(generation, session.sessionGeneration());
        assertEquals(1, firstCallbacks.get());
        assertEquals(1, secondCallbacks.get());
        assertEquals("rotated", SessionCookieCodec.value(session.getCookie(), SecureStrings.xXhhTokenId()));
        assertEquals("kept", SessionCookieCodec.value(session.getCookie(), "extra"));
    }

    @Test public void switchingAccountsDuringConnectionSetupCannotSendWithTheNewCookie() throws Exception {
        login("42");
        Response response = new Response(Collections.emptyList(), true);
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch releaseOpen = new CountDownLatch(1);
        ApiClient client = new ApiClient(session, null, url -> {
            opening.countDown();
            try {
                if (!releaseOpen.await(5, TimeUnit.SECONDS)) throw new IOException("fixture timeout");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException(error);
            }
            return response;
        });
        clients.add(client);
        AtomicInteger callbacks = new AtomicInteger();
        client.postForm("/fixture/write/", Collections.singletonMap("text", "old account"),
                countingCallback(callbacks));
        await(opening);

        SessionStore other = new SessionStore(context);
        other.clearSession();
        other.saveCookie(cookie("84"));
        other.saveLogin(new JSONObject().put("heyboxid", "84"));
        releaseOpen.countDown();
        await(response.finished);
        shadowOf(Looper.getMainLooper()).idle();

        String appliedCookie = response.getRequestProperty(SecureStrings.cookieHeader());
        assertEquals("42", SessionCookieCodec.userId(appliedCookie));
        assertEquals(0, response.outputOpened.get());
        assertEquals(1L, response.started.getCount());
        assertEquals(0, callbacks.get());
        assertEquals("84", session.userId());
    }

    @Test public void currentAccountPostStillSendsAndReceivesItsResult() throws Exception {
        login("42");
        Response response = new Response(Collections.emptyList(), true);
        ApiClient client = new ApiClient(session, null, url -> response);
        clients.add(client);
        AtomicInteger callbacks = new AtomicInteger();

        client.postForm("/fixture/write/", Collections.singletonMap("text", "current account"),
                countingCallback(callbacks));
        await(response.finished);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("42", SessionCookieCodec.userId(
                response.getRequestProperty(SecureStrings.cookieHeader())));
        assertEquals(1, response.outputOpened.get());
        assertEquals(1, callbacks.get());
    }

    private void login(String id) throws Exception {
        session.saveCookie(cookie(id));
        session.saveLogin(new JSONObject().put("heyboxid", id));
    }

    private void saveLogin(String id) {
        try {
            session.saveLogin(new JSONObject().put("heyboxid", id));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private String cookie(String id) {
        return SecureStrings.userHeyboxId() + "=" + id + "; "
                + SecureStrings.userPkey() + "=fixture-key";
    }

    private List<String> headers(String id) {
        return Arrays.asList(SecureStrings.userHeyboxId() + "=" + id + "; Path=/",
                SecureStrings.userPkey() + "=response-key; Path=/");
    }

    private AtomicInteger request(Response response, Runnable onSuccess) {
        ApiClient client = new ApiClient(session, null, url -> response);
        clients.add(client);
        AtomicInteger callbacks = new AtomicInteger();
        client.get("/fixture/session/", Collections.emptyMap(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                callbacks.incrementAndGet();
                if (onSuccess != null) onSuccess.run();
            }

            @Override public void onError(String message) {
                throw new AssertionError(message);
            }
        });
        return callbacks;
    }

    private ApiClient.Callback countingCallback(AtomicInteger callbacks) {
        return new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) { callbacks.incrementAndGet(); }
            @Override public void onError(String message) { throw new AssertionError(message); }
        };
    }

    private Thread mergeThread(SessionStore store, long generation, String header, CountDownLatch start) {
        return new Thread(() -> {
            try {
                if (!start.await(5, TimeUnit.SECONDS)) return;
                store.mergeCookies(Collections.singletonList(header), generation);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("request did not reach its expected phase", latch.await(5, TimeUnit.SECONDS));
    }

    private static final class Response extends HttpURLConnection {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release;
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicInteger outputOpened = new AtomicInteger();
        private final List<String> cookies;

        Response(List<String> cookies, boolean ready) throws Exception {
            super(new URL("https://fixture.invalid/"));
            this.cookies = cookies;
            release = new CountDownLatch(ready ? 0 : 1);
        }

        @Override public int getResponseCode() throws IOException {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("fixture timeout");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException(error);
            }
            return 200;
        }

        @Override public Map<String, List<String>> getHeaderFields() {
            return Collections.singletonMap("Set-Cookie", cookies);
        }

        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream("{\"status\":\"ok\",\"result\":{}}".getBytes());
        }

        @Override public OutputStream getOutputStream() {
            outputOpened.incrementAndGet();
            return new ByteArrayOutputStream();
        }

        @Override public void connect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { finished.countDown(); }
    }

    @Implements(value = ModernCookieCrypto.class, isInAndroidSdk = false)
    public static final class Cookies {
        static int failures;
        static int decryptCalls;

        @Implementation protected static String encrypt(String value) {
            return "fixture:" + value;
        }

        @Implementation protected static String decrypt(String value) throws Exception {
            decryptCalls++;
            if (failures-- > 0) throw new ModernCookieCrypto.KeyUnavailableException("fixture unavailable");
            return value.substring("fixture:".length());
        }
    }
}
