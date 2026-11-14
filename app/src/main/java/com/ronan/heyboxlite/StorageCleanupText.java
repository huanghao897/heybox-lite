package com.ronan.heyboxlite;

import java.util.Locale;

/** Short result text shared by native and Compose settings callers. */
final class StorageCleanupText {
    private StorageCleanupText() {}

    static String format(DisposableStorage.ClearResult result) {
        String message = "\u5df2\u6e05\u7406 " + size(result.reclaimedBytes)
                + "\uff0c\u5269\u4f59 " + size(result.after.disposableBytes);
        if (result.failedFiles > 0) message += "\uff08\u90e8\u5206\u5931\u8d25\uff09";
        else if (!result.after.complete) message += "\uff08\u7edf\u8ba1\u4e0d\u5b8c\u6574\uff09";
        return message;
    }

    static String size(long bytes) {
        if (bytes < 1024L) return Math.max(0L, bytes) + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }
}
