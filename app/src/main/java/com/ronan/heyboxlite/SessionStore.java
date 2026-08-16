package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class SessionStore {
    static final int MIN_UI_SCALE = 50;
    static final int MAX_UI_SCALE = 160;
    static final int MIN_TEXT_SCALE = 50;
    static final int MAX_TEXT_SCALE = 180;

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String USER_NAME = "user_name";
    private static final String AVATAR = "avatar";
    private static final String NO_IMAGE = "no_image";
    private static final String UI_SCALE = "ui_scale";
    private static final String TEXT_SCALE = "text_scale";
    private static final String PAGE_PADDING = "page_padding";
    private static final String ROUND_SCREEN = "round_screen";
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
    private static final String CROWN_SCROLL_ENABLED = "crown_scroll_enabled";
    private static final String CROWN_SCROLL_SPEED = "crown_scroll_speed";
    private static final String CROWN_HAPTICS_ENABLED = "crown_haptics_enabled";
    private static final String AUTO_UPDATE_CHECK = "auto_update_check";
    private static final String SPLASH_ENABLED = "splash_enabled";
    private static final String SPLASH_TEXT = "splash_text";
    private static final String SPLASH_DURATION = "splash_duration";
    private static final String SHELL_BACK_SWIPE = "shell_back_swipe";
    private static final String CONFIRM_EXIT_ON_BACK = "confirm_exit_on_back";
    private static final String REMEMBER_DETAIL_SCROLL = "remember_detail_scroll";
    private static final String AUTO_OFFLINE_CLEANUP = "auto_offline_cleanup";
    private static final String DOUBLE_TAP_COMMENT_REPLY = "double_tap_comment_reply";
    private static final String PLAY_GIF = "play_gif";
    private static final String NETWORK_MODE = "network_mode";
    private static final String COMMENT_DRAFT_PREFIX = "comment_draft_";
    private static final String TEST_RELEASE_ID = "test_release_id";
    private static final String MOTION_LEVEL = "motion_level";
    private static final String LAST_ANNOUNCEMENT_ID = "last_announcement_id";
    private static final String SEEN_ANNOUNCEMENT_IDS = "seen_announcement_ids";
    private static final String NATIVE_RND_CODE = "native_rnd_code";
    private static final String NATIVE_RND_VERSION = "native_rnd_version";
    private static final String OFFICIAL_PROVIDER_AUTH_IMPORTED = "official_provider_auth_imported";
    private static final String SEARCH_HISTORY = "search_history";
    private static final String BLOCK_KEYWORDS = "block_keywords";
    private static final String PRESENCE_IDENTITY_UPLOADED = "presence_identity_uploaded_";
    private static final String PRESENCE_DEVICE_ID = "presence_device_id";
    private static final String APP_BLOCKED = "app_blocked";
    private static final String APP_BLOCK_MESSAGE = "app_block_message";
    static final String DEFAULT_SPLASH_TEXT = "方寸之间，看见热爱";
    private static final String LEGACY_PREFIX = "L1:";
    private static final String[] LEGACY_SIGN_IN_KEYS = {
            "last_sign_attempt_date", "last_sign_success_date", "sign_summary",
            "signin_mobile_user_id", "signin_mobile_pkey", "signin_mobile_token",
            "signin_mobile_device_id", "signin_mobile_device_info",
            "signin_mobile_os_version", "signin_mobile_version", "signin_mobile_build",
            "signin_mobile_dw", "signin_mobile_channel", "signin_mobile_x_app",
            "signin_mobile_source", "signin_mobile_imported_at", "signin_replay_method",
            "signin_replay_url", "signin_replay_cookie", "signin_replay_user_agent",
            "signin_replay_referer"
    };

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
        if (prefs.getString(PRESENCE_DEVICE_ID, "").isEmpty()) {
            prefs.edit().putString(PRESENCE_DEVICE_ID, createPresenceDeviceId()).apply();
        }
        purgeLegacySignInState();
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

    Context appContext() {
        return context;
    }

    String userName() {
        return prefs.getString(USER_NAME, "");
    }

    String avatar() {
        return prefs.getString(AVATAR, "");
    }

    boolean presenceIdentityUploaded() {
        String id = userId();
        return !id.isEmpty() && prefs.getBoolean(PRESENCE_IDENTITY_UPLOADED + id, false);
    }

    void markPresenceIdentityUploaded() {
        String id = userId();
        if (!id.isEmpty()) {
            prefs.edit().putBoolean(PRESENCE_IDENTITY_UPLOADED + id, true).apply();
        }
    }

    boolean appBlocked() {
        return prefs.getBoolean(APP_BLOCKED, false);
    }

    String appBlockMessage() {
        return prefs.getString(APP_BLOCK_MESSAGE, "");
    }

    void setAppBlocked(boolean blocked, String message) {
        prefs.edit()
                .putBoolean(APP_BLOCKED, blocked)
                .putString(APP_BLOCK_MESSAGE, blocked && message != null ? message.trim() : "")
                .apply();
    }

    boolean noImage() {
        return prefs.getBoolean(NO_IMAGE, false);
    }

    void setNoImage(boolean value) {
        prefs.edit().putBoolean(NO_IMAGE, value).apply();
    }

    int uiScale() {
        return Math.round(configuredUiScale() * watchUiFactor(false));
    }

    int configuredUiScale() {
        return clamp(prefs.getInt(UI_SCALE, 100), MIN_UI_SCALE, MAX_UI_SCALE);
    }

    void setUiScale(int value) {
        prefs.edit().putInt(UI_SCALE, clamp(value, MIN_UI_SCALE, MAX_UI_SCALE)).apply();
    }

    int textScale() {
        return Math.round(configuredTextScale() * watchUiFactor(true));
    }

    int configuredTextScale() {
        return clamp(prefs.getInt(TEXT_SCALE, 100), MIN_TEXT_SCALE, MAX_TEXT_SCALE);
    }

    void setTextScale(int value) {
        prefs.edit().putInt(TEXT_SCALE, clamp(value, MIN_TEXT_SCALE, MAX_TEXT_SCALE)).apply();
    }

    /** Rectangular watches use compact sizing; only round watches receive corner insets. */
    private float watchUiFactor(boolean text) {
        boolean round = usesRoundLayout();
        boolean watch = round || RoundLayoutMetrics.isWatchDisplay(context);
        if (!watch) return 1.0f;
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int shortPixels = Math.min(metrics.widthPixels, metrics.heightPixels);
        float shortDp = shortPixels / Math.max(1.0f, metrics.density);
        if (round) {
            if (shortPixels <= 420 || shortDp <= 220.0f) return text ? 0.86f : 0.78f;
            if (shortPixels <= 520 || shortDp <= 270.0f) return text ? 0.89f : 0.82f;
            return text ? 0.92f : 0.86f;
        }
        if (shortPixels <= 420 || shortDp <= 220.0f) return text ? 0.90f : 0.82f;
        return text ? 0.93f : 0.87f;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    boolean usesRoundLayout() {
        return RoundLayoutMetrics.isRoundDisplay(context)
                || prefs.getBoolean(ROUND_SCREEN, false);
    }

    int pagePadding() {
        return prefs.getInt(PAGE_PADDING, 8);
    }

    void setPagePadding(int value) {
        prefs.edit().putInt(PAGE_PADDING, value).apply();
    }

    boolean roundScreen() {
        return prefs.getBoolean(ROUND_SCREEN, false);
    }

    void setRoundScreen(boolean value) {
        prefs.edit().putBoolean(ROUND_SCREEN, value).apply();
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

    boolean crownScrollEnabled() {
        return prefs.getBoolean(CROWN_SCROLL_ENABLED, true);
    }

    void setCrownScrollEnabled(boolean value) {
        prefs.edit().putBoolean(CROWN_SCROLL_ENABLED, value).apply();
    }

    int crownScrollSpeed() {
        return CrownScrollController.clampSpeed(prefs.getInt(
                CROWN_SCROLL_SPEED, CrownScrollController.DEFAULT_SPEED_PERCENT));
    }

    void setCrownScrollSpeed(int value) {
        prefs.edit().putInt(CROWN_SCROLL_SPEED,
                CrownScrollController.clampSpeed(value)).apply();
    }

    boolean crownHapticsEnabled() {
        return prefs.getBoolean(CROWN_HAPTICS_ENABLED, true);
    }

    void setCrownHapticsEnabled(boolean value) {
        prefs.edit().putBoolean(CROWN_HAPTICS_ENABLED, value).apply();
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

    boolean shellBackSwipe() {
        return prefs.getBoolean(SHELL_BACK_SWIPE, true);
    }

    void setShellBackSwipe(boolean value) {
        prefs.edit().putBoolean(SHELL_BACK_SWIPE, value).apply();
    }

    boolean confirmExitOnBack() {
        return prefs.getBoolean(CONFIRM_EXIT_ON_BACK, false);
    }

    void setConfirmExitOnBack(boolean value) {
        prefs.edit().putBoolean(CONFIRM_EXIT_ON_BACK, value).apply();
    }

    boolean rememberDetailScroll() {
        return prefs.getBoolean(REMEMBER_DETAIL_SCROLL, true);
    }

    void setRememberDetailScroll(boolean value) {
        prefs.edit().putBoolean(REMEMBER_DETAIL_SCROLL, value).apply();
    }

    boolean autoOfflineCleanup() {
        return prefs.getBoolean(AUTO_OFFLINE_CLEANUP, true);
    }

    void setAutoOfflineCleanup(boolean value) {
        prefs.edit().putBoolean(AUTO_OFFLINE_CLEANUP, value).apply();
    }

    boolean playGif() {
        return prefs.getBoolean(PLAY_GIF, true);
    }

    void setPlayGif(boolean value) {
        prefs.edit().putBoolean(PLAY_GIF, value).apply();
    }

    int networkMode() {
        return Math.max(0, Math.min(2, prefs.getInt(NETWORK_MODE, 1)));
    }

    void setNetworkMode(int value) {
        int mode = Math.max(0, Math.min(2, value));
        prefs.edit()
                .putInt(NETWORK_MODE, mode)
                .putBoolean(ORIGINAL_IMAGES, mode == 2)
                .putBoolean(PLAY_GIF, mode != 0)
                .apply();
    }

    int testReleaseId() {
        return Math.max(0, prefs.getInt(TEST_RELEASE_ID, 0));
    }

    void setTestReleaseId(int value) {
        prefs.edit().putInt(TEST_RELEASE_ID, Math.max(0, value)).apply();
    }

    String commentDraft(String linkId) {
        return prefs.getString(commentDraftKey(linkId), "");
    }

    void setCommentDraft(String linkId, String value) {
        String key = commentDraftKey(linkId);
        String clean = value == null ? "" : value;
        if (clean.isEmpty()) prefs.edit().remove(key).apply();
        else prefs.edit().putString(key, clean).apply();
    }

    private String commentDraftKey(String linkId) {
        String value = linkId == null ? "" : linkId.trim();
        return COMMENT_DRAFT_PREFIX + Integer.toHexString(value.hashCode());
    }

    /** 动画等级：0 关闭 / 1 精简 / 2 完整。首次按设备内存一次性判定并固化，之后完全听用户设置。 */
    int motionLevel() {
        int stored = prefs.getInt(MOTION_LEVEL, -1);
        if (stored >= 0) return MotionLevel.clamp(stored);
        int resolved = detectDefaultMotionLevel();
        prefs.edit().putInt(MOTION_LEVEL, resolved).apply();
        return resolved;
    }

    void setMotionLevel(int value) {
        prefs.edit().putInt(MOTION_LEVEL, MotionLevel.clamp(value)).apply();
    }

    private int detectDefaultMotionLevel() {
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                if (Build.VERSION.SDK_INT >= 19 && manager.isLowRamDevice()) {
                    return MotionLevel.OFF;
                }
                if (manager.getMemoryClass() <= 64) return MotionLevel.OFF;
            }
        } catch (RuntimeException ignored) {
        }
        return MotionLevel.REDUCED;
    }

    boolean doubleTapCommentReply() {
        return prefs.getBoolean(DOUBLE_TAP_COMMENT_REPLY, true);
    }

    void setDoubleTapCommentReply(boolean value) {
        prefs.edit().putBoolean(DOUBLE_TAP_COMMENT_REPLY, value).apply();
    }

    String lastAnnouncementId() {
        return prefs.getString(LAST_ANNOUNCEMENT_ID, "");
    }

    void setLastAnnouncementId(String value) {
        prefs.edit().putString(LAST_ANNOUNCEMENT_ID, value == null ? "" : value).apply();
    }

    boolean isAnnouncementSeen(String id) {
        String clean = id == null ? "" : id.trim();
        if (clean.isEmpty()) return false;
        if (clean.equals(lastAnnouncementId())) return true;
        JSONArray array = seenAnnouncementArray();
        for (int i = 0; i < array.length(); i++) {
            if (clean.equals(array.optString(i))) return true;
        }
        return false;
    }

    void markAnnouncementSeen(String id) {
        String clean = id == null ? "" : id.trim();
        if (clean.isEmpty()) return;
        List<String> ids = new ArrayList<>();
        String last = lastAnnouncementId();
        if (!last.isEmpty()) ids.add(last);
        JSONArray array = seenAnnouncementArray();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (!value.isEmpty() && !ids.contains(value)) ids.add(value);
        }
        ids.remove(clean);
        ids.add(0, clean);
        while (ids.size() > 50) ids.remove(ids.size() - 1);
        JSONArray next = new JSONArray();
        for (String value : ids) next.put(value);
        prefs.edit()
                .putString(LAST_ANNOUNCEMENT_ID, clean)
                .putString(SEEN_ANNOUNCEMENT_IDS, next.toString())
                .apply();
    }

    private JSONArray seenAnnouncementArray() {
        try {
            return new JSONArray(prefs.getString(SEEN_ANNOUNCEMENT_IDS, "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    boolean saveNativeRndConfig(JSONObject body) {
        if (body == null) return false;
        JSONObject result = body.optJSONObject("result");
        if (result == null) return false;
        return saveNativeRndConfig(result.optString("code"), result.optInt("version", -1));
    }

    boolean saveNativeRndConfig(String code, int version) {
        String clean = code == null ? "" : code.trim();
        if (clean.isEmpty() || version < 0) return false;
        prefs.edit()
                .putString(NATIVE_RND_CODE, clean)
                .putInt(NATIVE_RND_VERSION, version)
                .apply();
        return true;
    }

    String nativeRndCode() {
        return prefs.getString(NATIVE_RND_CODE, "");
    }

    int nativeRndVersion() {
        return prefs.getInt(NATIVE_RND_VERSION, -1);
    }

    List<String> searchHistory() {
        List<String> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(SEARCH_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i).trim();
                if (!value.isEmpty()) values.add(value);
            }
        } catch (JSONException ignored) {
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
                .putBoolean(ROUND_SCREEN, false)
                .remove(PRIMARY_COLOR)
                .remove(SECONDARY_COLOR)
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

    Map<String, String> mobileCommonParams() {
        Map<String, String> result = officialMobileParams(true);
        result.put("client_type", "mobile");
        return result;
    }

    Map<String, String> gatewayMobileParams() {
        Map<String, String> result = mobileCommonParams();
        result.remove(SecureStrings.time());
        result.put("dw", com.max.xiaoheihe.utils.i.e());
        result.put("channel", com.max.xiaoheihe.utils.f.x0());
        return result;
    }

    String deviceIdentifier() {
        return prefs.getString(SecureStrings.deviceId(), "");
    }

    String presenceDeviceIdentifier() {
        return prefs.getString(PRESENCE_DEVICE_ID, "");
    }

    private String createPresenceDeviceId() {
        String source = "";
        try {
            source = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (RuntimeException ignored) {
        }
        if (source == null || source.trim().isEmpty()
                || "9774d56d682e549c".equalsIgnoreCase(source.trim())
                || source.matches("0+")) {
            source = UUID.randomUUID().toString();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("heybox-lite-presence-v1:"
                    .getBytes(UTF_8));
            digest.update(source.trim().getBytes(UTF_8));
            byte[] bytes = digest.digest();
            StringBuilder value = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) value.append(String.format(Locale.US, "%02x", item & 0xff));
            return value.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    Map<String, String> officialMobileParams(boolean includeDeviceParams) {
        Map<String, String> result = new LinkedHashMap<>();
        String id = userId();
        String safeId = id.isEmpty() ? "-1" : id;
        result.put(SecureStrings.heyboxId(), safeId);
        if (!id.isEmpty()) {
            result.put(SecureStrings.userid(), id);
            result.put(SecureStrings.userId(), id);
        }
        if (!includeDeviceParams) return result;
        String release = Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        result.put("app", "heybox");
        String androidId = androidDeviceIdentifier();
        result.put(SecureStrings.deviceId(), androidId);
        result.put("imei", androidId);
        result.put("device_info", model.trim());
        result.put("os_type", "Android");
        result.put("os_version", release.trim());
        result.put("x_os_type", "Android");
        result.put("x_client_type", "mobile");
        result.put("x_app", "heybox");
        result.put("version", com.max.xiaoheihe.utils.f.B0());
        result.put("build", com.max.xiaoheihe.utils.f.buildCode());
        result.put("time_zone", TimeZone.getDefault().getID());
        result.put(SecureStrings.time(), String.valueOf(System.currentTimeMillis() / 1000L));
        result.put("channel", "heybox");
        return result;
    }

    String officialMobileCookie(boolean addClientKey) {
        Map<String, String> values = cookieMap(getCookie());
        List<String> parts = new ArrayList<>();
        String pkey = officialPkey(values);
        appendCookiePart(parts, officialPkeyKey(), pkey);
        if (addClientKey) appendCookiePart(parts, SecureStrings.xPkey(), pkey);
        appendCookiePart(parts, SecureStrings.xXhhTokenId(),
                values.get(SecureStrings.xXhhTokenId()));
        appendRestCookies(parts, values);
        if (addClientKey) {
            String id = firstCookieValue(values, SecureStrings.xHeyboxId(),
                    SecureStrings.userHeyboxId(), SecureStrings.heyboxId(),
                    SecureStrings.userid(), SecureStrings.userId(), "heyboxid");
            appendCookiePart(parts, SecureStrings.xHeyboxId(), id);
        }
        StringBuilder cookie = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (cookie.length() > 0) cookie.append(';');
            cookie.append(part);
        }
        return cookie.toString();
    }

    String officialRequestCookie(boolean includeClientKeys) {
        Map<String, String> values = cookieMap(getCookie());
        List<String> parts = new ArrayList<>();
        String pkey = officialPkey(values);
        appendCookiePart(parts, officialPkeyKey(), pkey);
        if (includeClientKeys) appendCookiePart(parts, SecureStrings.xPkey(), pkey);
        appendCookiePart(parts, SecureStrings.xXhhTokenId(),
                values.get(SecureStrings.xXhhTokenId()));
        appendRawCookiePart(parts, getCookie());
        if (includeClientKeys) {
            String id = firstCookieValue(values, SecureStrings.xHeyboxId(),
                    SecureStrings.userHeyboxId(), SecureStrings.heyboxId(),
                    SecureStrings.userid(), SecureStrings.userId(), "heyboxid");
            appendCookiePart(parts, SecureStrings.xHeyboxId(), id);
        }
        return joinCookieParts(parts);
    }

    String officialMinimalCookie(boolean includeClientKeys) {
        Map<String, String> values = cookieMap(getCookie());
        List<String> parts = new ArrayList<>();
        String pkey = officialPkey(values);
        appendCookiePart(parts, officialPkeyKey(), pkey);
        if (includeClientKeys) appendCookiePart(parts, SecureStrings.xPkey(), pkey);
        appendCookiePart(parts, SecureStrings.xXhhTokenId(),
                values.get(SecureStrings.xXhhTokenId()));
        if (includeClientKeys) {
            String id = firstCookieValue(values, SecureStrings.xHeyboxId(),
                    SecureStrings.userHeyboxId(), SecureStrings.heyboxId(),
                    SecureStrings.userid(), SecureStrings.userId(), "heyboxid");
            appendCookiePart(parts, SecureStrings.xHeyboxId(), id);
        }
        return joinCookieParts(parts);
    }

    String officialBridgeCookie(boolean includeClientKeys) {
        try {
            String raw = getCookie();
            Map<String, String> values = cookieMap(raw);
            com.max.xiaoheihe.utils.m0.init(userId(), officialPkey(values));
            com.max.xiaoheihe.utils.i.init(firstCookieValue(values,
                    SecureStrings.xXhhTokenId()));
            okhttp3.a0 request = new okhttp3.a0.a()
                    .a(SecureStrings.cookieHeader(), raw)
                    .b();
            String cookie = new com.max.xiaoheihe.router.serviceimpl.k()
                    .a(includeClientKeys, request);
            return cookie == null || cookie.trim().isEmpty()
                    ? officialRequestCookie(includeClientKeys) : cookie.trim();
        } catch (Throwable ignored) {
            return officialRequestCookie(includeClientKeys);
        }
    }

    String officialPkey() {
        return officialPkey(cookieMap(getCookie()));
    }

    String officialXhhToken() {
        Map<String, String> values = cookieMap(getCookie());
        return firstCookieValue(values, SecureStrings.xXhhTokenId());
    }

    boolean hasOfficialProviderAuth() {
        return prefs.getBoolean(OFFICIAL_PROVIDER_AUTH_IMPORTED, false);
    }

    String importOfficialProviderAuthForLog() {
        Cursor cursor = null;
        try {
            String usedUri = "";
            for (String uri : officialProviderUris()) {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
                try {
                    Uri providerUri = Uri.parse(uri);
                    if (!OfficialAppVerifier.isProviderTrusted(context, providerUri)) {
                        continue;
                    }
                    cursor = context.getContentResolver().query(
                            providerUri, null, null, null, null);
                    if (cursor == null) continue;
                    if (!cursor.moveToFirst()) continue;
                    usedUri = uri;
                    break;
                } catch (Throwable ignored) {
                    if (cursor != null) {
                        cursor.close();
                        cursor = null;
                    }
                }
            }
            if (cursor == null) {
                setOfficialProviderAuthImported(false);
                return "provider=null";
            }
            if (usedUri.isEmpty()) {
                setOfficialProviderAuthImported(false);
                return "provider=empty";
            }
            String id = cursorValue(cursor, SecureStrings.heyboxId());
            String pkey = cursorValue(cursor, officialPkeyKey());
            String deviceId = cursorValue(cursor, SecureStrings.deviceId());
            String token = cursorValue(cursor, SecureStrings.xXhhTokenId());
            if (id.isEmpty() && pkey.isEmpty() && token.isEmpty()) {
                setOfficialProviderAuthImported(false);
                return "provider=empty-auth uri=" + providerUriName(usedUri)
                        + " columns=" + cursorColumnsForLog(cursor);
            }
            Map<String, String> values = cookieMap(getCookie());
            if (!pkey.isEmpty()) {
                values.put(SecureStrings.userPkey(), pkey);
                values.put(SecureStrings.xPkey(), pkey);
            }
            if (!token.isEmpty()) values.put(SecureStrings.xXhhTokenId(), token);
            if (!id.isEmpty()) {
                values.put(SecureStrings.heyboxId(), id);
                values.put(SecureStrings.userHeyboxId(), id);
                values.put(SecureStrings.xHeyboxId(), id);
            }
            normalizeAuthCookies(values);
            saveCookie(joinCookies(values));
            SharedPreferences.Editor editor = prefs.edit();
            if (!id.isEmpty()) editor.putString(SecureStrings.userId(), id);
            if (!deviceId.isEmpty()) editor.putString(SecureStrings.deviceId(), deviceId);
            editor.putBoolean(OFFICIAL_PROVIDER_AUTH_IMPORTED, true);
            editor.apply();
            return "provider=ok uri=" + providerUriName(usedUri)
                    + " idLen=" + id.length()
                    + " pkeyLen=" + pkey.length()
                    + " tokenLen=" + token.length()
                    + " deviceLen=" + deviceId.length();
        } catch (Throwable error) {
            setOfficialProviderAuthImported(false);
            return "provider=error " + error.getClass().getSimpleName();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static String[] officialProviderUris() {
        return new String[] {
                "content://com.max.xiaoheihe.statusprovider/login",
                "content://com.max.xiaoheihe.statusprovider/login/",
                "content://com.max.xiaoheihe.statusprovider",
                "content://com.max.xiaoheihe.statusprovider/",
                "content://com.max.xiaoheihe.statusprovider.login"
        };
    }

    private static String providerUriName(String uri) {
        if (uri == null) return "";
        int index = uri.indexOf("statusprovider");
        if (index < 0) return "custom";
        String tail = uri.substring(index + "statusprovider".length());
        if (tail.isEmpty()) return "root";
        return tail.replace('/', '_').replace(':', '_');
    }

    private static String cursorColumnsForLog(Cursor cursor) {
        if (cursor == null) return "none";
        try {
            String[] names = cursor.getColumnNames();
            if (names == null || names.length == 0) return "none";
            StringBuilder out = new StringBuilder();
            for (String name : names) {
                if (name == null || name.trim().isEmpty()) continue;
                if (out.length() > 0) out.append(',');
                out.append(name.trim());
                if (out.length() > 180) {
                    out.append("...");
                    break;
                }
            }
            return out.length() == 0 ? "none" : out.toString();
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private void setOfficialProviderAuthImported(boolean imported) {
        prefs.edit().putBoolean(OFFICIAL_PROVIDER_AUTH_IMPORTED, imported).apply();
    }

    private String officialPkey(Map<String, String> values) {
        return firstCookieValue(values, officialPkeyKey(),
                SecureStrings.userPkey(), SecureStrings.xPkey());
    }

    private static String cursorValue(Cursor cursor, String column) {
        if (cursor == null || column == null || column.isEmpty()) return "";
        try {
            int index = cursor.getColumnIndex(column);
            if (index < 0) return "";
            String value = cursor.getString(index);
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String androidDeviceIdentifier() {
        String imported = deviceIdentifier();
        if (imported != null && !imported.trim().isEmpty()) return imported.trim();
        try {
            String value = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        } catch (Throwable ignored) {
        }
        return deviceIdentifier();
    }

    String officialMobileCookieKeysForLog(boolean addClientKey) {
        return cookieKeysForLog(officialMobileCookie(addClientKey));
    }

    String officialRequestCookieKeysForLog(boolean includeClientKeys) {
        return cookieKeysForLog(officialRequestCookie(includeClientKeys));
    }

    String officialMinimalCookieKeysForLog(boolean includeClientKeys) {
        return cookieKeysForLog(officialMinimalCookie(includeClientKeys));
    }

    String officialBridgeCookieKeysForLog(boolean includeClientKeys) {
        return cookieKeysForLog(officialBridgeCookie(includeClientKeys));
    }

    private String cookieKeysForLog(String cookie) {
        Map<String, String> values = cookieMap(cookie);
        StringBuilder result = new StringBuilder();
        for (String key : values.keySet()) {
            if (result.length() > 0) result.append(',');
            result.append(key);
        }
        return result.length() == 0 ? "none" : result.toString();
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

    private void appendCookiePart(List<String> parts, String key, String value) {
        if (parts == null || key == null || key.isEmpty()
                || value == null || value.isEmpty()) return;
        parts.add(key + "=" + value);
    }

    private void appendRawCookiePart(List<String> parts, String value) {
        if (parts == null || value == null) return;
        String clean = value.trim();
        if (clean.isEmpty()) return;
        parts.add(clean);
    }

    private String joinCookieParts(List<String> parts) {
        StringBuilder cookie = new StringBuilder();
        if (parts == null) return "";
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (cookie.length() > 0) cookie.append(';');
            cookie.append(part.trim());
        }
        return cookie.toString();
    }

    private void appendRestCookies(List<String> parts, Map<String, String> values) {
        if (parts == null || values == null || values.isEmpty()) return;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || value == null || value.isEmpty()) continue;
            if (isOfficialAuthCookie(key)) continue;
            appendCookiePart(parts, key, value);
        }
    }

    private boolean isOfficialAuthCookie(String key) {
        return officialPkeyKey().equals(key)
                || SecureStrings.userPkey().equals(key)
                || SecureStrings.xPkey().equals(key)
                || SecureStrings.userHeyboxId().equals(key)
                || SecureStrings.xHeyboxId().equals(key)
                || SecureStrings.xXhhTokenId().equals(key)
                || SecureStrings.heyboxId().equals(key)
                || SecureStrings.userid().equals(key)
                || SecureStrings.userId().equals(key)
                || "heyboxid".equals(key)
                || ("user_" + SecureStrings.heyboxId()).equals(key);
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
        String pkey = firstCookieValue(values, officialPkeyKey(),
                SecureStrings.userPkey(), SecureStrings.xPkey());
        putCookieAliases(values, pkey, officialPkeyKey(),
                SecureStrings.userPkey(), SecureStrings.xPkey());

        String id = firstCookieValue(values, SecureStrings.userHeyboxId(),
                SecureStrings.xHeyboxId(), SecureStrings.heyboxId(),
                SecureStrings.userid(), SecureStrings.userId(), "heyboxid");
        putCookieAliases(values, id, SecureStrings.userHeyboxId(),
                SecureStrings.xHeyboxId(), SecureStrings.heyboxId());
    }

    private void putCookieAliases(Map<String, String> values, String value, String... keys) {
        if (value == null || value.isEmpty()) return;
        for (String key : keys) {
            putCookieIfMissing(values, key, value);
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

    private static String officialPkeyKey() {
        return com.max.xiaoheihe.utils.p0.M();
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
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (removesOnLogout(key)) editor.remove(key);
        }
        editor.apply();
    }

    static boolean removesOnLogout(String key) {
        if (key == null || key.isEmpty()) return false;
        if (SecureStrings.cookieKey().equals(key)
                || SecureStrings.encryptedCookieKey().equals(key)
                || SecureStrings.userId().equals(key)
                || USER_NAME.equals(key)
                || AVATAR.equals(key)
                || OFFICIAL_PROVIDER_AUTH_IMPORTED.equals(key)) {
            return true;
        }
        return key.startsWith(COMMENT_DRAFT_PREFIX)
                || key.startsWith(PRESENCE_IDENTITY_UPLOADED)
                || isLegacySignInKey(key);
    }

    private void purgeLegacySignInState() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : LEGACY_SIGN_IN_KEYS) editor.remove(key);
        editor.apply();
    }

    private static boolean isLegacySignInKey(String key) {
        for (String legacyKey : LEGACY_SIGN_IN_KEYS) {
            if (legacyKey.equals(key)) return true;
        }
        return false;
    }

}
