package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.net.HttpURLConnection;

final class DeviceAuthorizationStore {
    static final String DEVICE_ID_HEADER = "X-HeyBox-Device-Id";
    static final String DEVICE_TOKEN_HEADER = "X-HeyBox-Device-Token";

    private static final String PREFERENCES = "heybox_server_device";
    private static final String TOKEN_KEY = "device_token_encrypted";
    private static final String TOKEN_PREFIX = "HBLITE1:";
    private static final String VALUE_PREFIX = "hblite_device_";
    private static volatile String processToken = "";

    private final SharedPreferences preferences;

    DeviceAuthorizationStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    String token() {
        if (!processToken.isEmpty()) return processToken;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "";
        String stored = preferences.getString(TOKEN_KEY, "");
        if (stored == null || !stored.startsWith(TOKEN_PREFIX)) return "";
        try {
            String value = ModernCookieCrypto.decrypt(stored.substring(TOKEN_PREFIX.length()));
            if (!validToken(value)) {
                clear();
                return "";
            }
            processToken = value;
            return value;
        } catch (Exception error) {
            clear();
            return "";
        }
    }

    void save(String token) throws Exception {
        if (!validToken(token)) throw new IllegalArgumentException("Invalid device token");
        processToken = token;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        String encrypted = TOKEN_PREFIX + ModernCookieCrypto.encrypt(token);
        if (!preferences.edit().putString(TOKEN_KEY, encrypted).commit()) {
            throw new IllegalStateException("Device token could not be persisted");
        }
    }

    void clear() {
        processToken = "";
        preferences.edit().remove(TOKEN_KEY).apply();
    }

    void apply(HttpURLConnection connection, SessionStore session) {
        String deviceId = session == null ? "" : session.presenceDeviceIdentifier();
        if (!deviceId.isEmpty()) connection.setRequestProperty(DEVICE_ID_HEADER, deviceId);
        String value = token();
        if (!value.isEmpty()) connection.setRequestProperty(DEVICE_TOKEN_HEADER, value);
    }

    static boolean validToken(String value) {
        if (value == null || !value.startsWith(VALUE_PREFIX)) return false;
        int payloadLength = value.length() - VALUE_PREFIX.length();
        if (payloadLength < 40 || payloadLength > 64) return false;
        for (int index = VALUE_PREFIX.length(); index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-') return false;
        }
        return true;
    }
}
