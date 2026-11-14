package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CacheMaintenance {
    interface BytesCallback {
        void onComplete(long bytes);
    }

    interface StorageCallback {
        void onComplete(DisposableStorage.Snapshot snapshot);
    }

    interface CleanupCallback {
        void onComplete(DisposableStorage.ClearResult result);
    }

    private static final long OFFLINE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final long PROCESS_STARTED_AT = processStartedAt();

    private final Activity activity;
    private final Handler mainHandler;
    private final DisposableStorage storage;
    private final Object pruneLock = new Object();
    private final List<Runnable> pruneCallbacks = new ArrayList<>();
    private final List<StorageCallback> storageObservers = new ArrayList<>();
    private final ExecutorService executor;
    private final Runnable clearMemoryCache;
    private boolean pruneRunning;
    private boolean closed;

    CacheMaintenance(Activity activity, LocalCache localCache, Handler mainHandler) {
        this(activity, localCache, mainHandler,
                new DisposableStorage(AppStorageLocations.read(activity), PROCESS_STARTED_AT,
                        ImageDiskCache.class),
                Executors.newSingleThreadExecutor(), ImageLoader::clear);
    }

    CacheMaintenance(Activity activity, LocalCache localCache, Handler mainHandler,
                     DisposableStorage storage, ExecutorService executor, Runnable clearMemoryCache) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.storage = storage;
        this.executor = executor;
        this.clearMemoryCache = clearMemoryCache;
    }

    void pruneOffline(Runnable complete) {
        synchronized (this.pruneLock) {
            if (complete != null) this.pruneCallbacks.add(complete);
            if (this.pruneRunning) return;
            this.pruneRunning = true;
        }
        this.executor.execute(() -> {
            try {
                DisposableStorage.ClearResult result = this.storage.pruneExpired(
                        System.currentTimeMillis(), OFFLINE_MAX_AGE_MS);
                post(() -> publishStorage(result.after));
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
            if (this.activity.isFinishing() || this.activity.isDestroyed()) return;
            for (Runnable callback : callbacks) callback.run();
        });
    }

    void cacheBytes(BytesCallback callback) {
        storageSnapshot(snapshot -> {
            if (callback != null) callback.onComplete(snapshot.disposableBytes);
        });
    }

    // Compatibility for existing toast callers: this now means confirmed deleted disk bytes.
    void clearTemporaryCache(BytesCallback callback) {
        clearDisposableStorage(result -> {
            if (callback != null) callback.onComplete(result.reclaimedBytes);
        });
    }

    void storageSnapshot(StorageCallback callback) {
        this.executor.execute(() -> {
            DisposableStorage.Snapshot snapshot = this.storage.snapshot(System.currentTimeMillis());
            if (callback != null) post(() -> callback.onComplete(snapshot));
        });
    }

    // Subscriptions and delivery are main-thread confined; inventory stays on the executor.
    void observeStorage(StorageCallback observer) {
        if (this.closed) return;
        this.storageObservers.add(observer);
        refreshStorage();
    }

    void removeStorageObserver(StorageCallback observer) {
        this.storageObservers.remove(observer);
    }

    void refreshStorage() {
        if (!this.closed) storageSnapshot(this::publishStorage);
    }

    private void publishStorage(DisposableStorage.Snapshot snapshot) {
        for (StorageCallback observer : new ArrayList<>(this.storageObservers)) {
            observer.onComplete(snapshot);
        }
    }

    void clearDisposableStorage(CleanupCallback callback) {
        this.executor.execute(() -> {
            DisposableStorage.ClearResult result = this.storage.clear(System.currentTimeMillis());
            this.mainHandler.post(() -> {
                // Emoji request ownership is main-thread confined. Evict even after Activity exit.
                this.clearMemoryCache.run();
                if (this.closed || this.activity.isFinishing() || this.activity.isDestroyed()) return;
                publishStorage(result.after);
                if (callback != null) callback.onComplete(result);
            });
        });
    }

    void close() {
        this.closed = true;
        this.storageObservers.clear();
        this.executor.shutdownNow();
    }

    private void post(Runnable callback) {
        this.mainHandler.post(() -> {
            if (!this.closed && !this.activity.isFinishing() && !this.activity.isDestroyed()) callback.run();
        });
    }

    private static long processStartedAt() {
        if (Build.VERSION.SDK_INT < 24) return System.currentTimeMillis();
        return System.currentTimeMillis()
                - Math.max(0L, SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime());
    }
}
