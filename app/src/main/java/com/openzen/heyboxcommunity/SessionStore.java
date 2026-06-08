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
import org.json.JSONArray;

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
    private static final String AUTO_UPDATE_CHECK = "auto_update_check";
    private static final String SPLASH_ENABLED = "splash_enabled";
    private static final String SPLASH_TEXT = "splash_text";
    private static final String SPLASH_DURATION = "splash_duration";
    private static final String SEARCH_HISTORY = "search_history";
    private static final String BLOCK_KEYWORDS = "block_keywords";
    static final String DEFAULT_SPLASH_TEXT = "方寸之间，看见热爱";
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
            String normalized = normalizeCookie(cookie);
            if (!normalized.equals(cookie)) saveCookie(normalized);
            return normalized;
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
        String saved = prefs.getString(SecureStrings.userId(), "");
        if (!saved.isEmpty()) return saved;
        String fromCookie = userIdFromCookie(getCookie());
        if (!fromCookie.isEmpty()) {
            prefs.edit().putString(SecureStrings.userId(), fromCookie).apply();
        }
        return fromCookie;
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

    boolean autoUpdateCheck() {
        return prefs.getBoolean(AUTO_UPDATE_CHECK, true);
    }

    void setAutoUpdateCheck(boolean value) {
        prefs.edit().putBoolean(AUTO_UPDATE_CHECK, value).apply();
    }

    boolean splashEnabled() {
        return prefs.getBoolean(SPLASH_ENABLED, true);
    }

    void setSplashEnabled(boolean value) {
        prefs.edit().putBoolean(SPLASH_ENABLED, value).apply();
    }

    String splashText() {
        String value = prefs.getString(SPLASH_TEXT, DEFAULT_SPLASH_TEXT);
        return value == null || value.trim().isEmpty() ? DEFAULT_SPLASH_TEXT : value.trim();
    }

    void setSplashText(String value) {
        String clean = value == null ? "" : value.trim();
        prefs.edit().putString(SPLASH_TEXT,
                clean.isEmpty() ? DEFAULT_SPLASH_TEXT : clean).apply();
    }

    int splashDuration() {
        return prefs.getInt(SPLASH_DURATION, 1100);
    }

    void setSplashDuration(int value) {
        prefs.edit().putInt(SPLASH_DURATION, value).apply();
    }

    List<String> searchHistory() {
        List<String> values = new java.util.ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(SEARCH_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i).trim();
                if (!value.isEmpty()) values.add(value);
            }
        } catch (Exception ignored) {
        }
        return values;
    }

    void addSearchHistory(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return;
        List<String> values = searchHistory();
        for (int i = values.size() - 1; i >= 0; i--) {
            if (clean.equalsIgnoreCase(values.get(i))) values.remove(i);
        }
        values.add(0, clean);
        while (values.size() > 8) values.remove(values.size() - 1);
        JSONArray array = new JSONArray();
        for (String item : values) array.put(item);
        prefs.edit().putString(SEARCH_HISTORY, array.toString()).apply();
    }

    void clearSearchHistory() {
        prefs.edit().remove(SEARCH_HISTORY).apply();
    }

    String blockKeywords() {
        return prefs.getString(BLOCK_KEYWORDS, "");
    }

    void setBlockKeywords(String value) {
        prefs.edit().putString(BLOCK_KEYWORDS, value == null ? "" : value.trim()).apply();
    }

    List<String> blockKeywordList() {
        List<String> values = new java.util.ArrayList<>();
        String raw = blockKeywords();
        if (raw.isEmpty()) return values;
        String[] parts = raw.split("[,，;；\\n\\r]+");
        for (String part : parts) {
            String clean = part.trim().toLowerCase(java.util.Locale.US);
            if (!clean.isEmpty()) values.add(clean);
        }
        return values;
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
        String id = userId();
        result.put("os_type", "web");
        result.put("app", "heybox");
        result.put("client_type", "web");
        result.put("version", "999.0.4");
        result.put("web_version", "2.5");
        result.put("x_client_type", "web");
        result.put("x_app", "heybox_website");
        result.put(SecureStrings.heyboxId(), id);
        if (!id.isEmpty()) result.put(SecureStrings.userid(), id);
        result.put("x_os_type", "Windows");
        result.put("device_info", "Edge");
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
        if (id.isEmpty()) id = userIdFromCookie(getCookie());
        SharedPreferences.Editor editor = prefs.edit();
        if (!id.isEmpty()) editor.putString(SecureStrings.userId(), id);
        if (!name.isEmpty()) editor.putString(USER_NAME, name);
        if (!avatar.isEmpty()) editor.putString(AVATAR, avatar);
        editor.apply();
    }

    void mergeCookies(List<String> headers) {
        if (headers == null || headers.isEmpty()) return;
        Map<String, String> values = cookieMap(getCookie());
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
        normalizeAuthCookies(values);
        String cookie = joinCookies(values);
        saveCookie(cookie);
        persistUserIdFromCookie(cookie);
    }

    boolean hasCookieValue(String key) {
        return !cookieValue(getCookie(), key).isEmpty();
    }

    void putCookieValue(String key, String value) {
        if (key == null || key.isEmpty()) return;
        Map<String, String> values = cookieMap(getCookie());
        if (value == null || value.isEmpty()) values.remove(key);
        else values.put(key, value);
        normalizeAuthCookies(values);
        String cookie = joinCookies(values);
        saveCookie(cookie);
        persistUserIdFromCookie(cookie);
    }

    void removeCookieValue(String key) {
        putCookieValue(key, "");
    }

    private void persistUserIdFromCookie(String cookie) {
        if (!prefs.getString(SecureStrings.userId(), "").isEmpty()) return;
        String id = userIdFromCookie(cookie);
        if (!id.isEmpty()) prefs.edit().putString(SecureStrings.userId(), id).apply();
    }

    private String userIdFromCookie(String cookie) {
        Map<String, String> values = cookieMap(cookie);
        String value = firstCookieValue(values, SecureStrings.userHeyboxId(),
                SecureStrings.xHeyboxId(), "user_" + SecureStrings.heyboxId());
        if (value.isEmpty()) value = firstCookieValue(values, SecureStrings.heyboxId(),
                SecureStrings.userid(), SecureStrings.userId(), "heyboxid");
        return value;
    }

    String authCookieKeysForLog() {
        Map<String, String> values = cookieMap(getCookie());
        StringBuilder result = new StringBuilder();
        appendCookieKey(result, values, SecureStrings.userPkey());
        appendCookieKey(result, values, SecureStrings.xPkey());
        appendCookieKey(result, values, SecureStrings.userHeyboxId());
        appendCookieKey(result, values, SecureStrings.xHeyboxId());
        appendCookieKey(result, values, SecureStrings.xXhhTokenId());
        appendCookieKey(result, values, SecureStrings.heyboxId());
        appendCookieKey(result, values, SecureStrings.userid());
        return result.length() == 0 ? "none" : result.toString();
    }

    private void appendCookieKey(StringBuilder result, Map<String, String> values, String key) {
        if (values == null || key == null || key.isEmpty() || !values.containsKey(key)) return;
        if (result.length() > 0) result.append(',');
        result.append(key);
    }

    private String firstCookieValue(Map<String, String> values, String... keys) {
        if (values == null || keys == null) return "";
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String value = values.get(key);
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private void normalizeAuthCookies(Map<String, String> values) {
        if (values == null || values.isEmpty()) return;
        mirrorCookie(values, SecureStrings.userPkey(), SecureStrings.xPkey());
        mirrorCookie(values, SecureStrings.xPkey(), SecureStrings.userPkey());

        String id = firstCookieValue(values, SecureStrings.userHeyboxId(),
                SecureStrings.xHeyboxId(), SecureStrings.heyboxId(),
                SecureStrings.userid(), SecureStrings.userId(), "heyboxid");
        if (!id.isEmpty()) {
            putCookieIfMissing(values, SecureStrings.userHeyboxId(), id);
            putCookieIfMissing(values, SecureStrings.xHeyboxId(), id);
            putCookieIfMissing(values, SecureStrings.heyboxId(), id);
        }
    }

    private void mirrorCookie(Map<String, String> values, String from, String to) {
        String value = values.get(from);
        if (value != null && !value.isEmpty()) putCookieIfMissing(values, to, value);
    }

    private void putCookieIfMissing(Map<String, String> values, String key, String value) {
        if (key == null || key.isEmpty() || value == null || value.isEmpty()) return;
        String existing = values.get(key);
        if (existing == null || existing.isEmpty()) values.put(key, value);
    }

    private String normalizeCookie(String cookie) {
        Map<String, String> values = cookieMap(cookie);
        normalizeAuthCookies(values);
        return joinCookies(values);
    }

    private String cookieValue(String cookie, String key) {
        if (cookie == null || cookie.isEmpty() || key == null || key.isEmpty()) return "";
        String value = cookieMap(cookie).get(key);
        return value == null ? "" : value;
    }

    private Map<String, String> cookieMap(String cookie) {
        Map<String, String> values = new LinkedHashMap<>();
        if (cookie == null || cookie.isEmpty()) return values;
        String[] parts = cookie.split(";");
        for (String part : parts) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String name = part.substring(0, equals).trim();
            String value = part.substring(equals + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) values.put(name, value);
        }
        return values;
    }

    private String joinCookies(Map<String, String> values) {
        StringBuilder merged = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()
                    || entry.getValue() == null || entry.getValue().isEmpty()) continue;
            if (merged.length() > 0) merged.append("; ");
            merged.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return merged.toString();
    }

    void saveCookie(String value) {
        try {
            String cookie = normalizeCookie(value == null ? "" : value);
            String encrypted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? ModernCookieCrypto.encrypt(cookie) : encryptLegacy(cookie);
            prefs.edit().putString(SecureStrings.encryptedCookieKey(),
                    encrypted)
                    .remove(SecureStrings.cookieKey()).apply();
            persistUserIdFromCookie(cookie);
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
        boolean autoUpdateCheck = autoUpdateCheck();
        boolean splashEnabled = splashEnabled();
        String splashText = splashText();
        int splashDuration = splashDuration();
        String searchHistory = prefs.getString(SEARCH_HISTORY, "[]");
        String blockKeywords = blockKeywords();
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
                .putBoolean(AUTO_UPDATE_CHECK, autoUpdateCheck)
                .putBoolean(SPLASH_ENABLED, splashEnabled)
                .putString(SPLASH_TEXT, splashText)
                .putInt(SPLASH_DURATION, splashDuration)
                .putString(SEARCH_HISTORY, searchHistory)
                .putString(BLOCK_KEYWORDS, blockKeywords)
                .apply();
    }
}
