package com.ronan.heyboxlite;

import android.os.Build;
import android.util.DisplayMetrics;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

final class CheckinCredentialPayload {
    static final class InvalidCredentials extends Exception {
        InvalidCredentials(String message) {
            super(message);
        }
    }

    final JSONObject json;
    final String fingerprint;

    private CheckinCredentialPayload(JSONObject json, String fingerprint) {
        this.json = json;
        this.fingerprint = fingerprint;
    }

    static CheckinCredentialPayload fromSession(SessionStore session)
            throws InvalidCredentials {
        if (session == null || !session.isLoggedIn()) {
            throw new InvalidCredentials("请先登录小黑盒账号");
        }
        Map<String, String> mobile = session.officialMobileParams(true);
        String deviceId = first(mobile.get("device_id"), mobile.get("imei"));
        String deviceInfo = first(mobile.get("device_info"), Build.MODEL, "Android");
        String osVersion = first(mobile.get("os_version"), Build.VERSION.RELEASE,
                String.valueOf(Build.VERSION.SDK_INT));
        String channel = first(mobile.get("channel"), "heybox");
        String screenWidth = com.max.xiaoheihe.utils.i.e();
        if (screenWidth.isEmpty() || "0".equals(screenWidth)) {
            DisplayMetrics metrics = session.appContext().getResources().getDisplayMetrics();
            float density = metrics.density <= 0f ? 1f : metrics.density;
            screenWidth = String.valueOf(Math.max(1, Math.round(metrics.widthPixels / density)));
        }
        return create(session.userId(), session.officialPkey(), session.officialXhhToken(),
                session.getCookie(), deviceId, deviceInfo, osVersion, channel, screenWidth,
                TimeZone.getDefault().getID(), BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE);
    }

    static CheckinCredentialPayload create(String heyboxId, String pkey, String token,
                                            String cookie, String deviceId, String deviceInfo,
                                            String osVersion, String channel,
                                            String screenWidthDp, String timeZone,
                                            String appVersion, int appVersionCode)
            throws InvalidCredentials {
        String cleanId = required(heyboxId, "小黑盒账号信息不完整");
        String cleanPkey = required(pkey, "当前登录缺少 pkey，请重新登录小黑盒");
        String cleanDeviceId = required(deviceId, "当前设备标识不可用");
        if (!cleanId.matches("[0-9]+")) {
            throw new InvalidCredentials("小黑盒账号信息无效");
        }
        try {
            JSONObject credentials = new JSONObject();
            credentials.put("heybox_id", cleanId);
            credentials.put("pkey", cleanPkey);
            credentials.put("x_xhh_tokenid", optional(token));
            credentials.put("cookie", optional(cookie));

            JSONObject device = new JSONObject();
            device.put("device_id", cleanDeviceId);
            device.put("device_info", required(deviceInfo, "当前设备信息不可用"));
            device.put("os_version", required(osVersion, "当前系统版本不可用"));
            device.put("channel", required(channel, "当前渠道信息不可用"));
            device.put("screen_width_dp", required(screenWidthDp, "当前屏幕信息不可用"));
            device.put("time_zone", required(timeZone, "当前时区信息不可用"));

            JSONObject app = new JSONObject();
            app.put("version", required(appVersion, "当前应用版本不可用"));
            app.put("version_code", appVersionCode);

            JSONObject root = new JSONObject();
            root.put("schema", 1);
            root.put("credentials", credentials);
            root.put("device", device);
            root.put("app", app);
            return new CheckinCredentialPayload(root, fingerprint(root));
        } catch (JSONException error) {
            throw new InvalidCredentials("签到资料无法生成");
        }
    }

    private static String fingerprint(JSONObject value) throws InvalidCredentials {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes("UTF-8"));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.US, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception error) {
            throw new InvalidCredentials("签到资料无法校验");
        }
    }

    private static String required(String value, String message) throws InvalidCredentials {
        String result = optional(value);
        if (result.isEmpty()) throw new InvalidCredentials(message);
        return result;
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String first(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String clean = optional(value);
            if (!clean.isEmpty()) return clean;
        }
        return "";
    }
}
