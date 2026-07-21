package com.ronan.heyboxlite;

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class MobileLoginCryptoBridge {
    interface Logger {
        void log(String message);
    }

    static final class Result {
        final String encryptedPhone;
        final String error;

        private Result(String encryptedPhone, String error) {
            this.encryptedPhone = encryptedPhone == null ? "" : encryptedPhone;
            this.error = error == null ? "" : error;
        }

        static Result success(String value) {
            return new Result(value, "");
        }

        static Result failure(String message) {
            return new Result("", message);
        }

        boolean isSuccess() {
            return !encryptedPhone.isEmpty();
        }
    }

    private static final long TIMEOUT_MS = 8000L;

    private MobileLoginCryptoBridge() {}

    static Result encrypt(Context context, String normalizedPhone, Logger logger) {
        if (context == null || normalizedPhone == null || normalizedPhone.isEmpty()) {
            return Result.failure("invalid phone number");
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return Result.failure("phone encryption cannot run on the main thread");
        }
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Result> result = new AtomicReference<>();
        AtomicBoolean bound = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Messenger reply = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override public void handleMessage(Message message) {
                if (message.what != NativeSignService.MSG_ENCRYPT_PHONE_RESULT) {
                    super.handleMessage(message);
                    return;
                }
                Bundle data = message.getData();
                String encrypted = data == null ? ""
                        : data.getString(NativeSignService.EXTRA_ENCRYPTED_PHONE, "");
                String error = data == null ? ""
                        : data.getString(NativeSignService.EXTRA_ERROR, "");
                result.set(encrypted.isEmpty()
                        ? Result.failure(error) : Result.success(encrypted));
                latch.countDown();
            }
        });
        Context serviceContext = app;
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                if (cancelled.get()) return;
                try {
                    Message request = Message.obtain(null, NativeSignService.MSG_ENCRYPT_PHONE);
                    Bundle data = new Bundle();
                    data.putString(NativeSignService.EXTRA_PHONE, normalizedPhone);
                    request.setData(data);
                    request.replyTo = reply;
                    new Messenger(service).send(request);
                } catch (RemoteException error) {
                    result.set(Result.failure("native service connection failed"));
                    latch.countDown();
                }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                if (result.get() == null) {
                    result.set(Result.failure("native service disconnected"));
                }
                latch.countDown();
            }
        };
        try {
            boolean connected = serviceContext.bindService(
                    new Intent(serviceContext, NativeSignService.class),
                    connection, Context.BIND_AUTO_CREATE);
            if (!connected) return Result.failure("native service is unavailable");
            bound.set(true);
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log(logger, "mobile login phone encryption timed out");
                return Result.failure("phone encryption timed out");
            }
            Result value = result.get();
            return value == null ? Result.failure("phone encryption failed") : value;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Result.failure("phone encryption interrupted");
        } catch (RuntimeException error) {
            log(logger, "mobile login crypto bridge failed: "
                    + error.getClass().getSimpleName());
            return Result.failure("phone encryption failed");
        } finally {
            cancelled.set(true);
            if (bound.get()) {
                try {
                    serviceContext.unbindService(connection);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private static void log(Logger logger, String message) {
        if (logger != null) logger.log(message);
    }
}
