package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.net.HttpURLConnection;

import javax.crypto.AEADBadTagException;

final class DeviceAuthorizationStore {
    static final String DEVICE_ID_HEADER = "X-HeyBox-Device-Id";
    static final String DEVICE_TOKEN_HEADER = "X-HeyBox-Device-Token";

    private static final String PREFERENCES = "heybox_server_device";
    private static final String TOKEN_KEY = "device_token_encrypted";
    private static final String TOKEN_PREFIX = "HBLITE1:";
    private static final String VALUE_PREFIX = "hblite_device_";
    private static final Object LOCK = new Object();
    private static String processToken = "";
    private static String processEncrypted = "";

    private final SharedPreferences preferences;

    DeviceAuthorizationStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    String token() {
        synchronized (LOCK) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return processToken;
            String stored = preferences.getString(TOKEN_KEY, "");
            if (stored == null || !stored.startsWith(TOKEN_PREFIX)) {
                processToken = "";
                processEncrypted = "";
                return "";
            }
            if (stored.equals(processEncrypted)) return processToken;
            try {
                String value = ModernCookieCrypto.decrypt(stored.substring(TOKEN_PREFIX.length()));
                if (!validToken(value)) {
                    clear();
                    return "";
                }
                processToken = value;
                processEncrypted = stored;
                return value;
            } catch (ModernCookieCrypto.InvalidPayloadException | AEADBadTagException error) {
                clear();
                return "";
            } catch (Exception error) {
                // A temporary Keystore failure must not destroy the only saved authorization.
                return "";
            }
        }
    }

    void save(String token) throws Exception {
        synchronized (LOCK) {
            if (!validToken(token)) throw new IllegalArgumentException("Invalid device token");
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                processToken = token;
                return;
            }
            String encrypted = TOKEN_PREFIX + ModernCookieCrypto.encrypt(token);
            if (!preferences.edit().putString(TOKEN_KEY, encrypted).commit()) {
                throw new IllegalStateException("Device token could not be persisted");
            }
            processToken = token;
            processEncrypted = encrypted;
        }
    }

    void clear() {
        synchronized (LOCK) {
            processToken = "";
            processEncrypted = "";
            preferences.edit().remove(TOKEN_KEY).apply();
        }
    }

    boolean clear(String expectedToken) {
        synchronized (LOCK) {
            if (expectedToken.isEmpty() || !expectedToken.equals(token())) return false;
            clear();
            return true;
        }
    }

    void apply(HttpURLConnection connection, SessionStore session) {
        apply(connection, session, token());
    }

    void apply(HttpURLConnection connection, SessionStore session, String value) {
        String deviceId = session == null ? "" : session.presenceDeviceIdentifier();
        if (!deviceId.isEmpty()) connection.setRequestProperty(DEVICE_ID_HEADER, deviceId);
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
