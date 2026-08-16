package com.ronan.heyboxlite;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class FileSnapshot {
    private static final Comparator<FileSnapshot> OLDEST_FIRST =
            new Comparator<FileSnapshot>() {
                @Override
                public int compare(FileSnapshot left, FileSnapshot right) {
                    if (left.lastModified < right.lastModified) return -1;
                    if (left.lastModified > right.lastModified) return 1;
                    return left.file.getAbsolutePath().compareTo(
                            right.file.getAbsolutePath());
                }
            };

    final File file;
    final long lastModified;
    final long length;

    private FileSnapshot(File file) {
        this.file = file;
        this.lastModified = file.lastModified();
        this.length = file.length();
    }

    static List<FileSnapshot> captureFiles(File[] files) {
        return capture(files, true);
    }

    static List<FileSnapshot> captureAll(File[] files) {
        return capture(files, false);
    }

    private static List<FileSnapshot> capture(File[] files, boolean regularFilesOnly) {
        List<FileSnapshot> snapshots = new ArrayList<>();
        if (files == null) return snapshots;
        for (File file : files) {
            if (regularFilesOnly && !file.isFile()) continue;
            snapshots.add(new FileSnapshot(file));
        }
        return snapshots;
    }

    static void sortOldestFirst(List<FileSnapshot> snapshots) {
        Collections.sort(snapshots, OLDEST_FIRST);
    }
}
