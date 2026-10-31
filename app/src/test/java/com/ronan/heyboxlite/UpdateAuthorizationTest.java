package com.ronan.heyboxlite;

import android.app.Application;
import android.os.Looper;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ronan.heyboxlite.DeviceAuthorizationTestFixtures.TOKEN;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class,
        shadows = {UpdateAuthorizationTest.Preparation.class,
                UpdateAuthorizationTest.Fingerprint.class,
                DeviceAuthorizationTestFixtures.Crypto.class},
        instrumentedPackages = "com.ronan.heyboxlite")
@LooperMode(LooperMode.Mode.PAUSED)
public class UpdateAuthorizationTest {
    private SessionStore session;
    private DeviceAuthorizationStore authorization;
    private final List<UpdateChecker.Result> results = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> requestedUrls = new ArrayList<>();

    @Before public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        app.getApplicationInfo().sourceDir = "/fixture/installed.apk";
        session = new SessionStore(app);
        DeviceAuthorizationTestFixtures.Crypto.reset();
        authorization = new DeviceAuthorizationStore(app);
        authorization.clear();
        Preparation.ready = false;
        Preparation.warning = "设备认证已失效，测试设备需在后台重新授权";
        Preparation.deferred = false;
        Preparation.pending = null;
    }

    @Test public void missingTestAuthorizationStillRequestsPublicUpdateMetadata() throws Exception {
        DeviceAuthorizationTestFixtures.Response response = response("stable", true);
        check(response);
        await(response);

        assertTrue(errors.isEmpty());
        assertEquals(1, results.size());
        UpdateChecker.Result result = results.get(0);
        assertTrue(result.updateAvailable);
        assertEquals("stable", result.channel);
        assertEquals(Preparation.warning, result.authorizationWarning);
        assertTrue(result.checkMessage().contains("正式版"));
        assertTrue(result.checkMessage().contains("后台重新授权"));
        assertNull(response.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertFalse(response.getInstanceFollowRedirects());
        assertEquals(1, requestedUrls.size());
        assertTrue(requestedUrls.get(0).contains("installedApkSha256=fixture-fingerprint"));
        assertFalse(requestedUrls.get(0).contains("userId="));
    }

    @Test public void failedTestAuthenticationNeverClaimsAllChannelsAreUpToDate() throws Exception {
        DeviceAuthorizationTestFixtures.Response response = response("stable", false);
        check(response);
        await(response);

        assertFalse(results.get(0).updateAvailable);
        assertFalse(results.get(0).checkMessage().contains("当前已是最新版"));
        assertEquals("已检查正式版更新；" + Preparation.warning, results.get(0).checkMessage());
    }

    @Test public void authenticatedTestDeviceStillUsesItsExistingAuthorization() throws Exception {
        authorization.save(TOKEN);
        Preparation.ready = true;
        DeviceAuthorizationTestFixtures.Response response = response("test", true);
        check(response);
        await(response);

        assertEquals(TOKEN, response.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertEquals("test", results.get(0).channel);
        assertEquals("", results.get(0).authorizationWarning);
        assertEquals("", results.get(0).checkMessage());
        assertTrue(results.get(0).updateAvailable);
    }

    @Test public void requestWaitsForRegistrationAndUsesTokenIssuedBeforeCompletion() throws Exception {
        Preparation.deferred = true;
        DeviceAuthorizationTestFixtures.Response response = response("test", true);
        check(response);
        assertTrue(requestedUrls.isEmpty());
        assertNotNull(Preparation.pending);

        authorization.save(TOKEN);
        Preparation.pending.onComplete(true, "");
        await(response);
        assertEquals(TOKEN, response.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertEquals("", results.get(0).authorizationWarning);
    }

    @Test public void validTokenCanSelectTestChannelEvenIfHeartbeatTimedOut() throws Exception {
        authorization.save(TOKEN);
        Preparation.warning = "设备认证连接超时，请稍后重试";
        DeviceAuthorizationTestFixtures.Response response = response("test", true);
        check(response);
        await(response);

        assertEquals(TOKEN, response.getRequestProperty(DeviceAuthorizationStore.DEVICE_TOKEN_HEADER));
        assertEquals("test", results.get(0).channel);
        assertEquals("", results.get(0).authorizationWarning);
        assertEquals("", results.get(0).checkMessage());
    }

    @Test public void authenticatedCurrentVersionStillReportsUpToDate() throws Exception {
        Preparation.ready = true;
        authorization.save(TOKEN);
        DeviceAuthorizationTestFixtures.Response response = response("test", false);
        check(response);
        await(response);

        assertFalse(results.get(0).updateAvailable);
        assertEquals("当前已是最新版", results.get(0).checkMessage());
    }

    @Test public void blockedUpdateIsNotBypassedOrReportedAsSuccessful() throws Exception {
        DeviceAuthorizationTestFixtures.Response response =
                new DeviceAuthorizationTestFixtures.Response(403, "");
        check(response);
        await(response);

        assertTrue(results.isEmpty());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("HTTP 403"));
        assertEquals(1, requestedUrls.size());
    }

    private void check(DeviceAuthorizationTestFixtures.Response response) {
        UpdateChecker.check(BuildConfig.VERSION_NAME, session, 0, new UpdateChecker.Callback() {
            @Override public void onResult(UpdateChecker.Result result) { results.add(result); }
            @Override public void onError(String message) { errors.add(message); }
        }, url -> {
            requestedUrls.add(url.toExternalForm());
            return response;
        });
    }

    private void await(DeviceAuthorizationTestFixtures.Response response) throws Exception {
        assertTrue("Update request did not finish", response.finished.await(5, TimeUnit.SECONDS));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static DeviceAuthorizationTestFixtures.Response response(String channel, boolean update)
            throws Exception {
        JSONObject body = new JSONObject().put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE).put("hasUpdate", update)
                .put("channel", channel).put("downloadUrl", "https://heyboxlite.xyz/uploads/app.apk");
        return new DeviceAuthorizationTestFixtures.Response(200, body.toString());
    }

    @Implements(value = PresenceReporter.class, isInAndroidSdk = false)
    public static final class Preparation {
        static boolean ready;
        static String warning;
        static boolean deferred;
        static PresenceReporter.Completion pending;

        @Implementation protected static void prepareUpdate(SessionStore session,
                PresenceReporter.Completion completion) {
            pending = completion;
            if (!deferred) completion.onComplete(ready, warning);
        }
    }

    @Implements(value = InstalledApkFingerprint.class, isInAndroidSdk = false)
    public static final class Fingerprint {
        @Implementation protected static String read(File apk) { return "fixture-fingerprint"; }
    }
}
