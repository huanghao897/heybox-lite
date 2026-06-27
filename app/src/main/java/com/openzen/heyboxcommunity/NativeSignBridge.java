package com.openzen.heyboxcommunity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class NativeSignBridge {
    interface Logger {
        void log(String message);
    }

    private static final long TIMEOUT_MS = 1600L;

    private NativeSignBridge() {}

    static Map<String, String> sign(Context context, SessionStore session,
                                    String path, Logger logger) {
        return sign(context, session, path, null, logger);
    }

    static Map<String, String> sign(Context context, SessionStore session,
                                    String path, Map<String, String> requestParams,
                                    Logger logger) {
        return sign(context, session, path, requestParams, false, logger);
    }

    static Map<String, String> sign(Context context, SessionStore session,
                                    String path, Map<String, String> requestParams,
                                    boolean forceFallback, Logger logger) {
        Map<String, String> empty = new LinkedHashMap<>();
        if (context == null || session == null) return empty;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            log(logger, "remote native signer skipped on main thread");
            return empty;
        }
        log(logger, "remote native signer send path=" + path
                + " requestKeys=" + (requestParams == null
                ? java.util.Collections.emptySet() : requestParams.keySet())
                + " forceFallback=" + forceFallback);
        Context app = context.getApplicationContext();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, String>> result = new AtomicReference<>(empty);
        AtomicBoolean bound = new AtomicBoolean(false);
        AtomicBoolean receivedResult = new AtomicBoolean(false);

        Messenger reply = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override public void handleMessage(Message msg) {
                if (msg.what == NativeSignService.MSG_RESULT) {
                    receivedResult.set(true);
                    result.set(readResult(msg.getData(), logger));
                    latch.countDown();
                    return;
                }
                super.handleMessage(msg);
            }
        });

        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                bound.set(true);
                try {
                    Messenger remote = new Messenger(service);
                    Message message = Message.obtain(null, NativeSignService.MSG_SIGN);
                    Bundle data = new Bundle();
                    String signUserId = session.signInUserId().isEmpty()
                            ? session.userId() : session.signInUserId();
                    String signPkey = session.signInPkey().isEmpty()
                            ? session.officialPkey() : session.signInPkey();
                    String signToken = session.signInXhhToken().isEmpty()
                            ? session.officialXhhToken() : session.signInXhhToken();
                    data.putString(NativeSignService.EXTRA_PATH, path);
                    data.putString(NativeSignService.EXTRA_USER_ID, signUserId);
                    data.putString(NativeSignService.EXTRA_PKEY, signPkey);
                    data.putString(NativeSignService.EXTRA_XHH_TOKEN, signToken);
                    data.putBoolean(NativeSignService.EXTRA_FORCE_FALLBACK, forceFallback);
                    data.putString(NativeSignService.EXTRA_RND_CODE, session.nativeRndCode());
                    data.putInt(NativeSignService.EXTRA_RND_VERSION,
                            session.nativeRndVersion());
                    data.putBundle(NativeSignService.EXTRA_REQUEST_PARAMS,
                            toBundle(requestParams));
                    data.putStringArrayList(NativeSignService.EXTRA_REQUEST_PARAM_KEYS,
                            orderedKeys(requestParams));
                    message.setData(data);
                    message.replyTo = reply;
                    remote.send(message);
                } catch (RemoteException error) {
                    log(logger, "remote native signer send failed: "
                            + error.getClass().getSimpleName());
                    latch.countDown();
                }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                log(logger, "remote native signer disconnected receivedResult="
                        + receivedResult.get());
                if (!receivedResult.get()) {
                    LocalCache.disableOfficialNative(app, "remote native signer disconnected");
                }
                latch.countDown();
            }
        };

        try {
            Intent intent = new Intent(app, NativeSignService.class);
            boolean ok = app.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!ok) {
                log(logger, "remote native signer bind failed");
                return empty;
            }
            bound.set(true);
            boolean completed = latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!completed) log(logger, "remote native signer timeout");
            return result.get() == null ? empty : result.get();
        } catch (Throwable error) {
            log(logger, "remote native signer failed: "
                    + error.getClass().getSimpleName() + ": " + safe(error.getMessage()));
            return empty;
        } finally {
            if (bound.get()) {
                try {
                    app.unbindService(connection);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Map<String, String> readResult(Bundle data, Logger logger) {
        Map<String, String> out = new LinkedHashMap<>();
        if (data == null) return out;
        data.setClassLoader(NativeSignBridge.class.getClassLoader());
        ArrayList<String> logs = data.getStringArrayList(NativeSignService.EXTRA_LOGS);
        if (logs != null) {
            for (String line : logs) log(logger, line);
        }
        ArrayList<String> orderedKeys = data.getStringArrayList(NativeSignService.EXTRA_PARAM_KEYS);
        if (orderedKeys != null && !orderedKeys.isEmpty()) {
            for (String key : orderedKeys) copy(data, out, key);
        } else {
            copy(data, out, SecureStrings.time());
            copy(data, out, SecureStrings.nonce());
            copy(data, out, SecureStrings.hkey());
            copy(data, out, SecureStrings.keyParam());
            copy(data, out, SecureStrings.rndParam());
            for (String key : data.keySet()) {
                if (key == null
                        || NativeSignService.EXTRA_LOGS.equals(key)
                        || NativeSignService.EXTRA_PARAM_KEYS.equals(key)
                        || NativeSignService.EXTRA_NATIVE_URL.equals(key)
                        || NativeSignService.EXTRA_HKEY_ALIAS.equals(key)) continue;
                copy(data, out, key);
            }
        }
        String nativeUrl = valueOf(data, NativeSignService.EXTRA_NATIVE_URL);
        if (nativeUrl != null && !nativeUrl.isEmpty()) {
            out.put(NativeSignService.EXTRA_NATIVE_URL, nativeUrl);
            log(logger, "remote native signer url override len=" + nativeUrl.length());
        }
        if (!out.containsKey(SecureStrings.hkey())) {
            String alias = valueOf(data, NativeSignService.EXTRA_HKEY_ALIAS);
            if (alias != null && !alias.isEmpty()) {
                out.put(SecureStrings.hkey(), alias);
                log(logger, "remote native signer restored hkey from alias len="
                        + alias.length());
            } else {
                log(logger, "remote native signer hkey missing aliasLen="
                        + lengthOf(data, NativeSignService.EXTRA_HKEY_ALIAS));
            }
        } else {
            log(logger, "remote native signer hkey len="
                    + safeLength(out.get(SecureStrings.hkey())));
        }
        log(logger, "remote native signer result keys=" + out.keySet());
        return out;
    }

    private static Bundle toBundle(Map<String, String> params) {
        Bundle out = new Bundle();
        if (params == null) return out;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && value != null) out.putString(key, value);
        }
        return out;
    }

    private static ArrayList<String> orderedKeys(Map<String, String> params) {
        ArrayList<String> keys = new ArrayList<>();
        if (params == null) return keys;
        for (String key : params.keySet()) {
            if (key != null) keys.add(key);
        }
        return keys;
    }

    private static void log(Logger logger, String message) {
        if (logger != null) logger.log(message);
    }

    private static void copy(Bundle data, Map<String, String> out, String key) {
        String value = valueOf(data, key);
        if (value != null && !value.isEmpty()) out.put(key, value);
    }

    private static String valueOf(Bundle data, String key) {
        if (data == null || key == null) return null;
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        if (value instanceof CharSequence) return value.toString();
        return null;
    }

    private static int lengthOf(Bundle data, String key) {
        String value = valueOf(data, key);
        return value == null ? 0 : value.length();
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 140 ? clean.substring(0, 140) : clean;
    }
}
