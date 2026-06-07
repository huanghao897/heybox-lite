package com.openzen.heyboxcommunity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
    private static final String PRIMARY_COLOR = "primary_color";
    private static final String SECONDARY_COLOR = "secondary_color";
    private static final String BODY_TEXT_SCALE = "body_text_scale";
    private static final String BODY_LETTER_SPACING = "body_letter_spacing";
    private static final String BODY_PARAGRAPH_SPACING = "body_paragraph_spacing";
    private static final String BODY_LINE_SPACING = "body_line_spacing";
    private static final String BODY_BOLD = "body_bold";
    private static final String LEGACY_PREFIX = "L1:";

    private final Context context;
    private final SharedPreferences prefs;

    SessionStore(Context context) {
        this.context = context.getApplicationContext();
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
        if (!encrypted.isEmpty()) {
            String cookie = decrypt(encrypted);
            if (!cookie.isEmpty() && encrypted.startsWith(LEGACY_PREFIX)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                saveCookie(cookie);
            }
            return cookie;
        }
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

    String primaryColor() {
        String value = prefs.getString(PRIMARY_COLOR, "");
        return value.isEmpty() ? prefs.getString(ACCENT_COLOR, "") : value;
    }

    void setPrimaryColor(String value) {
        prefs.edit()
                .putString(PRIMARY_COLOR, value)
                .remove(ACCENT_COLOR)
                .apply();
    }

    String secondaryColor() {
        return prefs.getString(SECONDARY_COLOR, "");
    }

    void setSecondaryColor(String value) {
        prefs.edit().putString(SECONDARY_COLOR, value).apply();
    }

    int bodyTextScale() {
        return prefs.getInt(BODY_TEXT_SCALE, 100);
    }

    void setBodyTextScale(int value) {
        prefs.edit().putInt(BODY_TEXT_SCALE, value).apply();
    }

    int bodyLetterSpacing() {
        return prefs.getInt(BODY_LETTER_SPACING, 0);
    }

    void setBodyLetterSpacing(int value) {
        prefs.edit().putInt(BODY_LETTER_SPACING, value).apply();
    }

    int bodyParagraphSpacing() {
        return prefs.getInt(BODY_PARAGRAPH_SPACING, 9);
    }

    void setBodyParagraphSpacing(int value) {
        prefs.edit().putInt(BODY_PARAGRAPH_SPACING, value).apply();
    }

    int bodyLineSpacing() {
        return prefs.getInt(BODY_LINE_SPACING, 122);
    }

    void setBodyLineSpacing(int value) {
        prefs.edit().putInt(BODY_LINE_SPACING, value).apply();
    }

    boolean bodyBold() {
        return prefs.getBoolean(BODY_BOLD, true);
    }

    void setBodyBold(boolean value) {
        prefs.edit().putBoolean(BODY_BOLD, value).apply();
    }

    void setTheme(String primary, String secondary) {
        prefs.edit()
                .putString(PRIMARY_COLOR, primary)
                .putString(SECONDARY_COLOR, secondary)
                .remove(ACCENT_COLOR)
                .apply();
    }

    void resetDisplaySettings() {
        prefs.edit()
                .putBoolean(DARK_MODE, true)
                .putInt(UI_SCALE, 100)
                .putInt(TEXT_SCALE, 100)
                .putInt(PAGE_PADDING, 8)
                .putString(PRIMARY_COLOR, "#2479B8")
                .putString(SECONDARY_COLOR, "#73B8E6")
                .remove(ACCENT_COLOR)
                .putInt(BODY_TEXT_SCALE, 100)
                .putInt(BODY_LETTER_SPACING, 0)
                .putInt(BODY_PARAGRAPH_SPACING, 9)
                .putInt(BODY_LINE_SPACING, 122)
                .putBoolean(BODY_BOLD, true)
                .apply();
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
            String encrypted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? ModernCookieCrypto.encrypt(value) : encryptLegacy(value);
            prefs.edit().putString(SecureStrings.encryptedCookieKey(),
                    encrypted)
                    .remove(SecureStrings.cookieKey()).apply();
        } catch (Exception ignored) {
            prefs.edit().remove(SecureStrings.cookieKey())
                    .remove(SecureStrings.encryptedCookieKey()).apply();
        }
    }

    private String decrypt(String value) {
        try {
            if (value.startsWith(LEGACY_PREFIX)) return decryptLegacy(value);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return ModernCookieCrypto.decrypt(value);
            }
            return "";
        } catch (Exception ignored) {
            prefs.edit().remove(SecureStrings.encryptedCookieKey()).apply();
            return "";
        }
    }

    private String encryptLegacy(String value) throws Exception {
        byte[] keys = legacyKeyMaterial();
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(slice(keys, 0, 16), "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(value.getBytes("UTF-8"));
        byte[] body = join(iv, encrypted);
        byte[] mac = hmac(slice(keys, 16, 32), body);
        return LEGACY_PREFIX + Base64.encodeToString(join(body, mac), Base64.NO_WRAP);
    }

    private String decryptLegacy(String value) throws Exception {
        byte[] packed = Base64.decode(value.substring(LEGACY_PREFIX.length()), Base64.NO_WRAP);
        if (packed.length < 49) throw new IllegalArgumentException("Invalid session");
        byte[] body = slice(packed, 0, packed.length - 32);
        byte[] expected = slice(packed, packed.length - 32, packed.length);
        byte[] keys = legacyKeyMaterial();
        if (!MessageDigest.isEqual(expected, hmac(slice(keys, 16, 32), body))) {
            throw new SecurityException("Session integrity check failed");
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(slice(keys, 0, 16), "AES"),
                new IvParameterSpec(slice(body, 0, 16)));
        return new String(cipher.doFinal(slice(body, 16, body.length)), "UTF-8");
    }

    @SuppressWarnings("deprecation")
    private byte[] legacyKeyMaterial() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(context.getPackageName().getBytes("UTF-8"));
        digest.update((byte) 0x6d);
        digest.update((byte) 0x31);
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId != null) digest.update(androidId.getBytes("UTF-8"));
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNATURES);
        Signature[] signatures = info.signatures;
        if (signatures != null && signatures.length > 0) {
            digest.update(signatures[0].toByteArray());
        }
        return digest.digest();
    }

    private static byte[] hmac(byte[] key, byte[] value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
    }

    private static byte[] slice(byte[] source, int start, int end) {
        byte[] result = new byte[end - start];
        System.arraycopy(source, start, result, 0, result.length);
        return result;
    }

    private static byte[] join(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    void clearSession() {
        String deviceId = prefs.getString(SecureStrings.deviceId(), "");
        boolean noImage = noImage();
        int uiScale = uiScale();
        int textScale = textScale();
        int pagePadding = pagePadding();
        boolean darkMode = darkMode();
        boolean originalImages = originalImages();
        String primaryColor = primaryColor();
        String secondaryColor = secondaryColor();
        int bodyTextScale = bodyTextScale();
        int bodyLetterSpacing = bodyLetterSpacing();
        int bodyParagraphSpacing = bodyParagraphSpacing();
        int bodyLineSpacing = bodyLineSpacing();
        boolean bodyBold = bodyBold();
        prefs.edit().clear()
                .putString(SecureStrings.deviceId(), deviceId)
                .putBoolean(NO_IMAGE, noImage)
                .putInt(UI_SCALE, uiScale)
                .putInt(TEXT_SCALE, textScale)
                .putInt(PAGE_PADDING, pagePadding)
                .putBoolean(DARK_MODE, darkMode)
                .putBoolean(ORIGINAL_IMAGES, originalImages)
                .putString(PRIMARY_COLOR, primaryColor)
                .putString(SECONDARY_COLOR, secondaryColor)
                .putInt(BODY_TEXT_SCALE, bodyTextScale)
                .putInt(BODY_LETTER_SPACING, bodyLetterSpacing)
                .putInt(BODY_PARAGRAPH_SPACING, bodyParagraphSpacing)
                .putInt(BODY_LINE_SPACING, bodyLineSpacing)
                .putBoolean(BODY_BOLD, bodyBold)
                .apply();
    }
}
