package com.ronan.heyboxlite;

import android.app.Application;
import android.content.Context;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import static com.ronan.heyboxlite.DeviceAuthorizationTestFixtures.NEXT_TOKEN;
import static com.ronan.heyboxlite.DeviceAuthorizationTestFixtures.TOKEN;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class,
        shadows = DeviceAuthorizationTestFixtures.Crypto.class,
        instrumentedPackages = "com.ronan.heyboxlite")
public class PresenceTransportTest {
    private Context context;
    private SessionStore session;
    private DeviceAuthorizationStore authorization;
    private final List<String> requestedUrls = new ArrayList<>();

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        DeviceAuthorizationTestFixtures.Crypto.reset();
        session = new SessionStore(context);
        authorization = new DeviceAuthorizationStore(context);
        authorization.clear();
    }

    @Test public void registersAndStoresTokenWithoutSendingOfficialCredentials() throws Exception {
        DeviceAuthorizationTestFixtures.Response response = response(200, issued(TOKEN));
        PresenceTransport.Result result = transport(response).send("{\"versionCode\":222}");

        assertNotNull(result.access);
        assertFalse(result.access.banned);
        assertEquals(TOKEN, authorization.token());
        assertEquals("POST", response.getRequestMethod());
        assertFalse(response.getInstanceFollowRedirects());
        assertEquals(session.presenceDeviceIdentifier(),
                response.getRequestProperty(DeviceAuthorizationStore.DEVICE_ID_HEADER));
        assertNull(response.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertNull(response.getRequestProperty("Cookie"));
        assertNull(response.getRequestProperty("Authorization"));
        assertEquals("https://heyboxlite.xyz/api/presence", requestedUrls.get(0));
        assertTrue(response.disconnected);
    }

    @Test public void registeredHeartbeatUsesTokenWithoutReissuingIt() throws Exception {
        authorization.save(TOKEN);
        DeviceAuthorizationTestFixtures.Response response = response(200, "{\"ok\":true}");
        assertNotNull(transport(response).send("{}").access);
        assertEquals(TOKEN, response.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertEquals(TOKEN, authorization.token());
    }

    @Test public void testDeviceWithoutTokenFailsOnceWithActionableSafeReason() throws Exception {
        DeviceAuthorizationTestFixtures.Response response = response(401,
                "{\"message\":\"secret body must not be used\"}");
        PresenceTransport.Result result = transport(response).send("{}");

        assertNull(result.access);
        assertEquals(401, result.httpStatus);
        assertEquals("http_401", result.diagnosticCode);
        assertTrue(result.errorMessage.contains("后台重新授权"));
        assertFalse(result.errorMessage.contains("secret"));
        assertEquals(1, requestedUrls.size());
        assertEquals("", authorization.token());
    }

    @Test public void staleOrdinaryTokenRetriesRegistrationOnlyAfterDisconnecting() throws Exception {
        authorization.save(TOKEN);
        DeviceAuthorizationTestFixtures.Response rejected = response(401, "");
        DeviceAuthorizationTestFixtures.Response registered = response(200, issued(NEXT_TOKEN));
        registered.onResponse = () -> assertTrue(rejected.disconnected);

        PresenceTransport.Result result = transport(rejected, registered).send("{}");
        assertNotNull(result.access);
        assertEquals(2, requestedUrls.size());
        assertEquals(TOKEN, rejected.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertNull(registered.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertEquals(NEXT_TOKEN, authorization.token());
    }

    @Test public void staleTestAuthorizationNeverLoopsOrPretendsToBeAuthorized() throws Exception {
        authorization.save(TOKEN);
        PresenceTransport.Result result = transport(response(401, ""), response(401, "")).send("{}");
        assertNull(result.access);
        assertEquals(401, result.httpStatus);
        assertEquals(2, requestedUrls.size());
        assertEquals("", authorization.token());
    }

    @Test public void oldUnauthorizedResponseDoesNotDeleteReplacementToken() throws Exception {
        authorization.save(TOKEN);
        DeviceAuthorizationTestFixtures.Response rejected = response(401, "");
        rejected.onResponse = () -> {
            try {
                new DeviceAuthorizationStore(context).save(NEXT_TOKEN);
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        };
        DeviceAuthorizationTestFixtures.Response accepted = response(200, "{\"ok\":true}");

        assertNotNull(transport(rejected, accepted).send("{}").access);
        assertEquals(NEXT_TOKEN, authorization.token());
        assertEquals(NEXT_TOKEN, accepted.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
    }

    @Test public void timeoutDnsAndTlsAreDistinctWithoutExposingExceptionText() throws Exception {
        DeviceAuthorizationTestFixtures.Response timeout = response(200, "");
        timeout.failure = new SocketTimeoutException(TOKEN);
        DeviceAuthorizationTestFixtures.Response dns = response(200, "");
        dns.failure = new UnknownHostException(TOKEN);
        DeviceAuthorizationTestFixtures.Response tls = response(200, "");
        tls.failure = new SSLHandshakeException(TOKEN);
        for (DeviceAuthorizationTestFixtures.Response response : Arrays.asList(timeout, dns, tls)) {
            PresenceTransport.Result result = transport(response).send("{}");
            assertNull(result.access);
            assertFalse(result.errorMessage.contains(TOKEN));
            assertTrue(response.disconnected);
        }
        assertEquals("timeout", transport(timeout).send("{}").diagnosticCode);
        assertEquals("dns_failed", transport(dns).send("{}").diagnosticCode);
        assertEquals("tls_failed", transport(tls).send("{}").diagnosticCode);
    }

    @Test public void temporaryServerFailuresKeepTokenAndDoNotRetryImmediately() throws Exception {
        authorization.save(TOKEN);
        for (int status : new int[] {429, 502, 503}) {
            int before = requestedUrls.size();
            PresenceTransport.Result result = transport(response(status, TOKEN)).send("{}");
            assertNull(result.access);
            assertEquals("http_" + status, result.diagnosticCode);
            assertEquals(before + 1, requestedUrls.size());
            assertEquals(TOKEN, authorization.token());
        }
    }

    @Test public void redirectIsNotFollowedWithDeviceToken() throws Exception {
        authorization.save(TOKEN);
        DeviceAuthorizationTestFixtures.Response redirect = response(302, "");
        PresenceTransport.Result result = transport(redirect).send("{}");
        assertNull(result.access);
        assertEquals(1, requestedUrls.size());
        assertFalse(redirect.getInstanceFollowRedirects());
        assertEquals(TOKEN, authorization.token());
    }

    @Test public void missingMalformedAndOversizedResponsesNeverBecomeReady() throws Exception {
        for (String body : new String[] {"", "<html>challenge</html>", "{}",
                "{\"ok\":false}", "{\"ok\":true}", issued("invalid-token"),
                "{\"ok\":true,\"padding\":\"" + new String(new char[20_000]).replace('\0', 'x') + "\"}"}) {
            assertNull(transport(response(200, body)).send("{}").access);
        }
        assertEquals("", authorization.token());
    }

    @Test public void tokenPersistenceFailureIsNotSuccessfulRegistration() throws Exception {
        DeviceAuthorizationTestFixtures.Crypto.encryptFails = true;
        PresenceTransport.Result result = transport(response(200, issued(TOKEN))).send("{}");
        assertNull(result.access);
        assertEquals("token_storage_failed", result.diagnosticCode);
        assertEquals("", authorization.token());
    }

    @Test public void bannedStatusStillReachesAccessEnforcementWithoutToken() throws Exception {
        PresenceTransport.Result result = transport(response(200,
                "{\"ok\":true,\"banned\":true,\"message\":\"blocked\"}")).send("{}");
        assertNotNull(result.access);
        assertTrue(result.access.banned);
        assertEquals("blocked", result.access.message);
        assertEquals("", authorization.token());
    }

    private PresenceTransport transport(DeviceAuthorizationTestFixtures.Response... responses) {
        ArrayDeque<DeviceAuthorizationTestFixtures.Response> queue = new ArrayDeque<>(Arrays.asList(responses));
        return new PresenceTransport(session, authorization, url -> {
            requestedUrls.add(url.toExternalForm());
            assertFalse("Unexpected extra registration request", queue.isEmpty());
            return queue.removeFirst();
        });
    }

    private static DeviceAuthorizationTestFixtures.Response response(int status, String body)
            throws Exception {
        return new DeviceAuthorizationTestFixtures.Response(status, body);
    }

    private static String issued(String token) throws Exception {
        return new JSONObject().put("ok", true).put("deviceToken", token).toString();
    }
}
