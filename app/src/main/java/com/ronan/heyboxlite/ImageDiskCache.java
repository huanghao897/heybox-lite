package com.ronan.heyboxlite;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ImageDiskCache {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final long MAX_OFFLINE_BYTES = 96L * 1024L * 1024L;
    private static File directory;

    private ImageDiskCache() {}

    static synchronized void init(Context context) {
        if (directory != null || context == null) return;
        directory = new File(context.getApplicationContext().getFilesDir(),
                "offline-cache/images");
        directory.mkdirs();
    }

    static File exactFile(String url) {
        File dir = directory;
        String original = ImageLoader.originalUrl(url);
        if (dir == null || original.isEmpty()) return null;
        return new File(dir, hash(original) + "-" + hash(url).substring(0, 12) + ".img");
    }

    static File fallbackFile(String url) {
        File exact = exactFile(url);
        if (exact == null || exact.exists()) return exact;
        File dir = directory;
        String original = ImageLoader.originalUrl(url);
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null || original.isEmpty()) return null;
        String prefix = hash(original) + "-";
        File best = null;
        for (File file : files) {
            if (!file.getName().startsWith(prefix) || !file.getName().endsWith(".img")) continue;
            if (best == null || file.lastModified() > best.lastModified()) best = file;
        }
        return best;
    }

    static byte[] read(File file, int maxBytes) {
        if (file == null || !file.isFile() || file.length() <= 0L
                || file.length() > maxBytes) return null;
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            file.setLastModified(System.currentTimeMillis());
            return output.toByteArray();
        } catch (OutOfMemoryError error) {
            return null;
        } catch (Exception error) {
            file.delete();
            return null;
        }
    }

    static synchronized void write(String url, byte[] bytes) {
        File target = exactFile(url);
        if (target == null || bytes == null || bytes.length == 0) return;
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            output.write(bytes);
            output.flush();
            if (target.exists()) target.delete();
            if (!temp.renameTo(target)) temp.delete();
        } catch (Exception error) {
            temp.delete();
        }
    }

    static long bytes(List<String> sourceUrls) {
        File dir = directory;
        if (dir == null || sourceUrls == null || sourceUrls.isEmpty()) return 0L;
        Set<String> prefixes = new HashSet<>();
        for (String source : sourceUrls) {
            String original = ImageLoader.originalUrl(source);
            if (!original.isEmpty()) prefixes.add(hash(original) + "-");
        }
        File[] files = dir.listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File file : files) {
            for (String prefix : prefixes) {
                if (file.getName().startsWith(prefix) && file.getName().endsWith(".img")) {
                    total += file.length();
                    break;
                }
            }
        }
        return total;
    }

    static void prune(long maxAgeMs) {
        File dir = directory;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - Math.max(0L, maxAgeMs);
        List<FileSnapshot> kept = new ArrayList<>();
        long total = 0L;
        for (FileSnapshot snapshot : FileSnapshot.captureFiles(files)) {
            if (maxAgeMs > 0L && snapshot.lastModified < cutoff) {
                snapshot.file.delete();
            } else {
                kept.add(snapshot);
                total += snapshot.length;
            }
        }
        FileSnapshot.sortOldestFirst(kept);
        for (FileSnapshot snapshot : kept) {
            if (total <= MAX_OFFLINE_BYTES) break;
            if (snapshot.file.delete()) total -= snapshot.length;
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                out.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            return Integer.toHexString(value.hashCode()) + "000000000000";
        }
    }
}
