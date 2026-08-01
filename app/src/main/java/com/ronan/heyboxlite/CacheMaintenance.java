package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Handler;

import java.io.File;

final class CacheMaintenance {
    private static final long OFFLINE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;

    private final Activity activity;
    private final LocalCache localCache;
    private final Handler mainHandler;

    CacheMaintenance(Activity activity, LocalCache localCache, Handler mainHandler) {
        this.activity = activity;
        this.localCache = localCache;
        this.mainHandler = mainHandler;
    }

    void pruneOffline(Runnable complete) {
        new Thread(() -> {
            this.localCache.pruneExpired(OFFLINE_MAX_AGE_MS);
            ImageLoader.pruneOffline(this.activity, OFFLINE_MAX_AGE_MS);
            if (complete != null) {
                this.mainHandler.post(() -> {
                    if (!this.activity.isFinishing()) complete.run();
                });
            }
        }, "heybox-offline-cleanup").start();
    }

    long cacheBytes() {
        return temporaryBytes() + (long) ImageLoader.cacheSizeKb() * 1024L;
    }

    long clearTemporaryCache() {
        long before = temporaryBytes() + (long) ImageLoader.cacheSizeKb() * 1024L;
        deleteChildren(this.activity.getCacheDir());
        EmojiRenderer.clear();
        ImageLoader.clear();
        return before;
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
