package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/** Reads the existing watch-later manifest; uncertainty must never discard offline content. */
final class SavedOfflineStorage {
    private static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;
    private final Set<String> details = new HashSet<>();
    private final Set<String> images = new HashSet<>();
    private boolean preserveAll;
    private File manifest;
    private long revision;
    private long length;
    private long modified;
    private boolean existed;

    static SavedOfflineStorage read(File filesDir) {
        SavedOfflineStorage saved = new SavedOfflineStorage();
        File manifest = new File(filesDir, "offline-cache/saved-lists/watch-later.json");
        saved.manifest = manifest;
        saved.revision = LocalStorageWrites.savedRevision();
        try {
            saved.existed = manifest.exists();
            saved.length = manifest.length();
            saved.modified = manifest.lastModified();
            if (!manifest.getCanonicalFile().equals(new File(filesDir.getCanonicalFile(),
                    "offline-cache/saved-lists/watch-later.json"))) {
                saved.preserveAll = true;
            } else if (saved.existed) {
                saved.readManifest(manifest);
            }
            if (!saved.isCurrent()) saved.preserveAll = true;
        } catch (IOException | JSONException | SecurityException error) {
            saved.preserveAll = true;
        }
        return saved;
    }

    // Called under the lease lock only at deletion, never while walking or parsing JSON.
    boolean isCurrent() throws IOException {
        return revision == LocalStorageWrites.savedRevision()
                && !StorageFileLeases.inUse(manifest)
                && existed == manifest.exists() && length == manifest.length()
                && modified == manifest.lastModified();
    }

    boolean keepsDetail(String name) {
        return preserveAll || details.contains(name);
    }

    boolean readable() {
        return !preserveAll;
    }

    boolean keepsImage(String name) {
        int separator = name.indexOf('-');
        return preserveAll || (separator > 0 && images.contains(name.substring(0, separator)));
    }

    private void readManifest(File file) throws IOException, JSONException {
        if (!file.isFile() || file.length() > MAX_MANIFEST_BYTES) {
            throw new IOException("Unreadable offline manifest");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > MAX_MANIFEST_BYTES - bytes.size()) {
                    throw new IOException("Offline manifest too large");
                }
                bytes.write(buffer, 0, count);
            }
        }
        JSONArray entries = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            JSONObject item = entry.getJSONObject("item");
            Object rawId = item.has("linkid") ? item.get("linkid") : item.get("link_id");
            if (!(rawId instanceof String)) throw new JSONException("Invalid offline item id");
            String id = (String) rawId;
            if (id.isEmpty()) throw new JSONException("Missing offline item id");
            details.add((id + ".json").replaceAll("[^A-Za-z0-9._-]+", "_"));
            addImages(entry.has("images") ? entry.getJSONArray("images") : null);
            addImages(item.has("imgs") ? item.getJSONArray("imgs") : null);
            addImage(item.optString("image"));
        }
    }

    private void addImages(JSONArray urls) throws JSONException {
        if (urls == null) return;
        for (int index = 0; index < urls.length(); index++) {
            Object value = urls.get(index);
            if (!(value instanceof String)) throw new JSONException("Invalid offline image URL");
            addImage((String) value);
        }
    }

    private void addImage(String source) {
        String original = originalUrl(source);
        if (!original.isEmpty()) images.add(hash(original));
    }

    static String originalUrl(String source) {
        if (source == null || source.isEmpty()) return "";
        int query = source.indexOf('?');
        String value = (query >= 0 ? source.substring(0, query) : source).replace("\\/", "/");
        if (value.startsWith("//")) value = "https:" + value;
        if (value.regionMatches(true, 0, "http:", 0, 5)) value = "https:" + value.substring(5);
        return value.startsWith("https://") ? value : "";
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(Character.forDigit((item & 0xff) >>> 4, 16));
                result.append(Character.forDigit(item & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
