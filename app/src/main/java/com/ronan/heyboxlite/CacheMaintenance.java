package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Handler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CacheMaintenance {
    interface BytesCallback {
        void onComplete(long bytes);
    }

    private static final long OFFLINE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;

    private final Activity activity;
    private final LocalCache localCache;
    private final Handler mainHandler;
    private final Object pruneLock = new Object();
    private final List<Runnable> pruneCallbacks = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean pruneRunning;

    CacheMaintenance(Activity activity, LocalCache localCache, Handler mainHandler) {
        this.activity = activity;
        this.localCache = localCache;
        this.mainHandler = mainHandler;
    }

    void pruneOffline(Runnable complete) {
        synchronized (this.pruneLock) {
            if (complete != null) this.pruneCallbacks.add(complete);
            if (this.pruneRunning) return;
            this.pruneRunning = true;
        }
        this.executor.execute(() -> {
            try {
                this.localCache.pruneExpired(OFFLINE_MAX_AGE_MS);
                ImageLoader.pruneOffline(this.activity, OFFLINE_MAX_AGE_MS);
            } finally {
                finishPrune();
            }
        });
    }

    private void finishPrune() {
        List<Runnable> callbacks;
        synchronized (this.pruneLock) {
            this.pruneRunning = false;
            callbacks = new ArrayList<>(this.pruneCallbacks);
            this.pruneCallbacks.clear();
        }
        if (callbacks.isEmpty()) return;
        this.mainHandler.post(() -> {
            if (this.activity.isFinishing()) return;
            for (Runnable callback : callbacks) callback.run();
        });
    }

    void cacheBytes(BytesCallback callback) {
        this.executor.execute(() -> post(callback,
                temporaryBytes() + (long) ImageLoader.cacheSizeKb() * 1024L));
    }

    void clearTemporaryCache(BytesCallback callback) {
        this.executor.execute(() -> {
            long before = temporaryBytes() + (long) ImageLoader.cacheSizeKb() * 1024L;
            deleteChildren(this.activity.getCacheDir());
            EmojiRenderer.clear();
            ImageLoader.clear();
            post(callback, before);
        });
    }

    void close() {
        this.executor.shutdownNow();
    }

    private void post(BytesCallback callback, long bytes) {
        if (callback == null) return;
        this.mainHandler.post(() -> {
            if (!this.activity.isFinishing()) callback.onComplete(bytes);
        });
    }

    private long temporaryBytes() {
        return sizeOf(this.activity.getCacheDir())
                + (long) EmojiRenderer.cacheSizeKb() * 1024L;
    }

    private long sizeOf(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) total += sizeOf(child);
        return total;
    }

    private void deleteChildren(File directory) {
        if (directory == null || !directory.isDirectory()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) deleteTree(child);
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        file.delete();
    }
}
