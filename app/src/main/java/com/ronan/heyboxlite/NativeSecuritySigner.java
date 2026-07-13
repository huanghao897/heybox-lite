package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import com.graphice.shaderar.ShaderManager;
import com.max.xiaoheihe.utils.NDKTools;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

final class NativeSecuritySigner {
    interface Logger {
        void log(String message);
    }

    private static final String CHUNK_FLAG =
            "PAENEHAMGACOBHIEMIHIJLKJPMMHJMMQABCNGBPPENCENP";
    private static final String BLEND_MODE =
            "MFANEHAMGACOBHIEMIHIJLKJPMMHJMMLABCNGBPPENCENPOM";
    private static boolean initialized;
    private static boolean unavailable;
    private static String unavailableReason = "";

    private NativeSecuritySigner() {}

    static boolean canTry() {
        return !unavailable;
    }

    static Map<String, String> sign(Context context, SessionStore session,
                                    String path, Logger logger) {
        return sign(context, session == null ? "" : session.userId(), path, logger);
    }

    static Map<String, String> sign(Context context, String userId,
                                    String path, Logger logger) {
        return sign(context, userId, "", path, null, logger);
    }

    static Map<String, String> sign(Context context, String userId,
                                    String path, Map<String, String> requestParams,
                                    Logger logger) {
        return sign(context, userId, "", path, requestParams, logger);
    }

    static Map<String, String> sign(Context context, String userId, String pkey,
                                    String path, Map<String, String> requestParams,
                                    Logger logger) {
        return sign(context, userId, pkey, "", path, requestParams, logger);
    }

    static Map<String, String> sign(Context context, String userId, String pkey,
                                    String xhhToken, String path,
                                    Map<String, String> requestParams, Logger logger) {
        return sign(context, userId, pkey, xhhToken, path, requestParams, false, logger);
    }

    static Map<String, String> sign(Context context, String userId, String pkey,
                                    String xhhToken, String path,
                                    Map<String, String> requestParams,
                                    boolean forceFallback, Logger logger) {
        Map<String, String> out = new LinkedHashMap<>();
        if (unavailable) return out;
        try {
            Context app = context.getApplicationContext();
            Map<String, String> official = forceFallback ? new LinkedHashMap<>()
                    : OfficialNativeSigner.sign(app, userId, pkey,
                    xhhToken, path, requestParams, message -> log(logger, message));
            boolean hasOfficialShape = hasValue(official, SecureStrings.time())
                    && hasValue(official, SecureStrings.nonce())
                    && official.size() > 5;
            if (hasUsableOfficialParams(official)) {
                log(logger, "native signer using official native params keys="
                        + official.keySet());
                return official;
            }
            if (!official.isEmpty()) {
                log(logger, "native signer official params incomplete keys="
                        + official.keySet());
            } else if (!OfficialNativeSigner.unavailableReason().isEmpty()) {
                log(logger, "native signer official route unavailable reason="
                        + OfficialNativeSigner.unavailableReason());
            }
            log(logger, "native signer stage ensure init begin");
            ensureInitialized(app, logger);
            log(logger, "native signer stage ensure init done");
            Context nativeContext = new OfficialContext(app);
            log(logger, "native signer stage check signature begin");
            logSignatureState(app, nativeContext, logger);
            log(logger, "native signer stage check signature done");
            String time = String.valueOf(System.currentTimeMillis() / 1000L);
            if (userId == null || userId.isEmpty()) userId = "-1";
            String normalizedPath = normalize(path);
            log(logger, "native signer stage get chunk begin");
            String chunk = ShaderManager.getChunkFlag(nativeContext, CHUNK_FLAG);
            log(logger, "native signer chunk empty=" + (chunk == null || chunk.isEmpty()));
            log(logger, "native signer stage get nonce begin");
            String nonce = ShaderManager.getIdxOffset(nativeContext, chunk, time, userId);
            if (nonce == null || nonce.isEmpty()) {
                markUnavailable("native returned empty nonce", logger);
                return out;
            }

            log(logger, "native signer stage set viewport");
            ShaderManager.setViewport(time, nonce);
            log(logger, "native signer stage set gram len");
            ShaderManager.setGramLen(normalizedPath, nonce);
            log(logger, "native signer stage set buf");
            ShaderManager.setBuf(time, nonce);
            log(logger, "native signer stage set dep rel");
            ShaderManager.setDepRel(deviceModel(), nonce);
            log(logger, "native signer stage set d len");
            ShaderManager.setDLen(osVersion(), nonce);
            log(logger, "native signer stage set ptr offset");
            ShaderManager.setPtrOffset(com.max.xiaoheihe.utils.f.B0(), nonce);
            log(logger, "native signer stage encode begin");
            String encodeResult = NDKTools.encode(nativeContext, normalizedPath, time, nonce);
            log(logger, "native signer encode result empty="
                    + (encodeResult == null || encodeResult.isEmpty())
                    + " len=" + (encodeResult == null ? 0 : encodeResult.length())
                    + " kind=" + encodeResultKind(encodeResult));

            log(logger, "native signer stage get key official context");
            String key = ShaderManager.getObjType(nativeContext, nonce, false);
            log(logger, "native signer key empty=" + (key == null || key.isEmpty())
                    + " len=" + (key == null ? 0 : key.length()));
            if (key == null || key.isEmpty()) {
                markUnavailable("native returned empty key", logger);
                return out;
            }
            out.putAll(baseOfficialParams(userId, time));
            out.put(SecureStrings.time(), time);
            out.put(SecureStrings.nonce(), nonce);
            out.put(SecureStrings.hkey(), key);
            log(logger, "native signer stage rnd check");
            if (ShaderManager.f() && ShaderManager.g() != null) {
                log(logger, "native signer stage get rnd official context");
                String rndKey = ShaderManager.getObjType(nativeContext, nonce, true);
                log(logger, "native signer rnd empty=" + (rndKey == null || rndKey.isEmpty())
                        + " len=" + (rndKey == null ? 0 : rndKey.length()));
                if (rndKey != null && !rndKey.isEmpty()) {
                    out.put(SecureStrings.rndParam(), ShaderManager.g() + ":" + rndKey);
                }
            }
            if (hasOfficialShape) {
                Map<String, String> merged = new LinkedHashMap<>(official);
                merged.put(SecureStrings.time(), time);
                merged.put(SecureStrings.nonce(), nonce);
                merged.put(SecureStrings.hkey(), key);
                if (out.containsKey(SecureStrings.rndParam())) {
                    merged.put(SecureStrings.rndParam(), out.get(SecureStrings.rndParam()));
                }
                log(logger, "native signer filled official params with fallback hkey keys="
                        + merged.keySet());
                return merged;
            }
            log(logger, "native signer ok path=" + path
                    + " keys=" + out.keySet()
                    + " rnd=" + out.containsKey(SecureStrings.rndParam()));
        } catch (Throwable error) {
            markUnavailable(error.getClass().getSimpleName() + ": "
                    + safe(error.getMessage()), logger);
        }
        return out;
    }

    private static Map<String, String> baseOfficialParams(String userId, String time) {
        Map<String, String> out = new LinkedHashMap<>();
        String id = userId == null || userId.isEmpty() ? "-1" : userId;
        out.put(SecureStrings.heyboxId(), id);
        out.put("imei", androidId());
        out.put("device_info", deviceModel());
        out.put("os_type", "Android");
        out.put("x_os_type", "Android");
        out.put("x_client_type", "mobile");
        out.put("os_version", osVersion());
        out.put("version", com.max.xiaoheihe.utils.f.B0());
        out.put("build", com.max.xiaoheihe.utils.f.buildCode());
        out.put(SecureStrings.time(), time);
        out.put("dw", com.max.xiaoheihe.utils.i.e());
        out.put("channel", "heybox_oppo");
        out.put("x_app", "heybox");
        out.put("time_zone", TimeZone.getDefault().getID());
        return out;
    }

    private static boolean hasUsableOfficialParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) return false;
        boolean hasTime = hasValue(params, SecureStrings.time());
        boolean hasNonce = hasValue(params, SecureStrings.nonce());
        boolean hasHkey = hasValue(params, SecureStrings.hkey());
        boolean hasKey = hasValue(params, SecureStrings.keyParam());
        return hasTime && hasNonce && (hasHkey || hasKey);
    }

    private static boolean hasValue(Map<String, String> params, String key) {
        String value = params == null || key == null ? null : params.get(key);
        return value != null && !value.isEmpty();
    }

    static boolean loadRndConfig(JSONObject body, Logger logger) {
        return loadRndConfig(null, body, logger);
    }

    static boolean loadRndConfig(Context context, JSONObject body, Logger logger) {
        if (body == null || unavailable) return false;
        JSONObject result = body.optJSONObject("result");
        if (result == null) return false;
        return loadRndConfig(context, result.optString("code"),
                result.optInt("version", -1), logger);
    }

    static boolean loadRndConfig(Context context, String code, int version, Logger logger) {
        String clean = code == null ? "" : code.trim();
        if (clean.isEmpty() || version < 0 || unavailable) return false;
        try {
            if (context != null) ShaderManager.load(context.getApplicationContext());
            byte[] decoded = Base64.decode(clean, Base64.DEFAULT);
            if (decoded == null || decoded.length == 0) return false;
            ShaderManager.setHdrField(decoded);
            ShaderManager.h(true);
            ShaderManager.i(version);
            log(logger, "native rnd config loaded version=" + version
                    + " bytes=" + decoded.length);
            return true;
        } catch (Throwable error) {
            log(logger, "native rnd config ignored: "
                    + error.getClass().getSimpleName() + ": " + safe(error.getMessage()));
            return false;
        }
    }

    static String unavailableReason() {
        return unavailableReason;
    }

    static Map<String, String> officialParams(SessionStore session) {
        Map<String, String> result = session.hasSignInCredentials()
                ? session.signInOfficialMobileParams(true)
                : session.officialMobileParams(true);
        result.put("time_zone", TimeZone.getDefault().getID());
        return result;
    }

    private static void ensureInitialized(Context context, Logger logger) {
        if (initialized) return;
        log(logger, "native signer init load shader begin");
        ShaderManager.load(context);
        log(logger, "native signer init load shader done");
        log(logger, "native signer init load ndk begin");
        NDKTools.load(context);
        log(logger, "native signer init load ndk done");
        log(logger, "native signer init set parse depth");
        ShaderManager.setParseDepth(context.getCacheDir().getAbsolutePath(), true);
        log(logger, "native signer init set sec level");
        ShaderManager.setSecLevel();
        log(logger, "native signer init set blend mode");
        ShaderManager.setBlendMode(BLEND_MODE, String.valueOf(System.currentTimeMillis()));
        initialized = true;
        log(logger, "native signer initialized sdk=" + Build.VERSION.SDK_INT);
    }

    private static void logSignatureState(Context app, Context nativeContext, Logger logger) {
        try {
            log(logger, "native signer check app=" + NDKTools.checkSignature(app)
                    + " officialContext=" + NDKTools.checkSignature(nativeContext));
        } catch (Throwable error) {
            log(logger, "native signer check ignored: "
                    + error.getClass().getSimpleName() + ": " + safe(error.getMessage()));
        }
    }

    private static String normalize(String path) {
        String value = path == null ? "" : path.trim();
        if (!value.startsWith("/")) value = "/" + value;
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "/";
    }

    private static String deviceModel() {
        return Build.MODEL == null ? "" : Build.MODEL.trim();
    }

    private static String androidId() {
        try {
            return com.max.xiaoheihe.utils.f.Y();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String osVersion() {
        return Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE.trim();
    }

    private static boolean usableEncodeKey(String value) {
        if (value == null) return false;
        String clean = value.trim();
        if (clean.length() < 5 || clean.length() > 64) return false;
        String lower = clean.toLowerCase(java.util.Locale.US);
        if ("success".equals(lower) || "failed".equals(lower)
                || "failure".equals(lower) || "error".equals(lower)
                || "ok".equals(lower)) {
            return false;
        }
        for (int i = 0; i < clean.length(); i++) {
            char ch = clean.charAt(i);
            boolean allowed = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || ch == '_' || ch == '-';
            if (!allowed) return false;
        }
        return true;
    }

    private static String encodeResultKind(String value) {
        if (value == null || value.isEmpty()) return "empty";
        String lower = value.trim().toLowerCase(java.util.Locale.US);
        if ("success".equals(lower) || "ok".equals(lower)) return lower;
        if ("failed".equals(lower) || "failure".equals(lower)
                || "error".equals(lower)) return lower;
        return "opaque";
    }

    private static void markUnavailable(String reason, Logger logger) {
        unavailable = true;
        unavailableReason = reason == null ? "" : reason;
        log(logger, "native signer unavailable: " + unavailableReason);
    }

    private static void log(Logger logger, String message) {
        if (logger != null) logger.log(message);
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 160 ? clean.substring(0, 160) : clean;
    }
}
