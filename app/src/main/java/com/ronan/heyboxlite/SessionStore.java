package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
    private static final String HOME_SWIPE_EXIT = "home_swipe_exit";
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
    private static final String GAME_CARD_NO_IMAGE = "game_card_no_image";
    private static final String VIDEO_AUTOPLAY = "video_autoplay";
    private static final String VIDEO_LOOP = "video_loop";
    private static final String VIDEO_MUTED = "video_muted";
    // Keep the old preference key so existing users retain their gesture setting.
    private static final String VIDEO_LONG_PRESS_FAST_FORWARD = "video_double_tap_seek";
    private static final String VIDEO_EXTERNAL_FALLBACK = "video_external_fallback";
    private static final String VIDEO_DISPLAY_MODE = "video_display_mode";
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
    private final LegacyCookieCrypto legacyCookieCrypto;
    private final OfficialSessionCredentials officialCredentials;
    private volatile String cachedEncryptedCookie;
    private volatile String cachedCookie;

    SessionStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE);
        legacyCookieCrypto = new LegacyCookieCrypto(this.context);
        officialCredentials = new OfficialSessionCredentials(this, this.context);
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
        if (encrypted.isEmpty()) {
            migratePlainCookieIfNeeded();
            encrypted = prefs.getString(SecureStrings.encryptedCookieKey(), "");
        }
        if (!encrypted.isEmpty()) {
            if (encrypted.equals(cachedEncryptedCookie) && cachedCookie != null) {
                return cachedCookie;
            }
            String cookie = decrypt(encrypted);
            if (!cookie.isEmpty() && encrypted.startsWith(LegacyCookieCrypto.PREFIX)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                saveCookie(cookie);
            }
            String normalized = SessionCookieCodec.normalize(cookie);
            if (!normalized.equals(cookie)) saveCookie(normalized);
            cachedEncryptedCookie = prefs.getString(
                    SecureStrings.encryptedCookieKey(), encrypted);
            cachedCookie = normalized;
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
        String fromCookie = SessionCookieCodec.userId(getCookie());
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

    boolean homeSwipeExit() {
        return prefs.getBoolean(HOME_SWIPE_EXIT, false);
    }

    void setHomeSwipeExit(boolean value) {
        prefs.edit().putBoolean(HOME_SWIPE_EXIT, value).apply();
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

    boolean gameCardNoImage() {
        return prefs.getBoolean(GAME_CARD_NO_IMAGE, false);
    }

    void setGameCardNoImage(boolean value) {
        prefs.edit().putBoolean(GAME_CARD_NO_IMAGE, value).apply();
    }

    boolean videoAutoplay() {
        return prefs.getBoolean(VIDEO_AUTOPLAY, true);
    }

    void setVideoAutoplay(boolean value) {
        prefs.edit().putBoolean(VIDEO_AUTOPLAY, value).apply();
    }

    boolean videoLoop() {
        return prefs.getBoolean(VIDEO_LOOP, false);
    }

    void setVideoLoop(boolean value) {
        prefs.edit().putBoolean(VIDEO_LOOP, value).apply();
    }

    boolean videoMuted() {
        return prefs.getBoolean(VIDEO_MUTED, false);
    }

    void setVideoMuted(boolean value) {
        prefs.edit().putBoolean(VIDEO_MUTED, value).apply();
    }

    boolean videoLongPressFastForward() {
        return prefs.getBoolean(VIDEO_LONG_PRESS_FAST_FORWARD, true);
    }

    void setVideoLongPressFastForward(boolean value) {
        prefs.edit().putBoolean(VIDEO_LONG_PRESS_FAST_FORWARD, value).apply();
    }

    boolean videoExternalFallback() {
        return prefs.getBoolean(VIDEO_EXTERNAL_FALLBACK, true);
    }

    void setVideoExternalFallback(boolean value) {
        prefs.edit().putBoolean(VIDEO_EXTERNAL_FALLBACK, value).apply();
    }

    int videoDisplayMode() {
        return Math.max(0, Math.min(1, prefs.getInt(VIDEO_DISPLAY_MODE, 0)));
    }

    void setVideoDisplayMode(int value) {
        prefs.edit().putInt(VIDEO_DISPLAY_MODE, Math.max(0, Math.min(1, value))).apply();
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
        return officialCredentials.commonParams();
    }

    Map<String, String> mobileCommonParams() {
        return officialCredentials.mobileCommonParams();
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
        return officialCredentials.officialMobileParams(includeDeviceParams);
    }

    String officialMobileCookie(boolean addClientKey) {
        return officialCredentials.officialMobileCookie(addClientKey);
    }

    String officialRequestCookie(boolean includeClientKeys) {
        return officialCredentials.officialRequestCookie(includeClientKeys);
    }

    String officialMinimalCookie(boolean includeClientKeys) {
        return officialCredentials.officialMinimalCookie(includeClientKeys);
    }

    String officialBridgeCookie(boolean includeClientKeys) {
        return officialCredentials.officialBridgeCookie(includeClientKeys);
    }

    String officialPkey() {
        return officialCredentials.officialPkey();
    }

    String officialXhhToken() {
        return officialCredentials.officialXhhToken();
    }

    String officialMobileCookieKeysForLog(boolean addClientKey) {
        return officialCredentials.officialMobileCookieKeysForLog(addClientKey);
    }

    String officialRequestCookieKeysForLog(boolean includeClientKeys) {
        return officialCredentials.officialRequestCookieKeysForLog(includeClientKeys);
    }

    String officialMinimalCookieKeysForLog(boolean includeClientKeys) {
        return officialCredentials.officialMinimalCookieKeysForLog(includeClientKeys);
    }

    String officialBridgeCookieKeysForLog(boolean includeClientKeys) {
        return officialCredentials.officialBridgeCookieKeysForLog(includeClientKeys);
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
        if (id.isEmpty()) id = SessionCookieCodec.userId(getCookie());
        SharedPreferences.Editor editor = prefs.edit();
        if (!id.isEmpty()) editor.putString(SecureStrings.userId(), id);
        if (!name.isEmpty()) editor.putString(USER_NAME, name);
        if (!avatar.isEmpty()) editor.putString(AVATAR, avatar);
        editor.apply();
    }

    void mergeCookies(List<String> headers) {
        if (headers == null || headers.isEmpty()) return;
        Map<String, String> values = SessionCookieCodec.parse(getCookie());
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
        saveCookie(cookie);
        persistUserIdFromCookie(cookie);
    }

    boolean hasCookieValue(String key) {
        return !SessionCookieCodec.value(getCookie(), key).isEmpty();
    }

    void putCookieValue(String key, String value) {
        if (key == null || key.isEmpty()) return;
        Map<String, String> values = SessionCookieCodec.parse(getCookie());
        if (value == null || value.isEmpty()) values.remove(key);
        else values.put(key, value);
        SessionCookieCodec.normalizeAuth(values);
        String cookie = SessionCookieCodec.join(values);
        saveCookie(cookie);
        persistUserIdFromCookie(cookie);
    }

    void removeCookieValue(String key) {
        putCookieValue(key, "");
    }

    private void persistUserIdFromCookie(String cookie) {
        if (!prefs.getString(SecureStrings.userId(), "").isEmpty()) return;
        String id = SessionCookieCodec.userId(cookie);
        if (!id.isEmpty()) prefs.edit().putString(SecureStrings.userId(), id).apply();
    }

    String authCookieKeysForLog() {
        return SessionCookieCodec.authKeysForLog(getCookie());
    }

    void saveCookie(String value) {
        try {
            String cookie = SessionCookieCodec.normalize(value == null ? "" : value);
            String encrypted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? ModernCookieCrypto.encrypt(cookie) : legacyCookieCrypto.encrypt(cookie);
            prefs.edit().putString(SecureStrings.encryptedCookieKey(),
                    encrypted)
                    .remove(SecureStrings.cookieKey()).apply();
            cachedEncryptedCookie = encrypted;
            cachedCookie = cookie;
            persistUserIdFromCookie(cookie);
        } catch (Exception error) {
            Log.w("SessionStore", "Unable to encrypt session cookie", error);
            prefs.edit().remove(SecureStrings.cookieKey()).apply();
        }
    }

    private String decrypt(String value) {
        try {
            if (value.startsWith(LegacyCookieCrypto.PREFIX)) return legacyCookieCrypto.decrypt(value);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return ModernCookieCrypto.decrypt(value);
            }
            return "";
        } catch (ModernCookieCrypto.InvalidPayloadException error) {
            prefs.edit().remove(SecureStrings.encryptedCookieKey()).apply();
            cachedEncryptedCookie = null;
            cachedCookie = null;
            Log.w("SessionStore", "Discarded malformed encrypted session", error);
            return "";
        } catch (ModernCookieCrypto.KeyUnavailableException error) {
            Log.w("SessionStore", "Cookie key is temporarily unavailable", error);
            return "";
        } catch (Exception error) {
            if (value.startsWith(LegacyCookieCrypto.PREFIX)) {
                prefs.edit().remove(SecureStrings.encryptedCookieKey()).apply();
            }
            Log.w("SessionStore", "Unable to decrypt session cookie", error);
            return "";
        }
    }

    void clearSession() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (removesOnLogout(key)) editor.remove(key);
        }
        editor.apply();
        cachedEncryptedCookie = null;
        cachedCookie = null;
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
