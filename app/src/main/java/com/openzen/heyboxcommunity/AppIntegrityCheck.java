package com.openzen.heyboxcommunity;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
final class AppIntegrityCheck {
    private static final byte[] RELEASE_CERT = {
            123,22,56,-125,-43,112,64,-123,5,-40,32,23,79,-71,86,84,
            113,-17,-34,-115,-122,53,8,-3,57,14,79,78,-79,100,94,-72
    };

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
            return MessageDigest.isEqual(RELEASE_CERT, digest);
        } catch (Exception ignored) {
            return false;
        }
    }

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
}
