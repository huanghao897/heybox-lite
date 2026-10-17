package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class,
        instrumentedPackages = "com.ronan.heyboxlite",
        shadows = {CheckinCenterCoordinatorAuthorizationTest.Requests.class,
                CheckinCenterCoordinatorAuthorizationTest.Crypto.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class CheckinCenterCoordinatorAuthorizationTest {
    private static final String TOKEN_A = "ccdevice1_" + "A".repeat(43);
    private static final String TOKEN_B = "ccdevice1_" + "B".repeat(43);
    private final List<Boolean> authorizationChanges = new ArrayList<>();
    private CheckinCenterStore store;
    private CheckinCenterCoordinator coordinator;
    private Context context;

    @Before public void setUp() {
        Requests.statuses.clear();
        Requests.runs.clear();
        Requests.revocations.clear();
        Requests.qrs.clear();
        Crypto.failures = 0;
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("heybox_checkin_center", Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new CheckinCenterStore(context);
        coordinator = new CheckinCenterCoordinator(context, new LocalCache(context));
        assertTrue(coordinator.authorize(TOKEN_A));
        coordinator.setAuthorizationListener(authorizationChanges::add);
    }

    @After public void close() {
        coordinator.close();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void queuedOldStatusRetryIsNotSentAfterPairingReplacement() {
        Result<CheckinCenterClient.Status> result = new Result<>();
        coordinator.getStatus(result);
        Requests.statuses.get(0).callback.onError(error(CheckinCenterClient.Operation.STATUS, 503));
        replaceAuthorization();

        advance(60);

        assertEquals(1, Requests.statuses.size());
        assertPreservedReplacement(result);
    }

    @Test public void inFlightOldRetryUnauthorizedCannotClearTheReplacement() {
        Result<CheckinCenterClient.Status> result = new Result<>();
        coordinator.getStatus(result);
        Requests.statuses.get(0).callback.onError(error(CheckinCenterClient.Operation.STATUS, 503));
        advance(2);
        assertEquals(2, Requests.statuses.size());
        assertEquals(TOKEN_A, Requests.statuses.get(1).token);
        replaceAuthorization();

        Requests.statuses.get(1).callback.onError(error(CheckinCenterClient.Operation.STATUS, 401));

        assertPreservedReplacement(result);
    }

    @Test public void oldStatusSuccessAndFailureAreNotDeliveredToTheNewSession() {
        Result<CheckinCenterClient.Status> result = new Result<>();
        coordinator.getStatus(result);
        coordinator.getStatus(result);
        replaceAuthorization();

        Requests.statuses.get(0).callback.onSuccess(null);
        Requests.statuses.get(1).callback.onError(error(CheckinCenterClient.Operation.STATUS, 401));

        assertPreservedReplacement(result);
    }

    @Test public void lateOldRevokeSuccessCannotClearTheReplacement() {
        Result<Boolean> result = new Result<>();
        coordinator.revokeDevice(result);
        replaceAuthorization();

        Requests.revocations.get(0).callback.onSuccess(true);

        assertPreservedReplacement(result);
    }

    @Test public void lateOldRevokeUnauthorizedCannotClearTheReplacement() {
        Result<Boolean> result = new Result<>();
        coordinator.revokeDevice(result);
        replaceAuthorization();

        Requests.revocations.get(0).callback.onError(error(CheckinCenterClient.Operation.REVOKE, 401));

        assertPreservedReplacement(result);
    }

    @Test public void queuedOldRevokeRetryIsNotSentAfterReplacement() {
        Result<Boolean> result = new Result<>();
        coordinator.revokeDevice(result);
        Requests.revocations.get(0).callback.onError(error(CheckinCenterClient.Operation.REVOKE, 503));
        replaceAuthorization();

        advance(60);

        assertEquals(1, Requests.revocations.size());
        assertPreservedReplacement(result);
    }

    @Test public void queuedOldRunRetryIsNotSentAfterReplacement() {
        Result<CheckinCenterClient.RunResult> result = new Result<>();
        coordinator.runNow(result);
        Requests.runs.get(0).callback.onError(error(CheckinCenterClient.Operation.RUN_NOW, 503));
        replaceAuthorization();

        advance(60);

        assertEquals(1, Requests.runs.size());
        assertPreservedReplacement(result);
    }

    @Test public void oldPaymentCallbacksCannotClearOrUpdateTheNewSession() {
        Result<byte[]> result = new Result<>();
        coordinator.loadBillingQr("HB" + "A".repeat(30), result);
        coordinator.loadBillingQr("HB" + "B".repeat(30), result);
        replaceAuthorization();

        Requests.qrs.get(0).callback.onSuccess(new byte[]{1});
        Requests.qrs.get(1).callback.onError(error(CheckinCenterClient.Operation.BILLING_QR, 401));

        assertPreservedReplacement(result);
    }

    @Test public void currentStatusUnauthorizedStillClearsAuthorizationAndReachesCaller() {
        replaceAuthorization();
        Result<CheckinCenterClient.Status> result = new Result<>();
        coordinator.getStatus(result);
        assertEquals(TOKEN_B, Requests.statuses.get(0).token);

        Requests.statuses.get(0).callback.onError(error(CheckinCenterClient.Operation.STATUS, 401));

        assertFalse(coordinator.paired());
        assertEquals("", store.deviceToken());
        assertEquals(1, result.errors);
        assertEquals(0, result.successes);
        assertEquals(Boolean.FALSE, authorizationChanges.get(1));
    }

    @Test public void currentRevokeUnauthorizedStillCompletesRevocation() {
        replaceAuthorization();
        Result<Boolean> result = new Result<>();
        coordinator.revokeDevice(result);

        Requests.revocations.get(0).callback.onError(error(CheckinCenterClient.Operation.REVOKE, 401));

        assertFalse(coordinator.paired());
        assertEquals(1, result.successes);
        assertEquals(0, result.errors);
        assertEquals(Boolean.FALSE, authorizationChanges.get(1));
    }

    @Test public void currentPaymentUnauthorizedStillClearsAuthorization() {
        replaceAuthorization();
        Result<byte[]> result = new Result<>();
        coordinator.loadBillingQr("HB" + "A".repeat(30), result);

        Requests.qrs.get(0).callback.onError(error(CheckinCenterClient.Operation.BILLING_QR, 401));

        assertFalse(coordinator.paired());
        assertEquals(1, result.errors);
        assertEquals(Boolean.FALSE, authorizationChanges.get(1));
    }

    @Test public void conditionalClearObservesReplacementSavedByAnotherStore() throws Exception {
        CheckinCenterStore other = new CheckinCenterStore(context);
        other.saveDeviceToken(TOKEN_B);

        assertFalse(store.clearAuthorization(TOKEN_A));
        assertEquals(TOKEN_B, store.deviceToken());
        assertTrue(store.clearAuthorization(TOKEN_B));
        assertEquals("", other.deviceToken());
    }

    @Test public void temporaryKeyFailureKeepsEncryptedAuthorizationForAnotherStore() {
        String encrypted = context.getSharedPreferences("heybox_checkin_center", Context.MODE_PRIVATE)
                .getString("device_token_encrypted", "");
        Crypto.failures = 1;

        assertEquals("", new CheckinCenterStore(context).deviceToken());

        assertEquals(encrypted, context.getSharedPreferences(
                "heybox_checkin_center", Context.MODE_PRIVATE)
                .getString("device_token_encrypted", ""));
        assertEquals(TOKEN_A, store.deviceToken());
        assertTrue(coordinator.paired());
    }

    @Test public void temporaryKeyFailureDuringConditionalClearKeepsTheReplacement() throws Exception {
        CheckinCenterStore other = new CheckinCenterStore(context);
        other.saveDeviceToken(TOKEN_B);
        Crypto.failures = 1;

        assertFalse(store.clearAuthorization(TOKEN_A));

        assertEquals(TOKEN_B, other.deviceToken());
        assertTrue(coordinator.paired());
        assertTrue(authorizationChanges.isEmpty());
    }

    @Test public void currentResponseCompletesLoadingWithoutReopeningTheKeystore() {
        Result<CheckinCenterClient.Status> result = new Result<>();
        coordinator.getStatus(result);
        Crypto.failures = 1;

        Requests.statuses.get(0).callback.onSuccess(null);

        assertEquals(1, result.successes);
        assertFalse(result.loading);
        assertEquals(1, Crypto.failures);
        assertTrue(coordinator.paired());
        assertEquals("", new CheckinCenterStore(context).deviceToken());
        assertEquals(TOKEN_A, new CheckinCenterStore(context).deviceToken());
    }

    @Test public void currentUnauthorizedResponseReleasesLoadingDuringTemporaryKeyFailure() {
        Result<byte[]> result = new Result<>();
        coordinator.loadBillingQr("HB" + "A".repeat(30), result);
        Crypto.failures = 1;

        Requests.qrs.get(0).callback.onError(error(CheckinCenterClient.Operation.BILLING_QR, 401));

        assertEquals(1, result.errors);
        assertFalse(result.loading);
        assertFalse(coordinator.paired());
        assertEquals(Collections.singletonList(false), authorizationChanges);
        assertEquals(1, Crypto.failures);
    }

    @Test public void changedCiphertextInvalidatesCachedTokenBeforeTemporaryKeyFailure()
            throws Exception {
        assertEquals(TOKEN_A, store.deviceToken());
        new CheckinCenterStore(context).saveDeviceToken(TOKEN_B);
        Crypto.failures = 1;

        assertEquals("", store.deviceToken());

        assertEquals(TOKEN_B, store.deviceToken());
        assertFalse(store.clearAuthorization(TOKEN_A));
        assertEquals(TOKEN_B, store.deviceToken());
    }

    private void replaceAuthorization() {
        assertTrue(coordinator.authorize(TOKEN_B));
    }

    private void assertPreservedReplacement(Result<?> result) {
        assertEquals(TOKEN_B, store.deviceToken());
        assertTrue(coordinator.paired());
        assertEquals(Collections.singletonList(true), authorizationChanges);
        assertEquals(0, result.successes);
        assertEquals(0, result.errors);
    }

    private static void advance(long seconds) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds));
    }

    private static CheckinCenterClient.ApiError error(CheckinCenterClient.Operation operation,
                                                       int status) {
        return new CheckinCenterClient.ApiError(operation, status, "fixture failure");
    }

    private static final class Result<T> implements CheckinCenterClient.Callback<T> {
        int successes;
        int errors;
        boolean loading = true;
        @Override public void onSuccess(T value) { successes++; loading = false; }
        @Override public void onError(CheckinCenterClient.ApiError error) { errors++; loading = false; }
    }

    private static final class Pending<T> {
        final String token;
        final CheckinCenterClient.Callback<T> callback;
        Pending(String token, CheckinCenterClient.Callback<T> callback) {
            this.token = token;
            this.callback = callback;
        }
    }

    @Implements(value = CheckinCenterClient.class, isInAndroidSdk = false)
    public static final class Requests {
        static final List<Pending<CheckinCenterClient.Status>> statuses = new ArrayList<>();
        static final List<Pending<CheckinCenterClient.RunResult>> runs = new ArrayList<>();
        static final List<Pending<Boolean>> revocations = new ArrayList<>();
        static final List<Pending<byte[]>> qrs = new ArrayList<>();

        @Implementation protected void getStatus(String token,
                CheckinCenterClient.Callback<CheckinCenterClient.Status> callback) {
            statuses.add(new Pending<>(token, callback));
        }

        @Implementation protected void runNow(String token,
                CheckinCenterClient.Callback<CheckinCenterClient.RunResult> callback) {
            runs.add(new Pending<>(token, callback));
        }

        @Implementation protected void revokeDevice(String token,
                CheckinCenterClient.Callback<Boolean> callback) {
            revocations.add(new Pending<>(token, callback));
        }

        @Implementation protected void loadBillingQr(String token, String orderId,
                CheckinCenterClient.Callback<byte[]> callback) {
            qrs.add(new Pending<>(token, callback));
        }
    }

    @Implements(value = ModernCookieCrypto.class, isInAndroidSdk = false)
    public static final class Crypto {
        static int failures;
        @Implementation protected static String encrypt(String value) { return "fixture:" + value; }
        @Implementation protected static String decrypt(String value)
                throws ModernCookieCrypto.KeyUnavailableException {
            if (failures > 0) {
                failures--;
                throw new ModernCookieCrypto.KeyUnavailableException("fixture key unavailable");
            }
            return value.substring(8);
        }
    }
}
