package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class LocalCache {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String PREFS = "heybox_local_cache";
    private static final String FEED_ITEMS = "feed_items";
    private static final String FEED_SAVED_AT = "feed_saved_at";
    private static final String WATCH_LATER_FILE = "watch-later.json";
    private static final String RECENT_ITEMS = "recent-items";
    private static final String SCROLL_PREFIX = "scroll_";
    private static final int MAX_DETAIL_FILES = 80;
    private static final int MAX_OFFLINE_COMMENTS = 10;
    private static final int MAX_CACHED_FEED_ITEMS = 60;
    private static final ExecutorService CACHE_EXECUTOR =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "heybox-local-cache");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private final SharedPreferences prefs;
    private final File rootDir;
    private final File detailDir;
    private final File savedDir;
    private final LocalDiagnosticsLog diagnostics;

    static final class OfflineItem {
        final FeedItem item;
        final long savedAt;
        final long updatedAt;
        final long detailBytes;
        final List<String> imageUrls;

        OfflineItem(FeedItem item, long savedAt, long updatedAt,
                    long detailBytes, List<String> imageUrls) {
            this.item = item;
            this.savedAt = savedAt;
            this.updatedAt = updatedAt;
            this.detailBytes = detailBytes;
            this.imageUrls = imageUrls;
        }
    }

    LocalCache(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.rootDir = new File(app.getFilesDir(), "offline-cache");
        this.detailDir = new File(rootDir, "details");
        this.savedDir = new File(rootDir, "saved-lists");
        detailDir.mkdirs();
        savedDir.mkdirs();
        this.diagnostics = new LocalDiagnosticsLog(app, rootDir);
    }

    void saveFeed(List<FeedItem> items) {
        int count = items == null ? 0 : Math.min(items.size(), MAX_CACHED_FEED_ITEMS);
        List<FeedItem> snapshot = count == 0
                ? new ArrayList<>() : new ArrayList<>(items.subList(0, count));
        long savedAt = System.currentTimeMillis();
        CACHE_EXECUTOR.execute(() -> {
            try {
                prefs.edit().putString(FEED_ITEMS, encodeFeedItems(snapshot))
                        .putLong(FEED_SAVED_AT, savedAt).apply();
            } catch (OutOfMemoryError ignored) {
                // Optional cache writes must not terminate reading on small heaps.
            }
        });
    }

    List<FeedItem> feedItems() {
        return decodeItems(prefs.getString(FEED_ITEMS, "[]"));
    }

    long feedSavedAt() {
        return prefs.getLong(FEED_SAVED_AT, 0L);
    }

    void saveSavedList(String key, List<FeedItem> items) {
        write(file(savedDir, key + ".json"), encodeItems(items));
    }

    List<FeedItem> savedList(String key) {
        try {
            return decodeItems(read(file(savedDir, key + ".json")));
        } catch (OutOfMemoryError ignored) {
            return new ArrayList<>();
        }
    }

    synchronized void rememberRecent(FeedItem item) {
        if (item == null || item.id.isEmpty()) return;
        try {
            List<FeedItem> current = savedList(RECENT_ITEMS);
            List<FeedItem> next = new ArrayList<>();
            next.add(item);
            for (FeedItem value : current) {
                if (!item.id.equals(value.id) && next.size() < 50) next.add(value);
            }
            saveSavedList(RECENT_ITEMS, next);
        } catch (OutOfMemoryError ignored) {
            // 阅读历史是辅助功能，低内存时应跳过记录而不是中断打开帖子。
        }
    }

    List<FeedItem> recentItems() {
        return savedList(RECENT_ITEMS);
    }

    void saveDetail(String linkId, JSONObject body) {
        if (linkId == null || linkId.isEmpty() || body == null) return;
        JSONObject cached = copyForOffline(body);
        if (cached == null) return;
        CACHE_EXECUTOR.execute(() -> {
            try {
                String value = cached.toString();
                if (value != null) write(file(detailDir, linkId + ".json"), value);
                prune(detailDir, MAX_DETAIL_FILES);
            } catch (OutOfMemoryError ignored) {
                // Keep the previously saved detail when a new snapshot cannot fit.
            }
        });
    }

    JSONObject detail(String linkId) {
        File source = file(detailDir, linkId + ".json");
        String value = read(source);
        if (value.isEmpty()) return null;
        try {
            JSONObject body = new JSONObject(value);
            if (trimOfflineComments(body)) write(source, body.toString());
            source.setLastModified(System.currentTimeMillis());
            return body;
        } catch (JSONException | SecurityException | OutOfMemoryError ignored) {
            return null;
        }
    }

    private JSONObject copyForOffline(JSONObject body) {
        try {
            return OfflineDetailSnapshot.copy(body);
        } catch (JSONException | OutOfMemoryError ignored) {
            return null;
        }
    }

    private boolean trimOfflineComments(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONArray comments = result == null ? null : result.optJSONArray("comments");
        if (comments == null || comments.length() <= MAX_OFFLINE_COMMENTS) return false;
        JSONArray limited = new JSONArray();
        for (int i = 0; i < MAX_OFFLINE_COMMENTS; i++) limited.put(comments.opt(i));
        try {
            result.put("comments", limited);
            return true;
        } catch (JSONException ignored) {
            return false;
        }
    }

    synchronized boolean isWatchLater(String linkId) {
        if (linkId == null || linkId.isEmpty()) return false;
        JSONArray items = watchLaterArray();
        for (int i = 0; i < items.length(); i++) {
            if (linkId.equals(watchLaterId(items.optJSONObject(i)))) return true;
        }
        return false;
    }

    synchronized void addWatchLater(FeedItem item) {
        if (item == null || item.id.isEmpty()) return;
        long now = System.currentTimeMillis();
        JSONArray current = watchLaterArray();
        JSONArray next = new JSONArray();
        JSONObject entry = watchLaterEntry(item, now, now, itemImageUrls(item));
        next.put(entry);
        for (int i = 0; i < current.length(); i++) {
            JSONObject existing = current.optJSONObject(i);
            if (!item.id.equals(watchLaterId(existing))) next.put(existing);
        }
        writeWatchLater(next);
    }

    synchronized void updateWatchLater(FeedItem item, List<String> imageUrls) {
        if (item == null || item.id.isEmpty()) return;
        JSONArray items = watchLaterArray();
        boolean changed = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject entry = items.optJSONObject(i);
            if (!item.id.equals(watchLaterId(entry))) continue;
            try {
                entry.put("item", item.toJson());
                entry.put("updated_at", System.currentTimeMillis());
                entry.put("images", encodeStrings(imageUrls));
                changed = true;
            } catch (JSONException ignored) {
            }
            break;
        }
        if (changed) writeWatchLater(items);
    }

    synchronized void removeWatchLater(String linkId) {
        if (linkId == null || linkId.isEmpty()) return;
        JSONArray current = watchLaterArray();
        JSONArray next = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject entry = current.optJSONObject(i);
            if (!linkId.equals(watchLaterId(entry))) next.put(entry);
        }
        writeWatchLater(next);
    }

    synchronized List<OfflineItem> watchLaterItems() {
        List<OfflineItem> result = new ArrayList<>();
        JSONArray items = watchLaterArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject entry = items.optJSONObject(i);
            JSONObject value = entry == null ? null : entry.optJSONObject("item");
            if (value == null) continue;
            FeedItem item = FeedItem.from(value);
            if (item.id.isEmpty()) continue;
            result.add(new OfflineItem(item,
                    entry.optLong("saved_at", 0L),
                    entry.optLong("updated_at", 0L),
                    detailBytes(item.id),
                    decodeStrings(entry.optJSONArray("images"))));
        }
        return result;
    }

    synchronized int pruneExpired(long maxAgeMs) {
        if (maxAgeMs <= 0L) return 0;
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        Set<String> active = new HashSet<>();
        for (OfflineItem entry : watchLaterItems()) {
            if (entry.updatedAt >= cutoff) active.add(safeName(entry.item.id) + ".json");
        }
        int removed = 0;
        File[] files = detailDir.listFiles();
        if (files == null) return removed;
        for (File source : files) {
            if (source.isFile() && source.lastModified() < cutoff
                    && !active.contains(source.getName()) && source.delete()) {
                removed++;
            }
        }
        return removed;
    }

    long detailBytes(String linkId) {
        if (linkId == null || linkId.isEmpty()) return 0L;
        File source = file(detailDir, linkId + ".json");
        return source.isFile() ? source.length() : 0L;
    }

    void saveScroll(String linkId, int scrollY) {
        if (linkId == null || linkId.isEmpty()) return;
        prefs.edit().putInt(SCROLL_PREFIX + safeName(linkId), Math.max(0, scrollY)).apply();
    }

    int scroll(String linkId) {
        if (linkId == null || linkId.isEmpty()) return 0;
        return prefs.getInt(SCROLL_PREFIX + safeName(linkId), 0);
    }

    long offlineBytes() {
        return size(rootDir);
    }

    int detailCount() {
        File[] files = detailDir.listFiles();
        return files == null ? 0 : files.length;
    }

    private JSONObject watchLaterEntry(FeedItem item, long savedAt, long updatedAt,
                                       List<String> imageUrls) {
        JSONObject entry = new JSONObject();
        try {
            entry.put("item", item.toJson());
            entry.put("saved_at", savedAt);
            entry.put("updated_at", updatedAt);
            entry.put("images", encodeStrings(imageUrls));
        } catch (JSONException ignored) {
        }
        return entry;
    }

    private JSONArray watchLaterArray() {
        String value = read(file(savedDir, WATCH_LATER_FILE));
        if (value.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(value);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void writeWatchLater(JSONArray items) {
        write(file(savedDir, WATCH_LATER_FILE), items == null ? "[]" : items.toString());
    }

    private String watchLaterId(JSONObject entry) {
        JSONObject item = entry == null ? null : entry.optJSONObject("item");
        return item == null ? "" : item.optString("linkid", item.optString("link_id"));
    }

    private List<String> itemImageUrls(FeedItem item) {
        List<String> urls = new ArrayList<>();
        if (item == null) return urls;
        if (item.image != null && !item.image.isEmpty()) urls.add(item.image);
        for (String url : item.images) {
            if (url != null && !url.isEmpty() && !urls.contains(url)) urls.add(url);
        }
        return urls;
    }

    private JSONArray encodeStrings(List<String> values) {
        JSONArray array = new JSONArray();
        if (values == null) return array;
        for (String value : values) {
            if (value != null && !value.isEmpty()) array.put(value);
        }
        return array;
    }

    private List<String> decodeStrings(JSONArray values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i);
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    void log(String message) {
        diagnostics.log(message);
    }

    String recentLog() {
        return diagnostics.recentLog();
    }

    String previousLog() {
        return diagnostics.previousLog();
    }

    String crashLog() {
        return diagnostics.crashLog();
    }

    String previousCrashLog() {
        return diagnostics.previousCrashLog();
    }

    String nativeSignLog() {
        return diagnostics.nativeSignLog();
    }

    static void appendNativeSignLog(Context context, String message) {
        LocalDiagnosticsLog.appendNativeSignLog(context, message);
    }

    String sessionId() {
        return diagnostics.sessionId();
    }

    long sessionStartedAt() {
        return diagnostics.sessionStartedAt();
    }

    File writeDiagnostics(String text) {
        return diagnostics.writeDiagnostics(text);
    }

    private String encodeItems(List<FeedItem> items) {
        JSONArray array = new JSONArray();
        if (items != null) {
            for (FeedItem item : items) {
                if (item != null) array.put(item.toJson());
            }
        }
        return array.toString();
    }

    private String encodeFeedItems(List<FeedItem> items) {
        JSONArray array = new JSONArray();
        for (FeedItem item : items) {
            if (item != null) array.put(item.toCacheJson());
        }
        return array.toString();
    }

    private List<FeedItem> decodeItems(String value) {
        List<FeedItem> items = new ArrayList<>();
        if (value == null || value.isEmpty()) return items;
        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) items.add(FeedItem.from(object));
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    private File file(File dir, String name) {
        dir.mkdirs();
        return new File(dir, safeName(name));
    }

    private String safeName(String value) {
        String clean = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return clean.isEmpty() ? "default" : clean;
    }

    private void write(File file, String value) {
        writeStatic(file, value);
    }

    private static void writeStatic(File file, String value) {
        try {
            byte[] bytes = (value == null ? "" : value).getBytes(UTF_8);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(bytes);
            }
        } catch (IOException | SecurityException ignored) {
        }
    }

    private String read(File file) {
        return readStatic(file);
    }

    private static String readStatic(File file) {
        try {
            if (file == null || !file.exists()) return "";
        } catch (SecurityException ignored) {
            return "";
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), UTF_8);
        } catch (OutOfMemoryError ignored) {
            return "";
        } catch (IOException | SecurityException ignored) {
            return "";
        }
    }

    private void prune(File dir, int keep) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= keep) return;
        List<FileSnapshot> sorted = FileSnapshot.captureAll(files);
        FileSnapshot.sortOldestFirst(sorted);
        for (int i = 0; i < sorted.size() - keep; i++) {
            sorted.get(i).file.delete();
        }
    }

    private long size(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) total += size(child);
        return total;
    }

}
