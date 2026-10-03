package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancels queued decoding without interrupting an in-flight download or disk-cache write. */
final class ImageLoadRequest {
    interface Decode {
        Bitmap run();
    }

    private final Handler main;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile Runnable delivery;
    private Runnable task;
    private ExecutorService executor;

    private ImageLoadRequest(Handler main) {
        this.main = main;
    }

    static ImageLoadRequest deliver(Handler main, Bitmap bitmap, ImageLoader.Callback callback) {
        ImageLoadRequest request = new ImageLoadRequest(main);
        request.post(bitmap, callback);
        return request;
    }

    static ImageLoadRequest submit(ExecutorService executor, Handler main,
                                   Decode decode, ImageLoader.Callback callback) {
        ImageLoadRequest request = new ImageLoadRequest(main);
        request.executor = executor;
        request.task = () -> {
            if (request.cancelled.get()) return;
            Bitmap bitmap = decode.run();
            if (!request.cancelled.get()) request.post(bitmap, callback);
        };
        executor.execute(request.task);
        return request;
    }

    private void post(Bitmap bitmap, ImageLoader.Callback callback) {
        delivery = () -> {
            if (!cancelled.get()) callback.onLoaded(bitmap);
        };
        if (!cancelled.get()) main.post(delivery);
    }

    void cancel() {
        cancelled.set(true);
        Runnable pendingDelivery = delivery;
        if (pendingDelivery != null) main.removeCallbacks(pendingDelivery);
        if (task != null && executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executor).remove(task);
        }
    }
}
