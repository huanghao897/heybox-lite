package com.ronan.heyboxlite;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class NativeSignService extends Service {
    static final int MSG_SIGN = 1;
    static final int MSG_RESULT = 2;
    static final String EXTRA_PATH = "path";
    static final String EXTRA_USER_ID = "user_id";
    static final String EXTRA_PKEY = "pkey";
    static final String EXTRA_XHH_TOKEN = "xhh_token";
    static final String EXTRA_FORCE_FALLBACK = "force_fallback";
    static final String EXTRA_REQUEST_PARAMS = "request_params";
    static final String EXTRA_REQUEST_PARAM_KEYS = "request_param_keys";
    static final String EXTRA_RND_CODE = "rnd_code";
    static final String EXTRA_RND_VERSION = "rnd_version";
    static final String EXTRA_LOGS = "logs";
    static final String EXTRA_PARAM_KEYS = "param_keys";
    static final String EXTRA_NATIVE_URL = "native_url";
    static final String EXTRA_HKEY_ALIAS = "native_security_value";

    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            if (msg.what == MSG_SIGN) {
                handleSign(msg);
                return;
            }
            super.handleMessage(msg);
        }
    });

    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
        NativeLibraryLoader.init(this);
        LocalCache.appendNativeSignLog(this, "native service created");
    }

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private void handleSign(Message msg) {
        Bundle input = msg.getData();
        String path = input == null ? "" : input.getString(EXTRA_PATH, "");
        String userId = input == null ? "" : input.getString(EXTRA_USER_ID, "");
        String pkey = input == null ? "" : input.getString(EXTRA_PKEY, "");
        String xhhToken = input == null ? "" : input.getString(EXTRA_XHH_TOKEN, "");
        boolean forceFallback = input != null && input.getBoolean(EXTRA_FORCE_FALLBACK, false);
        Map<String, String> requestParams = readParams(input == null
                ? null : input.getBundle(EXTRA_REQUEST_PARAMS),
                input == null ? null : input.getStringArrayList(EXTRA_REQUEST_PARAM_KEYS));
        LocalCache.appendNativeSignLog(this, "native service sign start path=" + path
                + " requestKeys=" + requestParams.keySet()
                + " forceFallback=" + forceFallback);
        ArrayList<String> logs = new ArrayList<>();
        NativeSecuritySigner.Logger logger = message -> {
            logs.add(message);
            LocalCache.appendNativeSignLog(NativeSignService.this, message);
        };
        loadRndConfig(input, logger);
        Map<String, String> params = NativeSecuritySigner.sign(
                getApplicationContext(), userId, pkey, xhhToken, path, requestParams,
                forceFallback, logger);
        String nativeUrl = params.remove(EXTRA_NATIVE_URL);
        LocalCache.appendNativeSignLog(this, "native service sign finish keys=" + params.keySet());
        Bundle output = new Bundle();
        ArrayList<String> orderedKeys = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            orderedKeys.add(entry.getKey());
            output.putString(entry.getKey(), entry.getValue());
        }
        output.putStringArrayList(EXTRA_PARAM_KEYS, orderedKeys);
        String hkey = params.get(SecureStrings.hkey());
        if (hkey != null && !hkey.isEmpty()) {
            output.putString(EXTRA_HKEY_ALIAS, hkey);
            LocalCache.appendNativeSignLog(this,
                    "native service hkey len=" + hkey.length());
        }
        if (nativeUrl != null && !nativeUrl.isEmpty()) {
            output.putString(EXTRA_NATIVE_URL, nativeUrl);
        }
        output.putStringArrayList(EXTRA_LOGS, logs);
        Message result = Message.obtain(null, MSG_RESULT);
        result.setData(output);
        try {
            if (msg.replyTo != null) msg.replyTo.send(result);
        } catch (RemoteException ignored) {
        }
    }

    private void loadRndConfig(Bundle input, NativeSecuritySigner.Logger logger) {
        String code = input == null ? "" : input.getString(EXTRA_RND_CODE, "");
        int version = input == null ? -1 : input.getInt(EXTRA_RND_VERSION, -1);
        if (code == null || code.trim().isEmpty() || version < 0) {
            SessionStore store = new SessionStore(this);
            code = store.nativeRndCode();
            version = store.nativeRndVersion();
        }
        boolean loaded = NativeSecuritySigner.loadRndConfig(
                getApplicationContext(), code, version, logger);
        LocalCache.appendNativeSignLog(this, "native service rnd preload loaded=" + loaded
                + " version=" + version
                + " codeLen=" + (code == null ? 0 : code.length()));
    }

    private static Map<String, String> readParams(Bundle bundle, ArrayList<String> orderedKeys) {
        Map<String, String> out = new LinkedHashMap<>();
        if (bundle == null) return out;
        if (orderedKeys != null && !orderedKeys.isEmpty()) {
            for (String key : orderedKeys) {
                String value = bundle.getString(key);
                if (key != null && value != null) out.put(key, value);
            }
            return out;
        }
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            String value = bundle.getString(key);
            if (key != null && value != null) out.put(key, value);
        }
        return out;
    }
}
