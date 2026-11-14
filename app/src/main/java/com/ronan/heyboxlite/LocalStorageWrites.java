package com.ronan.heyboxlite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/** File writes remain outside global monitors; leases make unlink vs write atomic. */
final class LocalStorageWrites {
    private static final AtomicLong SAVED_REVISION = new AtomicLong();

    private LocalStorageWrites() {}

    static long savedRevision() {
        return SAVED_REVISION.get();
    }

    static void write(File file, String value) {
        boolean savedManifest = file.getName().equals("watch-later.json");
        try {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(file)) {
                if (savedManifest) SAVED_REVISION.incrementAndGet();
                try (FileOutputStream output = new FileOutputStream(file, false)) {
                    output.write(bytes);
                } finally {
                    if (savedManifest) SAVED_REVISION.incrementAndGet();
                }
            }
        } catch (IOException | SecurityException ignored) {
        }
    }
}
