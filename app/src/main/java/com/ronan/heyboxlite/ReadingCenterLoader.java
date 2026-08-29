package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.SystemClock;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ReadingCenterLoader {
    private static final long CACHE_TTL_MS = 30_000L;

    interface Callback {
        void onLoaded(Snapshot snapshot);
        void onError();
    }

    static final class Snapshot {
        final FeedItem recent;
        final int watchLaterCount;
        final long offlineBytes;

        Snapshot(FeedItem recent, int watchLaterCount, long offlineBytes) {
            this.recent = recent;
            this.watchLaterCount = watchLaterCount;
            this.offlineBytes = offlineBytes;
        }
    }

    private final Activity activity;
    private final LocalCache cache;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "heybox-reading-center");
        thread.setDaemon(true);
        return thread;
    });
    private int generation;
    private boolean loading;
    private boolean closed;
    private Snapshot cached;
    private long cachedAt;
    private Callback callback;

    ReadingCenterLoader(Activity activity, LocalCache cache) {
        this.activity = activity;
        this.cache = cache;
    }

    void load(Callback callback) {
        if (this.closed) return;
        this.callback = callback;
        long now = System.currentTimeMillis();
        if (this.cached != null) callback.onLoaded(this.cached);
        if (this.loading
                || (this.cached != null && now - this.cachedAt < CACHE_TTL_MS)) {
            return;
        }
        this.loading = true;
        int token = ++this.generation;
        this.executor.execute(() -> loadInBackground(token));
    }

    void close() {
        this.closed = true;
        this.generation++;
        this.callback = null;
        this.executor.shutdownNow();
    }

    private void loadInBackground(int token) {
        try {
            long startedAt = SystemClock.elapsedRealtime();
            List<FeedItem> recentItems = this.cache.recentItems();
            long recentAt = SystemClock.elapsedRealtime();
            List<LocalCache.OfflineItem> watchLater = this.cache.watchLaterItems();
            long watchLaterAt = SystemClock.elapsedRealtime();
            Snapshot result = new Snapshot(recentItems.isEmpty() ? null : recentItems.get(0),
                    watchLater.size(), this.cache.offlineBytes());
            long finishedAt = SystemClock.elapsedRealtime();
            this.cache.log("perf reading-center recentMs=" + (recentAt - startedAt)
                    + " watchLaterMs=" + (watchLaterAt - recentAt)
                    + " sizeMs=" + (finishedAt - watchLaterAt)
                    + " totalMs=" + (finishedAt - startedAt));
            this.activity.runOnUiThread(() -> finish(token, result));
        } catch (RuntimeException error) {
            CrashReporter.recordNonFatal(this.activity, "reading_center_load", error);
            this.activity.runOnUiThread(() -> fail(token));
        }
    }

    private void finish(int token, Snapshot result) {
        if (this.closed || token != this.generation || this.activity.isFinishing()) return;
        this.loading = false;
        this.cached = result;
        this.cachedAt = System.currentTimeMillis();
        if (this.callback != null) this.callback.onLoaded(result);
    }

    private void fail(int token) {
        if (this.closed || token != this.generation || this.activity.isFinishing()) return;
        this.loading = false;
        if (this.callback != null) this.callback.onError();
    }
}
