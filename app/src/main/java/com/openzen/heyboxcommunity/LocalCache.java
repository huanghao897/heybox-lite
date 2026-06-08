package com.openzen.heyboxcommunity;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class LocalCache {
    private static final String PREFS = "heybox_local_cache";
    private static final String FEED_ITEMS = "feed_items";
    private static final String FEED_SAVED_AT = "feed_saved_at";
    private static final String SCROLL_PREFIX = "scroll_";
    private static final int MAX_DETAIL_FILES = 80;
    private static final int MAX_LOG_BYTES = 96 * 1024;

    private final Context context;
    private final SharedPreferences prefs;
    private final File rootDir;
    private final File detailDir;
    private final File savedDir;
    private final File diagnosticsDir;

    LocalCache(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.rootDir = new File(this.context.getFilesDir(), "offline-cache");
        this.detailDir = new File(rootDir, "details");
        this.savedDir = new File(rootDir, "saved-lists");
        File external = this.context.getExternalFilesDir(null);
        this.diagnosticsDir = new File(external == null ? rootDir : external, "diagnostics");
        detailDir.mkdirs();
        savedDir.mkdirs();
        diagnosticsDir.mkdirs();
    }

    void saveFeed(List<FeedItem> items) {
        prefs.edit()
                .putString(FEED_ITEMS, encodeItems(items))
                .putLong(FEED_SAVED_AT, System.currentTimeMillis())
                .apply();
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
        return decodeItems(read(file(savedDir, key + ".json")));
    }

    void saveDetail(String linkId, JSONObject body) {
        if (linkId == null || linkId.isEmpty() || body == null) return;
        write(file(detailDir, linkId + ".json"), body.toString());
        prune(detailDir, MAX_DETAIL_FILES);
    }

    JSONObject detail(String linkId) {
        String value = read(file(detailDir, linkId + ".json"));
        if (value.isEmpty()) return null;
        try {
            return new JSONObject(value);
        } catch (Exception ignored) {
            return null;
        }
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

    void log(String message) {
        String line = timestamp() + "  " + (message == null ? "" : message) + "\n";
        File file = logFile();
        String previous = read(file);
        String next = previous + line;
        if (next.length() > MAX_LOG_BYTES) {
            next = next.substring(Math.max(0, next.length() - MAX_LOG_BYTES));
        }
        write(file, next);
    }

    String recentLog() {
        return read(logFile());
    }

    File writeDiagnostics(String text) {
        diagnosticsDir.mkdirs();
        File output = new File(diagnosticsDir, "heybox-lite-diagnostics.txt");
        write(output, text == null ? "" : text);
        return output;
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

    private List<FeedItem> decodeItems(String value) {
        List<FeedItem> items = new ArrayList<>();
        if (value == null || value.isEmpty()) return items;
        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) items.add(FeedItem.from(object));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private File logFile() {
        return new File(diagnosticsDir, "events.log");
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
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write((value == null ? "" : value).getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
    }

    private String read(File file) {
        if (file == null || !file.exists()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void prune(File dir, int keep) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= keep) return;
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        sorted.sort(Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < sorted.size() - keep; i++) {
            sorted.get(i).delete();
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

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
