package com.ronan.heyboxlite;

import android.content.Context;
import android.content.pm.PackageInfo;

import com.graphice.shaderar.ShaderManager;
import com.max.xiaoheihe.utils.NDKTools;
import com.nmmedit.protect.NativeUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;

import okhttp3.t;

final class OfficialNativeSigner {
    interface Logger {
        void log(String message);
    }

    private static boolean initialized;
    private static boolean unavailable;
    private static String unavailableReason = "";
    private static final String BLEND_MODE =
            "MFANEHAMGACOBHIEMIHIJLKJPMMHJMMLABCNGBPPENCENPOM";

    private OfficialNativeSigner() {}

    static Map<String, String> sign(Context context, String userId, String path,
                                    Map<String, String> requestParams, Logger logger) {
        return sign(context, userId, "", path, requestParams, logger);
    }

    static Map<String, String> sign(Context context, String userId, String pkey, String path,
                                    Map<String, String> requestParams, Logger logger) {
        return sign(context, userId, pkey, "", path, requestParams, logger);
    }

    static Map<String, String> sign(Context context, String userId, String pkey,
                                    String xhhToken, String path,
                                    Map<String, String> requestParams, Logger logger) {
        Map<String, String> out = new LinkedHashMap<>();
        if (unavailable || context == null) return out;
        try {
            Context app = context.getApplicationContext();
            if (LocalCache.isOfficialNativeDisabled(app)) {
                log(logger, "official native signer skipped disabled reason="
                        + LocalCache.officialNativeDisabledReason(app));
                return out;
            }
            Context officialBase = officialBaseContext(app, logger);
            configureOfficialAppInfo(officialBase, logger);
            Context officialContext = new OfficialContext(officialBase);
            com.max.xiaoheihe.utils.f.initDeviceId(deviceIdFrom(requestParams));
            com.max.xiaoheihe.utils.f.initAppInfo(
                    OfficialContext.officialVersionName(),
                    OfficialContext.officialBuildCode());
            com.max.xiaoheihe.app.HeyBoxApplication.init(officialContext);
            com.max.xiaoheihe.utils.m0.init(userId, pkey);
            com.max.xiaoheihe.utils.i.init(xhhToken);
            log(logger, "official native signer login state userId="
                    + (userId == null || userId.isEmpty() ? 0 : userId.length())
                    + " pkeyLen=" + (pkey == null ? 0 : pkey.length())
                    + " tokenLen=" + (xhhToken == null ? 0 : xhhToken.length()));
            ensureInitialized(app, officialBase, officialContext, logger);
            String cleanPath = normalizeInterceptorPath(path);
            t.a builder = new t.a()
                    .M("https")
                    .x("api.xiaoheihe.cn")
                    .l(cleanPath);
            seedQuery(builder, cleanPath, requestParams);
            String nativePath = nativeInterceptorPath(cleanPath);
            log(logger, "official native signer stage invoke path=" + nativePath
                    + " requestKeys=" + (requestParams == null
                    ? java.util.Collections.emptySet() : requestParams.keySet())
                    + " preKeys=" + builder.queryMap().keySet());
            new com.max.xiaoheihe.router.serviceimpl.k().b(builder, nativePath);
            Map<String, String> params = builder.queryMap();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null && !key.isEmpty()) out.put(key, value);
            }
            String nativeUrl = buildUrl(cleanPath, out);
            if (nativeUrl != null && !nativeUrl.isEmpty()) {
                out.put(NativeSignService.EXTRA_NATIVE_URL, nativeUrl);
            }
            log(logger, "official native signer result keys=" + out.keySet());
            log(logger, "official native signer param summary=" + paramSummary(out));
            log(logger, "official native signer url override=" + (nativeUrl != null
                    && !nativeUrl.isEmpty())
                    + " urlLen=" + (nativeUrl == null ? 0 : nativeUrl.length()));
            log(logger, "official native signer url query summary="
                    + querySummary(nativeUrl));
            log(logger, "official native signer value lens hkey="
                    + len(out.get(SecureStrings.hkey()))
                    + " nonce=" + len(out.get(SecureStrings.nonce()))
                    + " time=" + len(out.get(SecureStrings.time())));
        } catch (Throwable error) {
            log(logger, "official native signer throwable=" + stack(error));
            markUnavailable(error.getClass().getSimpleName() + ": "
                    + safe(error.getMessage()), logger);
        }
        return out;
    }

    static String unavailableReason() {
        return unavailableReason;
    }

    private static Context officialBaseContext(Context app, Logger logger) {
        try {
            if (!OfficialAppVerifier.isOfficialPackageTrusted(app)) {
                log(logger, "official native signer installed official context untrusted");
                return app;
            }
            Context official = app.createPackageContext(OfficialContext.PACKAGE_NAME,
                    Context.CONTEXT_IGNORE_SECURITY);
            log(logger, "official native signer using installed official context");
            return official;
        } catch (Throwable error) {
            log(logger, "official native signer installed context unavailable: "
                    + error.getClass().getSimpleName());
            return app;
        }
    }

    private static void configureOfficialAppInfo(Context officialBase, Logger logger) {
        try {
            if (!OfficialAppVerifier.isOfficialPackageTrusted(officialBase)) {
                log(logger, "official native signer app info skipped untrusted package");
                return;
            }
            PackageInfo info = officialBase.getPackageManager()
                    .getPackageInfo(OfficialContext.PACKAGE_NAME, 0);
            String version = info == null ? "" : info.versionName;
            int build = info == null ? 0 : info.versionCode;
            OfficialContext.configureAppInfo(version, build);
            log(logger, "official native signer app info version="
                    + OfficialContext.officialVersionName()
                    + " build=" + OfficialContext.officialBuildCode());
        } catch (Throwable error) {
            log(logger, "official native signer app info fallback: "
                    + error.getClass().getSimpleName());
        }
    }

    private static String deviceIdFrom(Map<String, String> requestParams) {
        if (requestParams == null) return "";
        String value = requestParams.get(SecureStrings.deviceId());
        if (value == null || value.trim().isEmpty()) value = requestParams.get("imei");
        return value == null ? "" : value.trim();
    }

    private static void seedQuery(t.a builder, String path, Map<String, String> requestParams) {
        if (builder == null || requestParams == null || requestParams.isEmpty()) return;
        if (isTaskPath(path)) return;
        for (Map.Entry<String, String> entry : requestParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || value == null) continue;
            if (isSecurityParam(key)) continue;
            builder.g(key, value);
        }
    }

    private static boolean isTaskPath(String path) {
        return path != null && path.contains("/task/");
    }

    private static boolean isSecurityParam(String key) {
        return SecureStrings.hkey().equals(key)
                || SecureStrings.keyParam().equals(key)
                || SecureStrings.nonce().equals(key)
                || SecureStrings.time().equals(key)
                || SecureStrings.rndParam().equals(key);
    }

    private static String buildUrl(String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder("https://api.xiaoheihe.cn");
        url.append(path == null || path.isEmpty() ? "/" : path);
        if (params == null || params.isEmpty()) return url.toString();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || value == null
                    || NativeSignService.EXTRA_NATIVE_URL.equals(key)) continue;
            url.append(first ? '?' : '&');
            first = false;
            url.append(encode(key)).append('=').append(encode(value));
        }
        return url.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Throwable ignored) {
            return value == null ? "" : value;
        }
    }

    private static void ensureInitialized(Context loadContext, Context officialBase,
                                          Context signContext, Logger logger) {
        if (initialized) return;
        log(logger, "official native signer load shader begin");
        ShaderManager.load(signContext);
        log(logger, "official native signer load shader done");
        log(logger, "official native signer load ndk begin");
        NDKTools.load(signContext);
        log(logger, "official native signer load ndk done");
        try {
            log(logger, "official native signer check signature app="
                    + NDKTools.checkSignature(loadContext)
                    + " officialBase=" + NDKTools.checkSignature(officialBase)
                    + " wrapped=" + NDKTools.checkSignature(signContext));
        } catch (Throwable error) {
            log(logger, "official native signer check signature error="
                    + error.getClass().getSimpleName() + ": " + safe(error.getMessage()));
        }
        log(logger, "official native signer set parse depth begin");
        ShaderManager.setParseDepth(loadContext.getCacheDir().getAbsolutePath(), true);
        log(logger, "official native signer set parse depth done");
        log(logger, "official native signer set sec level begin");
        ShaderManager.setSecLevel();
        log(logger, "official native signer set sec level done");
        log(logger, "official native signer set blend mode begin");
        ShaderManager.setBlendMode(BLEND_MODE, String.valueOf(System.currentTimeMillis()));
        log(logger, "official native signer set blend mode done");
        log(logger, "official native signer load ailab begin");
        NativeUtil.load(signContext);
        log(logger, "official native signer load ailab done");
        log(logger, "official native signer classes16 init begin");
        NativeUtil.classes16Init0(0);
        initialized = true;
        log(logger, "official native signer classes16 init done");
    }

    private static String normalizeInterceptorPath(String path) {
        String value = path == null ? "" : path.trim();
        if (value.isEmpty()) return "/";
        if (!value.startsWith("/")) value = "/" + value;
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String nativeInterceptorPath(String path) {
        if ("/bbs/app/feeds".equals(path)
                || "/bbs/app/topic/feeds".equals(path)
                || "/bbs/app/waterfall/feeds".equals(path)) {
            return path + "/";
        }
        return path;
    }

    private static void markUnavailable(String reason, Logger logger) {
        unavailable = true;
        unavailableReason = reason == null ? "" : reason;
        log(logger, "official native signer unavailable: " + unavailableReason);
    }

    private static void log(Logger logger, String message) {
        if (logger != null) logger.log(message);
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 160 ? clean.substring(0, 160) : clean;
    }

    private static int len(String value) {
        return value == null ? 0 : value.length();
    }

    private static String paramSummary(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "empty";
        StringBuilder out = new StringBuilder();
        appendSummary(out, params, SecureStrings.heyboxId(), false);
        appendSummary(out, params, "imei", false);
        appendSummary(out, params, "device_info", true);
        appendSummary(out, params, "os_type", true);
        appendSummary(out, params, "os_version", true);
        appendSummary(out, params, "version", true);
        appendSummary(out, params, "build", true);
        appendSummary(out, params, "channel", true);
        appendSummary(out, params, "x_app", true);
        appendSummary(out, params, "x_client_type", true);
        appendSummary(out, params, "time_zone", true);
        appendSummary(out, params, "dw", false);
        appendSummary(out, params, SecureStrings.hkey(), false);
        appendSummary(out, params, SecureStrings.nonce(), false);
        appendSummary(out, params, SecureStrings.time(), false);
        return out.toString();
    }

    private static String querySummary(String url) {
        if (url == null || url.isEmpty()) return "empty";
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart + 1 >= url.length()) return "empty";
        int fragmentStart = url.indexOf('#', queryStart + 1);
        String query = fragmentStart >= 0
                ? url.substring(queryStart + 1, fragmentStart)
                : url.substring(queryStart + 1);
        if (query.isEmpty()) return "empty";
        StringBuilder out = new StringBuilder();
        String[] parts = query.split("&");
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            int equals = part.indexOf('=');
            String key = equals >= 0 ? part.substring(0, equals) : part;
            String value = equals >= 0 ? part.substring(equals + 1) : "";
            if (out.length() > 0) out.append(',');
            out.append(safe(key)).append("=len").append(value.length());
        }
        return out.length() == 0 ? "empty" : out.toString();
    }

    private static void appendSummary(StringBuilder out, Map<String, String> params,
                                      String key, boolean showValue) {
        String value = params.get(key);
        if (out.length() > 0) out.append(',');
        out.append(key).append('=');
        if (value == null) {
            out.append("null");
        } else if (showValue) {
            out.append(safe(value));
        } else {
            out.append("len").append(value.length());
        }
    }

    private static String stack(Throwable error) {
        if (error == null) return "";
        try {
            StringWriter out = new StringWriter();
            PrintWriter writer = new PrintWriter(out);
            error.printStackTrace(writer);
            writer.flush();
            String value = out.toString().replace('\r', ' ').trim();
            return value.length() > 3000 ? value.substring(0, 3000) : value;
        } catch (Throwable ignored) {
            return error.toString();
        }
    }

}
