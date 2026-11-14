package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

final class StorageTestFiles {
    static final long DAY = 24L * 60L * 60L * 1000L;
    static final long NOW = 1_800_000_000_000L;
    static final long START = NOW - DAY;
    static final long OLD = START - DAY;
    final File files;
    final File cache;
    final File externalCache;
    final File external;
    final File installed;
    final File runtime;
    final File preferences;
    final DisposableStorage.Roots roots;

    StorageTestFiles(File root) throws IOException {
        files = directory(root, "files");
        cache = directory(root, "cache");
        externalCache = directory(root, "external-cache");
        external = directory(root, "external-files");
        installed = directory(root, "installed");
        runtime = directory(root, "app_native_signer");
        preferences = directory(root, "shared_prefs");
        roots = new DisposableStorage.Roots(files, Arrays.asList(cache, externalCache, null, cache),
                Arrays.asList(external, null), Collections.singletonList(installed),
                Collections.singletonList(runtime), Collections.singletonList(preferences));
    }

    DisposableStorage storage() {
        return new DisposableStorage(roots, START);
    }

    File put(File root, String path, long bytes, long modified) throws IOException {
        File file = new File(root, path);
        File parent = file.getParentFile();
        assertTrue(parent.isDirectory() || parent.mkdirs());
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            output.setLength(bytes);
        }
        assertTrue(file.setLastModified(modified));
        return file.getCanonicalFile();
    }

    File image(String source, String variant, long bytes) throws IOException {
        return put(files, "offline-cache/images/" + imageName(source, variant), bytes, OLD);
    }

    static String imageName(String source, String variant) {
        return SavedOfflineStorage.hash(SavedOfflineStorage.originalUrl(source)) + "-"
                + SavedOfflineStorage.hash(variant).substring(0, 12) + ".img";
    }

    File manifest(String id, String... urls) throws Exception {
        JSONObject item = new JSONObject().put("linkid", id);
        JSONArray images = new JSONArray(Arrays.asList(urls));
        return manifestText(new JSONArray().put(new JSONObject()
                .put("item", item).put("images", images)
                .put("updated_at", 1)).toString());
    }

    File manifestText(String text) throws IOException {
        File file = put(files, "offline-cache/saved-lists/watch-later.json", 0L, OLD);
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File directory(File root, String name) throws IOException {
        File file = new File(root, name);
        assertTrue(file.mkdirs());
        return file.getCanonicalFile();
    }
}
