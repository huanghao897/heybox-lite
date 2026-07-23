package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

final class CheckinCenterStore {
    private static final String PREFERENCES = "heybox_checkin_center";
    private static final String DEVICE_TOKEN = "device_token_encrypted";
    private static final String CREDENTIAL_FINGERPRINT = "credential_fingerprint";
    private static final String TOKEN_PREFIX = "CCSEC1:";

    private final SharedPreferences preferences;

    CheckinCenterStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    boolean hasDeviceToken() {
        return !deviceToken().isEmpty();
    }

    String deviceToken() {
        String stored = preferences.getString(DEVICE_TOKEN, "");
        if (stored == null || !stored.startsWith(TOKEN_PREFIX) || !supported()) return "";
        try {
            String token = ModernCookieCrypto.decrypt(stored.substring(TOKEN_PREFIX.length()));
            return validToken(token) ? token : "";
        } catch (Exception error) {
            clearAuthorization();
            return "";
        }
    }

    void saveDeviceToken(String token) throws Exception {
        if (!supported() || !validToken(token)) {
            throw new IllegalStateException("Device authorization is unavailable");
        }
        String encrypted = TOKEN_PREFIX + ModernCookieCrypto.encrypt(token);
        if (!preferences.edit().putString(DEVICE_TOKEN, encrypted)
                .remove(CREDENTIAL_FINGERPRINT).commit()) {
            throw new IllegalStateException("Device authorization could not be persisted");
        }
    }

    String credentialFingerprint() {
        String value = preferences.getString(CREDENTIAL_FINGERPRINT, "");
        return value == null ? "" : value;
    }

    void saveCredentialFingerprint(String fingerprint) {
        String value = fingerprint == null ? "" : fingerprint.trim();
        if (!value.matches("[0-9a-f]{64}")) return;
        preferences.edit().putString(CREDENTIAL_FINGERPRINT, value).apply();
    }

    void clearAuthorization() {
        preferences.edit().remove(DEVICE_TOKEN).remove(CREDENTIAL_FINGERPRINT).apply();
    }

    private static boolean validToken(String value) {
        if (value == null || !value.startsWith("ccdevice1_")) return false;
        int payloadLength = value.length() - "ccdevice1_".length();
        if (payloadLength < 40 || payloadLength > 64) return false;
        for (int i = "ccdevice1_".length(); i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-') return false;
        }
        return true;
    }
}
