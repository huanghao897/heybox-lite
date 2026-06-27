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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class LocalCache {
    private static final String PREFS = "heybox_local_cache";
    private static final String FEED_ITEMS = "feed_items";
    private static final String FEED_SAVED_AT = "feed_saved_at";
    private static final String SCROLL_PREFIX = "scroll_";
    private static final String OFFICIAL_NATIVE_DISABLED_CODE = "official_native_disabled_code";
    private static final String OFFICIAL_NATIVE_DISABLED_REASON = "official_native_disabled_reason";
    private static final int OFFICIAL_NATIVE_ATTEMPT_REVISION = 34;
    private static final int MAX_DETAIL_FILES = 80;
    private static final int MAX_LOG_BYTES = 96 * 1024;
    private static final Object SESSION_LOCK = new Object();
    private static String processSessionId;
    private static long processSessionStartedAt;
    private static boolean processSessionLogPrepared;

    private final Context context;
    private final SharedPreferences prefs;
    private final File rootDir;
    private final File detailDir;
    private final File savedDir;
    private final File diagnosticsDir;
    private final String sessionId;
    private final long sessionStartedAt;

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
        synchronized (SESSION_LOCK) {
            if (processSessionId == null || processSessionId.isEmpty()) {
                processSessionId = UUID.randomUUID().toString();
                processSessionStartedAt = System.currentTimeMillis();
                processSessionLogPrepared = false;
            }
            this.sessionId = processSessionId;
            this.sessionStartedAt = processSessionStartedAt;
            if (!processSessionLogPrepared) {
                resetSessionLogLocked();
                processSessionLogPrepared = true;
            }
        }
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
        synchronized (SESSION_LOCK) {
            File file = logFile();
            String previous = read(file);
            String next = previous + line;
            if (next.length() > MAX_LOG_BYTES) {
                next = next.substring(Math.max(0, next.length() - MAX_LOG_BYTES));
            }
            write(file, next);
        }
    }

    String recentLog() {
        synchronized (SESSION_LOCK) {
            return read(logFile());
        }
    }

    String previousLog() {
        synchronized (SESSION_LOCK) {
            return read(previousLogFile());
        }
    }

    String crashLog() {
        synchronized (SESSION_LOCK) {
            return read(crashLogFile());
        }
    }

    String previousCrashLog() {
        synchronized (SESSION_LOCK) {
            return read(previousCrashLogFile());
        }
    }

    String nativeSignLog() {
        synchronized (SESSION_LOCK) {
            return read(nativeSignLogFile());
        }
    }

    static void appendNativeSignLog(Context context, String message) {
        if (context == null) return;
        synchronized (SESSION_LOCK) {
            Context app = context.getApplicationContext();
            File external = app.getExternalFilesDir(null);
            File dir = new File(external == null
                    ? new File(app.getFilesDir(), "offline-cache") : external, "diagnostics");
            File file = new File(dir, "native-sign.log");
            String line = timestampNow() + "  " + (message == null ? "" : message) + "\n";
            String previous = readStatic(file);
            String next = previous + line;
            if (next.length() > MAX_LOG_BYTES) {
                next = next.substring(Math.max(0, next.length() - MAX_LOG_BYTES));
            }
            writeStatic(file, next);
        }
    }

    static boolean isOfficialNativeDisabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(OFFICIAL_NATIVE_DISABLED_CODE, -1) == officialNativeAttemptCode();
    }

    static String officialNativeDisabledReason(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(OFFICIAL_NATIVE_DISABLED_REASON, "");
    }

    static void disableOfficialNative(Context context, String reason) {
        if (context == null) return;
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(OFFICIAL_NATIVE_DISABLED_CODE, officialNativeAttemptCode())
                .putString(OFFICIAL_NATIVE_DISABLED_REASON, reason == null ? "" : reason)
                .apply();
        appendNativeSignLog(context, "official native disabled for this version: "
                + (reason == null ? "" : reason));
    }

    private static int officialNativeAttemptCode() {
        return BuildConfig.VERSION_CODE * 100 + OFFICIAL_NATIVE_ATTEMPT_REVISION;
    }

    String sessionId() {
        return sessionId;
    }

    long sessionStartedAt() {
        return sessionStartedAt;
    }

    File writeDiagnostics(String text) {
        diagnosticsDir.mkdirs();
        File output = new File(diagnosticsDir,
                "heybox-lite-diagnostics-" + timestampFile() + ".txt");
        write(output, text == null ? "" : text);
        write(new File(diagnosticsDir, "heybox-lite-diagnostics-latest.txt"),
                text == null ? "" : text);
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
        return new File(diagnosticsDir, "events-session.log");
    }

    private File previousLogFile() {
        return new File(diagnosticsDir, "events-previous-session.log");
    }

    private File crashLogFile() {
        return new File(diagnosticsDir, "crash-latest.log");
    }

    private File previousCrashLogFile() {
        return new File(diagnosticsDir, "crash-previous.log");
    }

    private File nativeSignLogFile() {
        return new File(diagnosticsDir, "native-sign.log");
    }

    private void resetSessionLogLocked() {
        String previous = read(logFile());
        if (!previous.trim().isEmpty()) {
            write(previousLogFile(), previous);
        }
        write(logFile(), "sessionId: " + sessionId + "\n"
                + "sessionStartedLocal: " + timestamp(sessionStartedAt) + "\n"
                + "sessionStartedMillis: " + sessionStartedAt + "\n");
        write(nativeSignLogFile(), "sessionId: " + sessionId + "\n"
                + "sessionStartedLocal: " + timestamp(sessionStartedAt) + "\n"
                + "sessionStartedMillis: " + sessionStartedAt + "\n");
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
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write((value == null ? "" : value).getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
    }

    private String read(File file) {
        return readStatic(file);
    }

    private static String readStatic(File file) {
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
        Collections.sort(sorted, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                long diff = left.lastModified() - right.lastModified();
                if (diff == 0L) return 0;
                return diff < 0L ? -1 : 1;
            }
        });
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
        return timestamp(System.currentTimeMillis());
    }

    private static String timestampNow() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private String timestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    private String timestampFile() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }
}
