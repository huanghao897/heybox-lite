package com.ronan.heyboxlite;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Inventories accessible app storage, but deletes only recognized disposable regular files. */
final class DisposableStorage {
    private static final Pattern IMAGE = Pattern.compile("[a-f0-9]{64}-[a-f0-9]{12}[.]img");
    private static final Pattern UPDATE = Pattern.compile(
            "heybox-Lite-update-[A-Za-z0-9._-]+-[0-9]+[.]apk(?:[.]part)?");
    private static final Pattern REPORT = Pattern.compile(
            "heybox-lite-diagnostics-[0-9]{8}-[0-9]{6}[.]txt");
    private static final Pattern REGION = Pattern.compile("long-image-[A-Za-z0-9_-]+[.]img");
    private static final long REPORT_GRACE_MS = 24L * 60L * 60L * 1000L;
    private static final long MAX_IMAGE_BYTES = 96L * 1024L * 1024L;
    private static final long IMAGE_SETTLE_MS = 100L;

    interface DeleteFile {
        boolean delete(File file);
    }

    interface ListDirectory {
        File[] list(File directory);
    }

    enum Kind { IMAGES, DETAILS, UPDATES, DIAGNOSTICS, REGION, RETAINED, CODE, UNKNOWN }

    static final class Roots {
        final File files;
        final List<File> caches;
        final List<File> externalFiles;
        final List<File> installedCode;
        final List<File> runtimeCode;
        final List<File> retainedData;

        Roots(File files, List<File> caches, List<File> externalFiles,
              List<File> installedCode, List<File> runtimeCode, List<File> retainedData) {
            this.files = files;
            this.caches = caches;
            this.externalFiles = externalFiles;
            this.installedCode = installedCode;
            this.runtimeCode = runtimeCode;
            this.retainedData = retainedData;
        }
    }

    static final class Snapshot {
        final long disposableBytes;
        final long retainedBytes;
        final long deferredBytes;
        final long installedCodeBytes;
        final long runtimeCodeBytes;
        final long unrecognizedBytes;
        final long imageBytes;
        final int detailCount;
        final boolean complete;
        private final List<Entry> entries;

        private Snapshot(Scan scan) {
            this.disposableBytes = scan.disposable;
            this.retainedBytes = scan.retained;
            this.deferredBytes = scan.deferred;
            this.installedCodeBytes = scan.installed;
            this.runtimeCodeBytes = scan.runtime;
            this.unrecognizedBytes = scan.unknown;
            this.imageBytes = scan.images;
            this.detailCount = scan.details;
            this.complete = scan.complete;
            this.entries = Collections.unmodifiableList(scan.entries);
        }
    }

    static final class ClearResult {
        final Snapshot before;
        final Snapshot after;
        // Confirmed removed file lengths, not RAM, a pre-clear estimate, or free-space delta.
        final long reclaimedBytes;
        final int deletedFiles;
        final int failedFiles;

        private ClearResult(Snapshot before, Snapshot after, long bytes, int deleted, int failed) {
            this.before = before;
            this.after = after;
            this.reclaimedBytes = bytes;
            this.deletedFiles = deleted;
            this.failedFiles = failed;
        }
    }

    private static final class Entry {
        final File file;
        final long bytes;
        final long modified;
        final Kind kind;

        Entry(File file, Kind kind) {
            this.file = file;
            this.bytes = Math.max(0L, file.length());
            this.modified = file.lastModified();
            this.kind = kind;
        }
    }

    private static final class Scan {
        final Set<File> seen = new HashSet<>();
        final List<Entry> entries = new ArrayList<>();
        long disposable, retained, deferred, installed, runtime, unknown, images;
        int details;
        boolean complete = true;
    }

    private final Roots roots;
    private final long processStartedAt;
    private final DeleteFile deleteFile;
    private final Object imageWriteLock;
    private final ListDirectory listDirectory;
    private static final long SKIPPED = -1L;
    private static final long FAILED = -2L;
    private static final long RETRY_SAVED = -3L;

    DisposableStorage(Roots roots, long processStartedAt) {
        this(roots, processStartedAt, File::delete);
    }

    DisposableStorage(Roots roots, long processStartedAt, Object imageWriteLock) {
        this(roots, processStartedAt, File::delete, imageWriteLock, File::listFiles);
    }

    DisposableStorage(Roots roots, long processStartedAt, DeleteFile deleteFile) {
        this(roots, processStartedAt, deleteFile, new Object(), File::listFiles);
    }

    DisposableStorage(Roots roots, long processStartedAt, DeleteFile deleteFile,
                      Object imageWriteLock, ListDirectory listDirectory) {
        this.roots = roots;
        this.processStartedAt = processStartedAt;
        this.deleteFile = deleteFile;
        this.imageWriteLock = imageWriteLock;
        this.listDirectory = listDirectory;
    }

    Snapshot snapshot(long now) {
        Scan scan = new Scan();
        SavedOfflineStorage saved = SavedOfflineStorage.read(roots.files);
        scan.complete = saved.readable();
        walk(scan, roots.files, "files", "", saved, now, 0);
        for (File root : roots.caches) walk(scan, root, "cache", "", saved, now, 0);
        for (File root : roots.externalFiles) walk(scan, root, "external", "", saved, now, 0);
        for (File root : roots.installedCode) walk(scan, root, "installed", "", saved, now, 0);
        for (File root : roots.runtimeCode) walk(scan, root, "runtime", "", saved, now, 0);
        for (File root : roots.retainedData) walk(scan, root, "retained", "", saved, now, 0);
        return new Snapshot(scan);
    }

    ClearResult clear(long now) {
        return remove(snapshot(now), now, 0L);
    }

    ClearResult pruneExpired(long now, long maxAgeMs) {
        return remove(snapshot(now), now, Math.max(1L, maxAgeMs));
    }

    // Revalidate the captured files so new, replaced or still-writing data is never claimed.
    ClearResult remove(Snapshot before, long now, long maxAgeMs) {
        long reclaimed = 0L;
        int deleted = 0;
        int failed = 0;
        long imageBytes = before.imageBytes;
        List<Entry> candidates = new ArrayList<>(before.entries);
        if (maxAgeMs > 0L) Collections.sort(candidates,
                (left, right) -> Long.compare(left.modified, right.modified));
        SavedOfflineStorage saved = SavedOfflineStorage.read(roots.files);
        for (Entry entry : candidates) {
            if (Thread.currentThread().isInterrupted()) break;
            if (maxAgeMs > 0L) {
                if (entry.kind != Kind.IMAGES && entry.kind != Kind.DETAILS) continue;
                if (entry.modified >= now - maxAgeMs
                        && (entry.kind != Kind.IMAGES || imageBytes <= MAX_IMAGE_BYTES)) continue;
            }
            long removed = deleteOne(entry, saved);
            if (removed == RETRY_SAVED) {
                saved = SavedOfflineStorage.read(roots.files);
                removed = deleteOne(entry, saved);
            }
            if (removed >= 0L) {
                reclaimed += removed;
                if (entry.kind == Kind.IMAGES) imageBytes -= removed;
                deleted++;
            } else if (removed == FAILED) failed++;
        }
        return new ClearResult(before, snapshot(now), reclaimed, deleted, failed);
    }

    private long deleteOne(Entry entry, SavedOfflineStorage saved) {
        // Never acquire an owner monitor under the lease lock. No batch I/O is inside either.
        if (entry.kind == Kind.IMAGES) {
            synchronized (this.imageWriteLock) {
                return deleteLeased(entry, saved);
            }
        }
        return deleteLeased(entry, saved);
    }

    private long deleteLeased(Entry entry, SavedOfflineStorage saved) {
        synchronized (StorageFileLeases.LOCK) {
            try {
                File file = entry.file;
                if (!unchanged(entry) || StorageFileLeases.inUse(file)) return SKIPPED;
                if (entry.kind == Kind.IMAGES || entry.kind == Kind.DETAILS) {
                    if (!saved.isCurrent()) return RETRY_SAVED;
                    if (entry.kind == Kind.IMAGES && saved.keepsImage(file.getName())) return SKIPPED;
                    if (entry.kind == Kind.DETAILS && saved.keepsDetail(file.getName())) return SKIPPED;
                }
                return deleteFile.delete(file) && !file.exists() ? entry.bytes : FAILED;
            } catch (IOException | SecurityException error) {
                return FAILED;
            }
        }
    }

    private boolean unchanged(Entry entry) throws IOException {
        return entry.file.getCanonicalFile().equals(entry.file)
                && entry.file.isFile() && entry.file.length() == entry.bytes
                && entry.file.lastModified() == entry.modified;
    }

    private void walk(Scan scan, File root, String owner, String path, SavedOfflineStorage saved,
                      long now, int depth) {
        if (root == null) return;
        try {
            File canonical = root.getCanonicalFile();
            if (depth > 0 && !canonical.equals(root.getAbsoluteFile())) {
                scan.complete = false;
                return;
            }
            if (!scan.seen.add(canonical) || !canonical.exists()) return;
            if (canonical.isFile()) {
                record(scan, canonical, owner, path, saved, now);
                return;
            }
            File[] children = this.listDirectory.list(canonical);
            if (children == null || depth >= 32) {
                scan.complete = false;
                return;
            }
            for (File child : children) {
                walk(scan, child, owner, path.isEmpty() ? child.getName()
                        : path + "/" + child.getName(), saved, now, depth + 1);
            }
        } catch (IOException | SecurityException error) {
            scan.complete = false;
        }
    }

    private void record(Scan scan, File file, String owner, String path, SavedOfflineStorage saved,
                        long now) throws IOException {
        Entry entry = new Entry(file, kind(owner, path));
        if (entry.kind == Kind.IMAGES) scan.images += entry.bytes;
        if (entry.kind == Kind.DETAILS) scan.details++;
        if (owner.equals("installed")) scan.installed += entry.bytes;
        else if (owner.equals("runtime") || entry.kind == Kind.CODE) scan.runtime += entry.bytes;
        else if (owner.equals("retained") || entry.kind == Kind.RETAINED || (entry.kind == Kind.IMAGES
                && saved.keepsImage(file.getName())) || (entry.kind == Kind.DETAILS
                && saved.keepsDetail(file.getName()))) scan.retained += entry.bytes;
        else if (entry.kind == Kind.UNKNOWN) scan.unknown += entry.bytes;
        else if (deferred(entry, now) || StorageFileLeases.inUse(file)) scan.deferred += entry.bytes;
        else {
            scan.disposable += entry.bytes;
            scan.entries.add(entry);
        }
    }

    private boolean deferred(Entry entry, long now) {
        String name = entry.file.getName();
        if (entry.modified <= 0L || entry.modified >= now) return true;
        if (entry.kind == Kind.DIAGNOSTICS) return entry.modified >= now - REPORT_GRACE_MS;
        if (entry.kind == Kind.REGION || entry.kind == Kind.UPDATES
                || entry.kind == Kind.DETAILS || name.endsWith(".tmp")) {
            return entry.modified >= processStartedAt;
        }
        if (entry.kind == Kind.IMAGES) return entry.modified > now - IMAGE_SETTLE_MS;
        return false;
    }

    private Kind kind(String owner, String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (owner.equals("files")) {
            if (path.startsWith("native_signer/")) return Kind.CODE;
            if (direct(path, "offline-cache/images/")
                    && IMAGE.matcher(stripTmp(name)).matches()) return Kind.IMAGES;
            if (direct(path, "offline-cache/details/")
                    && name.matches("[A-Za-z0-9._-]+[.]json")) return Kind.DETAILS;
            if (direct(path, "updates/") && UPDATE.matcher(name).matches()) return Kind.UPDATES;
            if (path.startsWith("offline-cache/saved-lists/")
                    || path.startsWith("crash-reports/")) return Kind.RETAINED;
            if (direct(path, "offline-cache/diagnostics/") || direct(path, "diagnostics/")) {
                return diagnosticKind(name);
            }
        } else if (owner.equals("cache")) {
            if (!path.contains("/") && REGION.matcher(name).matches()) return Kind.REGION;
            if (direct(path, "updates/") && UPDATE.matcher(name).matches()) return Kind.UPDATES;
        } else if (owner.equals("external")) {
            if (direct(path, "Download/heyboxlite/") && UPDATE.matcher(name).matches()) {
                return Kind.UPDATES;
            }
            if (direct(path, "diagnostics/")) return diagnosticKind(name);
        }
        return Kind.UNKNOWN;
    }

    private Kind diagnosticKind(String name) {
        if (REPORT.matcher(name).matches()) return Kind.DIAGNOSTICS;
        if (name.equals("events-session.log") || name.equals("events-previous-session.log")
                || name.equals("native-sign.log") || name.equals("crash-latest.log")
                || name.equals("crash-previous.log")
                || name.equals("heybox-lite-diagnostics-latest.txt")) return Kind.RETAINED;
        return Kind.UNKNOWN;
    }

    private static boolean direct(String path, String directory) {
        return path.startsWith(directory) && path.indexOf('/', directory.length()) < 0;
    }

    private static String stripTmp(String name) {
        return name.endsWith(".tmp") ? name.substring(0, name.length() - 4) : name;
    }
}
