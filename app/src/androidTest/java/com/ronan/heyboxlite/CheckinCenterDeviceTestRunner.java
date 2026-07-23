package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class CheckinCenterDeviceTestRunner extends Instrumentation {
    private static final String SYNTHETIC_TOKEN =
            "ccdevice1_abcdefghijklmnopqrstuvwxyzABCDEFGH123456789";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        new Thread(() -> {
            Bundle result = new Bundle();
            try {
                testDeviceTokenIsEncryptedAtRest();
                testPinnedServerRejectsSyntheticAuthorization();
                testLegacyDownloadUrlUpgradesToHttps();
                result.putString("stream", "CheckinCenter device tests passed\n");
                finish(Activity.RESULT_OK, result);
            } catch (Throwable error) {
                result.putString("stream", "CheckinCenter device tests failed: "
                        + error.getClass().getSimpleName() + "\n");
                finish(Activity.RESULT_CANCELED, result);
            }
        }, "checkin-device-tests").start();
    }

    private void testDeviceTokenIsEncryptedAtRest() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Context context = getTargetContext();
        CheckinCenterStore store = new CheckinCenterStore(context);
        store.clearAuthorization();

        store.saveDeviceToken(SYNTHETIC_TOKEN);

        SharedPreferences raw = context.getSharedPreferences(
                "heybox_checkin_center", Context.MODE_PRIVATE);
        String persisted = raw.getString("device_token_encrypted", "");
        require(persisted != null && persisted.startsWith("CCSEC1:"));
        require(!persisted.contains(SYNTHETIC_TOKEN));
        require(SYNTHETIC_TOKEN.equals(new CheckinCenterStore(context).deviceToken()));
        store.clearAuthorization();
    }

    private void testPinnedServerRejectsSyntheticAuthorization() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        CountDownLatch completed = new CountDownLatch(1);
        int[] status = {-1};
        CheckinCenterClient client = new CheckinCenterClient();
        client.getStatus(SYNTHETIC_TOKEN,
                new CheckinCenterClient.Callback<CheckinCenterClient.Status>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.Status value) {
                        status[0] = 200;
                        completed.countDown();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        status[0] = error.statusCode;
                        completed.countDown();
                    }
                });

        boolean completedInTime = completed.await(30, TimeUnit.SECONDS);
        client.close();
        require(completedInTime && status[0] == 401);
    }

    private void testLegacyDownloadUrlUpgradesToHttps() {
        String upgraded = UpdateChecker.requireTrustedUrl(
                "http://8.138.134.236/download/latest.apk");
        require("https://8.138.134.236/download/latest.apk".equals(upgraded));
        require(UpdateChecker.trustedUrlOrEmpty(
                "http://103.236.54.97/download/latest.apk").isEmpty());
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
