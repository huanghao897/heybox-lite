package com.ronan.heyboxlite;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates source-file ownership with deletion without closing a viewer's decoder. */
final class StorageFileLeases {
    static final Object LOCK = new Object();
    private static final Map<String, Integer> USERS = new ConcurrentHashMap<>();

    private StorageFileLeases() {}

    static Lease acquire(File file) throws IOException {
        String path = file.getCanonicalPath();
        synchronized (LOCK) {
            Integer count = USERS.get(path);
            USERS.put(path, count == null ? 1 : count + 1);
        }
        return new Lease(path);
    }

    static boolean inUse(File file) throws IOException {
        return USERS.containsKey(file.getCanonicalPath());
    }

    static final class Lease implements AutoCloseable {
        private final String path;
        private boolean closed;

        private Lease(String path) {
            this.path = path;
        }

        @Override public void close() {
            synchronized (LOCK) {
                if (closed) return;
                closed = true;
                int count = USERS.get(path);
                if (count == 1) USERS.remove(path);
                else USERS.put(path, count - 1);
            }
        }
    }
}
