package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

final class CheckinCenterStore {
    private static final String PREFERENCES = "heybox_checkin_center";
    private static final String DEVICE_TOKEN = "device_token_encrypted";
    private static final String LEGACY_CREDENTIAL_FINGERPRINT = "credential_fingerprint";
    private static final String LEGACY_SERVER_MANAGED_CREDENTIALS =
            "server_managed_credentials";
    private static final String TOKEN_PREFIX = "CCSEC1:";
    private static final Object AUTHORIZATION_LOCK = new Object();

    private final SharedPreferences preferences;
    private String cachedEncrypted;
    private String cachedToken;

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
        synchronized (AUTHORIZATION_LOCK) {
            return readDeviceToken();
        }
    }

    private String readDeviceToken() {
        String stored = preferences.getString(DEVICE_TOKEN, "");
        if (stored == null || !stored.startsWith(TOKEN_PREFIX) || !supported()) {
            cachedEncrypted = null;
            cachedToken = null;
            return "";
        }
        if (stored.equals(cachedEncrypted)) return cachedToken;
        cachedEncrypted = null;
        cachedToken = null;
        try {
            String token = ModernCookieCrypto.decrypt(stored.substring(TOKEN_PREFIX.length()));
            if (!validToken(token)) return "";
            cachedEncrypted = stored;
            cachedToken = token;
            return token;
        } catch (ModernCookieCrypto.KeyUnavailableException error) {
            return "";
        } catch (Exception error) {
            clearAuthorization();
            return "";
        }
    }

    void saveDeviceToken(String token) throws Exception {
        synchronized (AUTHORIZATION_LOCK) {
            if (!supported() || !validToken(token)) {
                throw new IllegalStateException("Device authorization is unavailable");
            }
            String encrypted = TOKEN_PREFIX + ModernCookieCrypto.encrypt(token);
            if (!preferences.edit().putString(DEVICE_TOKEN, encrypted)
                    .remove(LEGACY_CREDENTIAL_FINGERPRINT)
                    .remove(LEGACY_SERVER_MANAGED_CREDENTIALS)
                    .commit()) {
                throw new IllegalStateException("Device authorization could not be persisted");
            }
            cachedEncrypted = encrypted;
            cachedToken = token;
        }
    }

    void clearAuthorization() {
        synchronized (AUTHORIZATION_LOCK) {
            preferences.edit()
                    .remove(DEVICE_TOKEN)
                    .remove(LEGACY_CREDENTIAL_FINGERPRINT)
                    .remove(LEGACY_SERVER_MANAGED_CREDENTIALS)
                    .apply();
            cachedEncrypted = null;
            cachedToken = null;
        }
    }

    boolean clearAuthorization(String expectedToken) {
        synchronized (AUTHORIZATION_LOCK) {
            if (!expectedToken.equals(readDeviceToken())) return false;
            clearAuthorization();
            return true;
        }
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
