package com.ronan.heyboxlite;

import android.app.Application;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 28}, application = Application.class,
        shadows = DeviceAuthorizationTestFixtures.Crypto.class,
        instrumentedPackages = "com.ronan.heyboxlite")
@LooperMode(LooperMode.Mode.PAUSED)
public class MetadataClientBoundaryTest {
    private SessionStore session;

    @Before public void setUp() throws Exception {
        DeviceAuthorizationTestFixtures.Crypto.reset();
        session = new SessionStore(RuntimeEnvironment.getApplication());
        new DeviceAuthorizationStore(session.appContext()).save(DeviceAuthorizationTestFixtures.TOKEN);
    }

    @Test public void configurationRejectsCrossOriginRedirectBeforeTokenCouldBeForwarded() throws Exception {
        Response response = new Response(302, 0);
        List<URL> opened = new ArrayList<>();
        assertFalse(RemoteConfig.request(session, url -> { opened.add(url); return response; }));
        assertEquals(1, opened.size());
        assertEquals("heyboxlite.xyz", opened.get(0).getHost());
        assertFalse(response.getInstanceFollowRedirects());
        assertEquals(DeviceAuthorizationTestFixtures.TOKEN,
                response.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertFalse(response.read);
        assertTrue(response.disconnected);
    }

    @Test public void oversizedConfigurationDoesNotPublishPartialState() throws Exception {
        AccessStatus before = RemoteConfig.accessStatus();
        Response response = new Response(200, MetadataResponse.MAX_BYTES + 1);
        assertFalse(RemoteConfig.request(session, url -> response));
        assertSame(before, RemoteConfig.accessStatus());
        assertTrue(response.closed);
        assertTrue(response.disconnected);
    }

    @Test public void announcementsRejectRedirectAndNeverSendDeviceCredentials() throws Exception {
        Response response = new Response(302, 0);
        List<String> errors = new ArrayList<>();
        AnnouncementChecker.request(callback(errors), url -> response);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, errors.size());
        assertFalse(response.getInstanceFollowRedirects());
        assertNull(response.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertTrue(response.disconnected);
    }

    @Test public void announcementsReportOversizeWithoutParsingOrPublishing() throws Exception {
        Response response = new Response(200, MetadataResponse.MAX_BYTES + 1);
        List<String> errors = new ArrayList<>();
        AnnouncementChecker.request(callback(errors), url -> response);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("大小限制"));
        assertTrue(response.closed);
        assertTrue(response.disconnected);
    }

    private static AnnouncementChecker.Callback callback(List<String> errors) {
        return new AnnouncementChecker.Callback() {
            @Override public void onResult(List<AnnouncementChecker.Item> items) {
                fail("Invalid response must not publish announcement items");
            }
            @Override public void onError(String message) { errors.add(message); }
        };
    }

    private static final class Response extends HttpURLConnection {
        final int status;
        final int size;
        boolean read;
        boolean closed;
        boolean disconnected;

        Response(int status, int size) throws Exception {
            super(new URL("https://heyboxlite.xyz/fixture"));
            this.status = status;
            this.size = size;
        }
        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String name) {
            return "Location".equalsIgnoreCase(name) ? "https://outside.invalid/redirect" : null;
        }
        @Override public InputStream getInputStream() {
            read = true;
            return new ByteArrayInputStream(new byte[size]) {
                @Override public void close() { closed = true; }
            };
        }
        @Override public void connect() {}
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
    }
}
