package com.openzen.heyboxcommunity;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
final class AppIntegrityCheck {
    private AppIntegrityCheck() {}

    static boolean isTrusted(Context context) {
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            return true;
        }
        try {
            Signature[] signatures = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? currentSignatures(context) : legacySignatures(context);
            if (signatures == null || signatures.length != 1) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signatures[0].toByteArray());
            return BuildConfig.RELEASE_CERT_SHA256.equals(hex(digest));
        } catch (Exception ignored) {
            return true;
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.P)
    private static Signature[] currentSignatures(Context context)
            throws PackageManager.NameNotFoundException {
        return ModernSignatureReader.read(context);
    }

    @SuppressWarnings("deprecation")
    private static Signature[] legacySignatures(Context context)
            throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNATURES);
        return info.signatures;
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v < 16) value.append('0');
            value.append(Integer.toHexString(v));
        }
        return value.toString();
    }
}
