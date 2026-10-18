package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.List;
import java.util.Map;

/** Encrypted cookie persistence and ownership of asynchronous session responses. */
final class SessionCookies {
    static final String GENERATION_KEY = "session_generation";
    private final SharedPreferences prefs;
    private final LegacyCookieCrypto legacyCrypto;
    private String cachedEncrypted;
    private String cachedCookie;

    SessionCookies(Context context, SharedPreferences prefs) {
        this.prefs = prefs;
        legacyCrypto = new LegacyCookieCrypto(context);
    }

    long generation() {
        return prefs.getLong(GENERATION_KEY, 0L);
    }

    boolean isCurrent(long generation) {
        return generation == generation();
    }

    String get() {
        synchronized (prefs) {
            String cookie = read();
            return cookie == null ? "" : cookie;
        }
    }

    long merge(List<String> headers, long generation) {
        // Context shares this preferences object across SessionStore instances.
        synchronized (prefs) {
            if (!isCurrent(generation)) return -1L;
            if (headers == null || headers.isEmpty()) return generation;
            String previous = read();
            if (previous == null) return generation;
            Map<String, String> values = SessionCookieCodec.parse(previous);
            for (String header : headers) {
                if (header == null) continue;
                String first = header.split(";", 2)[0];
                int equals = first.indexOf('=');
                if (equals <= 0) continue;
                String key = first.substring(0, equals).trim();
                String value = first.substring(equals + 1).trim();
                if (value.isEmpty()) values.remove(key);
                else values.put(key, value);
            }
            SessionCookieCodec.normalizeAuth(values);
            String cookie = SessionCookieCodec.join(values);
            boolean accountChanged = !SessionCookieCodec.userId(previous)
                    .equals(SessionCookieCodec.userId(cookie));
            write(cookie, accountChanged);
            return generation();
        }
    }

    void putValue(String key, String value) {
        if (key == null || key.isEmpty()) return;
        synchronized (prefs) {
            String previous = read();
            if (previous == null) return;
            Map<String, String> values = SessionCookieCodec.parse(previous);
            if (value == null || value.isEmpty()) values.remove(key);
            else values.put(key, value);
            SessionCookieCodec.normalizeAuth(values);
            write(SessionCookieCodec.join(values), false);
        }
    }

    void save(String value) {
        synchronized (prefs) {
            write(value, false);
        }
    }

    void clearCached() {
        synchronized (prefs) {
            cachedEncrypted = null;
            cachedCookie = null;
        }
    }

    private String read() {
        String encrypted = prefs.getString(SecureStrings.encryptedCookieKey(), "");
        if (encrypted.isEmpty()) {
            String legacy = prefs.getString(SecureStrings.cookieKey(), "");
            if (!legacy.isEmpty() && !write(legacy, false)) return null;
            encrypted = prefs.getString(SecureStrings.encryptedCookieKey(), "");
        }
        if (encrypted.isEmpty()) return "";
        if (encrypted.equals(cachedEncrypted) && cachedCookie != null) return cachedCookie;
        String cookie = decrypt(encrypted);
        if (cookie == null) return null;
        if (!cookie.isEmpty() && encrypted.startsWith(LegacyCookieCrypto.PREFIX)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            write(cookie, false);
        }
        String normalized = SessionCookieCodec.normalize(cookie);
        if (!normalized.equals(cookie)) write(normalized, false);
        cachedEncrypted = prefs.getString(SecureStrings.encryptedCookieKey(), encrypted);
        cachedCookie = normalized;
        return normalized;
    }

    private boolean write(String value, boolean advanceGeneration) {
        try {
            String cookie = SessionCookieCodec.normalize(value == null ? "" : value);
            String encrypted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? ModernCookieCrypto.encrypt(cookie) : legacyCrypto.encrypt(cookie);
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(SecureStrings.encryptedCookieKey(), encrypted)
                    .remove(SecureStrings.cookieKey());
            if (advanceGeneration) editor.putLong(GENERATION_KEY, generation() + 1L);
            editor.apply();
            cachedEncrypted = encrypted;
            cachedCookie = cookie;
            persistUserId(cookie);
            return true;
        } catch (Exception error) {
            Log.w("SessionStore", "Unable to encrypt session cookie", error);
            return false;
        }
    }

    private void persistUserId(String cookie) {
        if (!prefs.getString(SecureStrings.userId(), "").isEmpty()) return;
        String id = SessionCookieCodec.userId(cookie);
        if (!id.isEmpty()) prefs.edit().putString(SecureStrings.userId(), id).apply();
    }

    private String decrypt(String value) {
        try {
            if (value.startsWith(LegacyCookieCrypto.PREFIX)) return legacyCrypto.decrypt(value);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return ModernCookieCrypto.decrypt(value);
            return "";
        } catch (ModernCookieCrypto.InvalidPayloadException error) {
            prefs.edit().remove(SecureStrings.encryptedCookieKey()).apply();
            clearCached();
            Log.w("SessionStore", "Discarded malformed encrypted session", error);
            return "";
        } catch (ModernCookieCrypto.KeyUnavailableException error) {
            Log.w("SessionStore", "Cookie key is temporarily unavailable", error);
            return null;
        } catch (Exception error) {
            if (value.startsWith(LegacyCookieCrypto.PREFIX)) {
                prefs.edit().remove(SecureStrings.encryptedCookieKey()).apply();
            }
            Log.w("SessionStore", "Unable to decrypt session cookie", error);
            return value.startsWith(LegacyCookieCrypto.PREFIX) ? "" : null;
        }
    }
}
