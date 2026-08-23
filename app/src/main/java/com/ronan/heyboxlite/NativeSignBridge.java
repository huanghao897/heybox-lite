package com.ronan.heyboxlite;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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
import java.util.concurrent.atomic.AtomicReference;

final class NativeSignBridge {
    interface Logger {
        void log(String message);
    }

    private static final long BIND_TIMEOUT_MS = 2_000L;
    private static final long RESULT_TIMEOUT_MS = 6_000L;
    private static final Object CONNECTION_LOCK = new Object();
    private static final HandlerThread REPLY_THREAD = replyThread();
    private static Connection connection;

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
        Connection active = connection(context.getApplicationContext());
        Messenger remote = active.awaitRemote(logger);
        if (remote == null) return empty;
        Map<String, String> result = send(remote, session, path, requestParams,
                forceFallback, logger);
        if (result != null) return result;

        active.invalidate(remote);
        remote = active.awaitRemote(logger);
        if (remote == null) return empty;
        result = send(remote, session, path, requestParams, forceFallback, logger);
        return result == null ? empty : result;
    }

    static void close(Context context) {
        synchronized (CONNECTION_LOCK) {
            if (connection == null) return;
            connection.close();
            connection = null;
        }
    }

    private static Map<String, String> send(Messenger remote, SessionStore session,
                                            String path, Map<String, String> requestParams,
                                            boolean forceFallback, Logger logger) {
        CountDownLatch resultReady = new CountDownLatch(1);
        AtomicReference<Map<String, String>> result = new AtomicReference<>();
        Messenger reply = new Messenger(new Handler(REPLY_THREAD.getLooper()) {
            @Override public void handleMessage(Message message) {
                if (message.what == NativeSignService.MSG_RESULT) {
                    result.set(readResult(message.getData(), logger));
                    resultReady.countDown();
                    return;
                }
                super.handleMessage(message);
            }
        });
        try {
            Message message = Message.obtain(null, NativeSignService.MSG_SIGN);
            message.setData(request(session, path, requestParams, forceFallback));
            message.replyTo = reply;
            remote.send(message);
            if (!resultReady.await(RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log(logger, "remote native signer result timeout");
                return new LinkedHashMap<>();
            }
            Map<String, String> value = result.get();
            return value == null ? new LinkedHashMap<>() : value;
        } catch (RemoteException error) {
            log(logger, "remote native signer send failed: "
                    + error.getClass().getSimpleName());
            return null;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new LinkedHashMap<>();
        }
    }

    private static Bundle request(SessionStore session, String path,
                                  Map<String, String> requestParams,
                                  boolean forceFallback) {
        Bundle data = new Bundle();
        data.putString(NativeSignService.EXTRA_PATH, path);
        data.putString(NativeSignService.EXTRA_USER_ID, session.userId());
        data.putString(NativeSignService.EXTRA_PKEY, session.officialPkey());
        data.putString(NativeSignService.EXTRA_XHH_TOKEN, session.officialXhhToken());
        data.putBoolean(NativeSignService.EXTRA_FORCE_FALLBACK, forceFallback);
        data.putString(NativeSignService.EXTRA_RND_CODE, session.nativeRndCode());
        data.putInt(NativeSignService.EXTRA_RND_VERSION, session.nativeRndVersion());
        data.putBundle(NativeSignService.EXTRA_REQUEST_PARAMS, toBundle(requestParams));
        data.putStringArrayList(NativeSignService.EXTRA_REQUEST_PARAM_KEYS,
                orderedKeys(requestParams));
        return data;
    }

    private static Connection connection(Context app) {
        synchronized (CONNECTION_LOCK) {
            if (connection == null) connection = new Connection(app);
            return connection;
        }
    }

    private static Map<String, String> readResult(Bundle data, Logger logger) {
        Map<String, String> out = new LinkedHashMap<>();
        if (data == null) return out;
        data.setClassLoader(NativeSignBridge.class.getClassLoader());
        ArrayList<String> logs = data.getStringArrayList(NativeSignService.EXTRA_LOGS);
        if (logs != null) for (String line : logs) log(logger, line);
        ArrayList<String> keys = data.getStringArrayList(NativeSignService.EXTRA_PARAM_KEYS);
        if (keys != null) for (String key : keys) copy(data, out, key);
        copy(data, out, NativeSignService.EXTRA_NATIVE_URL);
        if (!out.containsKey(SecureStrings.hkey())) {
            String alias = valueOf(data, NativeSignService.EXTRA_HKEY_ALIAS);
            if (alias != null && !alias.isEmpty()) out.put(SecureStrings.hkey(), alias);
        }
        log(logger, "remote native signer result keys=" + out.keySet());
        return out;
    }

    private static Bundle toBundle(Map<String, String> params) {
        Bundle out = new Bundle();
        if (params == null) return out;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                out.putString(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static ArrayList<String> orderedKeys(Map<String, String> params) {
        ArrayList<String> keys = new ArrayList<>();
        if (params == null) return keys;
        for (String key : params.keySet()) if (key != null) keys.add(key);
        return keys;
    }

    private static void copy(Bundle data, Map<String, String> out, String key) {
        String value = valueOf(data, key);
        if (value != null && !value.isEmpty()) out.put(key, value);
    }

    private static String valueOf(Bundle data, String key) {
        Object value = data == null || key == null ? null : data.get(key);
        return value instanceof CharSequence ? value.toString() : null;
    }

    private static void log(Logger logger, String message) {
        if (logger != null) logger.log(message);
    }

    private static HandlerThread replyThread() {
        HandlerThread thread = new HandlerThread("heybox-native-replies");
        thread.start();
        return thread;
    }

    private static final class Connection implements ServiceConnection {
        private final Context app;
        private Messenger remote;
        private CountDownLatch connected = new CountDownLatch(1);
        private boolean binding;
        private boolean bound;

        Connection(Context app) {
            this.app = app;
        }

        Messenger awaitRemote(Logger logger) {
            CountDownLatch latch;
            synchronized (this) {
                if (remote != null) return remote;
                if (!binding) bind(logger);
                latch = connected;
            }
            try {
                if (!latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    log(logger, "remote native signer bind timeout");
                    return null;
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return null;
            }
            synchronized (this) {
                return remote;
            }
        }

        synchronized void invalidate(Messenger value) {
            if (remote != value) return;
            remote = null;
            binding = false;
            connected = new CountDownLatch(1);
        }

        private void bind(Logger logger) {
            binding = true;
            connected = new CountDownLatch(1);
            bound = app.bindService(new Intent(app, NativeSignService.class),
                    this, Context.BIND_AUTO_CREATE);
            if (!bound) {
                binding = false;
                connected.countDown();
                log(logger, "remote native signer bind failed");
            }
        }

        @Override public synchronized void onServiceConnected(ComponentName name,
                                                               IBinder service) {
            remote = new Messenger(service);
            binding = false;
            connected.countDown();
        }

        @Override public synchronized void onServiceDisconnected(ComponentName name) {
            remote = null;
            binding = false;
            connected = new CountDownLatch(1);
        }

        @Override public synchronized void onBindingDied(ComponentName name) {
            onServiceDisconnected(name);
        }

        synchronized void close() {
            remote = null;
            binding = false;
            connected.countDown();
            if (bound) {
                app.unbindService(this);
                bound = false;
            }
        }
    }
}
