package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/** One removable task and at most one main-thread delivery, with explicit result ownership. */
final class ImageLoadRequest {
    interface Decode { Bitmap run(); }
    interface Work<T> { T run(); }
    interface Result<T> { void accept(T value); }
    interface Release<T> { void release(T value); }

    private final Handler main;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ImageWorkToken token = new ImageWorkToken();
    private Runnable delivery;
    private Task<?> task;
    private ExecutorService executor;
    private Runnable discardResult;

    private ImageLoadRequest(Handler main) {
        this.main = main;
    }

    static ImageLoadRequest deliver(Handler main, Bitmap bitmap, ImageLoader.Callback callback) {
        ImageLoadRequest request = new ImageLoadRequest(main);
        request.post(bitmap, callback::onLoaded, ignored -> {});
        return request;
    }

    static ImageLoadRequest submit(ExecutorService executor, Handler main,
                                   Decode decode, ImageLoader.Callback callback) {
        return work(executor, main, decode::run, callback::onLoaded, ignored -> {});
    }

    static <T> ImageLoadRequest work(ExecutorService executor, Handler main, Work<T> work,
                                     Result<T> callback, Release<T> release) {
        return work(executor, main, work, callback, release, () -> {});
    }

    static <T> ImageLoadRequest work(ExecutorService executor, Handler main, Work<T> work,
                                     Result<T> callback, Release<T> release, Runnable notStarted) {
        ImageLoadRequest request = new ImageLoadRequest(main);
        request.executor = executor;
        request.task = new Task<>(request, work, callback, release, notStarted);
        executor.execute(request.task);
        return request;
    }

    private static final class Task<T> implements ImageTaskExecutor.Discardable {
        private final ImageLoadRequest request;
        private final Work<T> work;
        private final Release<T> release;
        private final Runnable notStarted;
        private Result<T> result;
        private boolean started;

        Task(ImageLoadRequest request, Work<T> work, Result<T> result, Release<T> release,
             Runnable notStarted) {
            this.request = request;
            this.work = work;
            this.result = result;
            this.release = release;
            this.notStarted = notStarted;
        }

        @Override public void run() {
            synchronized (request) {
                if (request.cancelled.get()) return;
                started = true;
            }
            T value;
            ImageWorkToken.enter(request.token);
            try { value = work.run(); }
            catch (RuntimeException | OutOfMemoryError error) {
                notStarted.run();
                value = null;
            }
            finally { ImageWorkToken.abortWith(null); ImageWorkToken.leave(); }
            synchronized (request) {
                Result<T> target = result;
                result = null;
                request.task = null;
                if (target == null) release.release(value);
                else request.post(value, target, release);
            }
        }

        @Override public void discarded() {
            synchronized (request) {
                notStarted.run();
                Result<T> target = result;
                result = null;
                request.task = null;
                if (target != null) request.post(null, target, release);
            }
        }
    }

    private synchronized <T> void post(T value, Result<T> callback, Release<T> release) {
        if (cancelled.get()) { release.release(value); return; }
        discardResult = () -> release.release(value);
        delivery = () -> {
            synchronized (this) {
                if (cancelled.get()) return;
                delivery = null;
                discardResult = null;
            }
            callback.accept(value);
        };
        main.post(delivery);
    }

    void cancel() {
        synchronized (this) {
            if (!cancelled.compareAndSet(false, true)) return;
            if (delivery != null) main.removeCallbacks(delivery);
            if (discardResult != null) discardResult.run();
            if (task != null) {
                task.result = null;
                if (!task.started) task.notStarted.run();
            }
            if (executor instanceof ThreadPoolExecutor && task != null) {
                ((ThreadPoolExecutor) executor).remove(task);
            }
            delivery = null;
            discardResult = null;
            task = null;
        }
        token.cancel();
    }
}
