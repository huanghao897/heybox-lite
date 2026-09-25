package com.ronan.heyboxlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

/** A small process-safe outbox. Failed delivery is never acknowledged. */
final class CrashReportStore {
    interface Sender { boolean send(String report) throws IOException; }

    static final int MAX_REPORTS = 8;
    static final int MAX_REPORT_BYTES = 28 * 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final File directory;

    CrashReportStore(File directory) { this.directory = directory; }

    void enqueue(String report) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create crash outbox");
        }
        String value = CrashText.limit(DiagnosticSanitizer.redact(report), MAX_REPORT_BYTES);
        File pending = new File(directory, System.currentTimeMillis() + "-"
                + UUID.randomUUID() + ".pending");
        File temporary = new File(directory, pending.getName() + ".tmp");
        write(temporary, value);
        if (!temporary.renameTo(pending)) {
            temporary.delete();
            throw new IOException("Cannot persist crash report");
        }
        File latest = new File(directory, "latest.log");
        if (latest.isFile()) write(new File(directory, "previous.log"), read(latest));
        write(latest, value);
        List<FileSnapshot> pendingFiles = pending();
        for (int i = 0; i < pendingFiles.size() - MAX_REPORTS; i++) {
            pendingFiles.get(i).file.delete();
        }
    }

    List<FileSnapshot> pending() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".pending"));
        List<FileSnapshot> snapshots = FileSnapshot.captureFiles(files);
        FileSnapshot.sortOldestFirst(snapshots);
        return snapshots;
    }

    String latest() { return read(new File(directory, "latest.log")); }
    String previous() { return read(new File(directory, "previous.log")); }

    boolean drain(Sender sender) throws IOException {
        if (!directory.isDirectory()) return true;
        try (RandomAccessFile lockFile = new RandomAccessFile(
                new File(directory, "upload.lock"), "rw")) {
            FileLock lock;
            try {
                lock = lockFile.getChannel().tryLock();
            } catch (OverlappingFileLockException busy) {
                return false;
            }
            if (lock == null) return false;
            try {
                for (FileSnapshot entry : pending()) {
                    String report = read(entry.file);
                    if (report.isEmpty() || !sender.send(report)) return false;
                    if (!entry.file.delete()) return false;
                }
                return true;
            } finally {
                lock.release();
            }
        }
    }

    static String read(File file) {
        if (!file.isFile() || file.length() > 96 * 1024L) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            int count;
            while (offset < bytes.length
                    && (count = input.read(bytes, offset, bytes.length - offset)) > 0) {
                offset += count;
            }
            return new String(bytes, 0, offset, UTF_8);
        } catch (IOException | SecurityException ignored) {
            return "";
        }
    }

    private static void write(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(UTF_8));
            output.getFD().sync();
        }
    }
}
