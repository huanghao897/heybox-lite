package com.openzen.heyboxcommunity;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SessionStore {
    private static final String USER_NAME = "user_name";
    private static final String AVATAR = "avatar";
    private static final String NO_IMAGE = "no_image";
    private static final String UI_SCALE = "ui_scale";
    private static final String TEXT_SCALE = "text_scale";
    private static final String PAGE_PADDING = "page_padding";
    private static final String DARK_MODE = "dark_mode";
    private static final String ORIGINAL_IMAGES = "original_images";
    private static final String ACCENT_COLOR = "accent_color";

    private final SharedPreferences prefs;

    SessionStore(Context context) {
        prefs = context.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE);
        migratePlainCookieIfNeeded();
        if (prefs.getString(SecureStrings.deviceId(), "").isEmpty()) {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String id = androidId == null || androidId.isEmpty()
                    ? UUID.randomUUID().toString().replace("-", "") : androidId;
            prefs.edit().putString(SecureStrings.deviceId(), id).apply();
        }
    }

    boolean isLoggedIn() {
        return !getCookie().isEmpty() && !userId().isEmpty();
    }

    String getCookie() {
        String encrypted = prefs.getString(SecureStrings.encryptedCookieKey(), "");
        if (!encrypted.isEmpty()) return decrypt(encrypted);
        return "";
    }

    void migratePlainCookieIfNeeded() {
        String legacy = prefs.getString(SecureStrings.cookieKey(), "");
        if (legacy.isEmpty()) return;
        saveCookie(legacy);
        prefs.edit().remove(SecureStrings.cookieKey()).apply();
    }

    String userId() {
        return prefs.getString(SecureStrings.userId(), "");
    }

    String userName() {
        return prefs.getString(USER_NAME, "");
    }

    String avatar() {
        return prefs.getString(AVATAR, "");
    }

    boolean noImage() {
        return prefs.getBoolean(NO_IMAGE, false);
    }

    void setNoImage(boolean value) {
        prefs.edit().putBoolean(NO_IMAGE, value).apply();
    }

    int uiScale() {
        return prefs.getInt(UI_SCALE, 100);
    }

    void setUiScale(int value) {
        prefs.edit().putInt(UI_SCALE, value).apply();
    }

    int textScale() {
        return prefs.getInt(TEXT_SCALE, 100);
    }

    void setTextScale(int value) {
        prefs.edit().putInt(TEXT_SCALE, value).apply();
    }

    int pagePadding() {
        return prefs.getInt(PAGE_PADDING, 8);
    }

    void setPagePadding(int value) {
        prefs.edit().putInt(PAGE_PADDING, value).apply();
    }

    boolean darkMode() {
        return prefs.getBoolean(DARK_MODE, true);
    }

    void setDarkMode(boolean value) {
        prefs.edit().putBoolean(DARK_MODE, value).apply();
    }

    boolean originalImages() {
        return prefs.getBoolean(ORIGINAL_IMAGES, false);
    }

    void setOriginalImages(boolean value) {
        prefs.edit().putBoolean(ORIGINAL_IMAGES, value).apply();
    }

    String accentColor() {
        return prefs.getString(ACCENT_COLOR, "");
    }

    void setAccentColor(String value) {
        prefs.edit().putString(ACCENT_COLOR, value).apply();
    }

    Map<String, String> commonParams() {
        Map<String, String> result = new HashMap<>();
        result.put("os_type", "web");
        result.put("app", "heybox");
        result.put("client_type", "web");
        result.put("version", "999.0.4");
        result.put("web_version", "2.5");
        result.put("x_client_type", "web");
        result.put("x_app", "heybox_website");
        result.put(SecureStrings.heyboxId(), userId());
        result.put("x_os_type", "Android");
        result.put("device_info", "Chrome");
        result.put(SecureStrings.deviceId(), prefs.getString(SecureStrings.deviceId(), ""));
        return result;
    }

    void saveLogin(JSONObject result) {
        String id = result.optString("heyboxid",
                result.optString(SecureStrings.userid(), result.optString(SecureStrings.heyboxId())));
        JSONObject account = result.optJSONObject("account_detail");
        String name = result.optString("nickname", result.optString("username"));
        String avatar = result.optString("avatar");
        if (account != null) {
            if (id.isEmpty()) id = account.optString(
                    SecureStrings.userid(), account.optString("heyboxid"));
            if (name.isEmpty()) name = account.optString("username", account.optString("nickname"));
            if (avatar.isEmpty()) avatar = account.optString("avatar");
        }
        prefs.edit()
                .putString(SecureStrings.userId(), id)
                .putString(USER_NAME, name)
                .putString(AVATAR, avatar)
                .apply();
    }

    void mergeCookies(List<String> headers) {
        if (headers == null || headers.isEmpty()) return;
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : getCookie().split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0) values.put(part.substring(0, equals).trim(), part.substring(equals + 1).trim());
        }
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
        StringBuilder merged = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (merged.length() > 0) merged.append("; ");
            merged.append(entry.getKey()).append('=').append(entry.getValue());
        }
        saveCookie(merged.toString());
    }

    void saveCookie(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            byte[] packed = new byte[1 + iv.length + encrypted.length];
            packed[0] = (byte) iv.length;
            System.arraycopy(iv, 0, packed, 1, iv.length);
            System.arraycopy(encrypted, 0, packed, 1 + iv.length, encrypted.length);
            prefs.edit().putString(SecureStrings.encryptedCookieKey(),
                    Base64.encodeToString(packed, Base64.NO_WRAP))
                    .remove(SecureStrings.cookieKey()).apply();
        } catch (Exception ignored) {
            prefs.edit().remove(SecureStrings.cookieKey())
                    .remove(SecureStrings.encryptedCookieKey()).apply();
        }
    }

    private String decrypt(String value) {
        try {
            byte[] packed = Base64.decode(value, Base64.NO_WRAP);
            int ivLength = packed[0] & 0xff;
            byte[] iv = new byte[ivLength];
            byte[] encrypted = new byte[packed.length - 1 - ivLength];
            System.arraycopy(packed, 1, iv, 0, ivLength);
            System.arraycopy(packed, 1 + ivLength, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            prefs.edit().remove(SecureStrings.encryptedCookieKey()).apply();
            return "";
        }
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        String alias = SecureStrings.keyAlias();
        java.security.Key existing = store.getKey(alias, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    void clearSession() {
        String deviceId = prefs.getString(SecureStrings.deviceId(), "");
        boolean noImage = noImage();
        int uiScale = uiScale();
        int textScale = textScale();
        int pagePadding = pagePadding();
        boolean darkMode = darkMode();
        boolean originalImages = originalImages();
        String accentColor = accentColor();
        prefs.edit().clear()
                .putString(SecureStrings.deviceId(), deviceId)
                .putBoolean(NO_IMAGE, noImage)
                .putInt(UI_SCALE, uiScale)
                .putInt(TEXT_SCALE, textScale)
                .putInt(PAGE_PADDING, pagePadding)
                .putBoolean(DARK_MODE, darkMode)
                .putBoolean(ORIGINAL_IMAGES, originalImages)
                .putString(ACCENT_COLOR, accentColor)
                .apply();
    }
}
