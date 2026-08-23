package com.ronan.heyboxlite;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

final class UpdateApkVerifier {
    private UpdateApkVerifier() {}

    static boolean isTrusted(Context context, File apk) {
        if (context == null || apk == null || !apk.isFile()) return false;
        try {
            PackageInfo info = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? ModernArchiveSignatureReader.read(context, apk)
                    : legacyPackageInfo(context, apk);
            if (info == null || !BuildConfig.APPLICATION_ID.equals(info.packageName)) return false;
            Signature[] signatures = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? ModernArchiveSignatureReader.signatures(info) : info.signatures;
            if (signatures == null || signatures.length != 1) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signatures[0].toByteArray());
            return BuildConfig.UPDATE_CERT_SHA256.equals(hex(digest));
        } catch (Exception error) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo legacyPackageInfo(Context context, File apk) {
        return context.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.US, "%02x", item & 0xff));
        return value.toString();
    }
}
