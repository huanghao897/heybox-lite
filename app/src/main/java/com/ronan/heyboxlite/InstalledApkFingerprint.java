package com.ronan.heyboxlite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hash the installed APK, not the last download the user clicked. Worker thread only. */
final class InstalledApkFingerprint {
    private static String cachedPath = "";
    private static long cachedSize;
    private static long cachedModified;
    private static String cachedDigest = "";

    private InstalledApkFingerprint() {}

    static synchronized String read(File apk) throws IOException {
        String path = apk.getAbsolutePath();
        long size = apk.length();
        long modified = apk.lastModified();
        if (path.equals(cachedPath) && size == cachedSize && modified == cachedModified) {
            return cachedDigest;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        try (FileInputStream input = new FileInputStream(apk)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        cachedPath = path;
        cachedSize = size;
        cachedModified = modified;
        cachedDigest = hex.toString();
        return cachedDigest;
    }
}
