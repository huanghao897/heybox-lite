package com.openzen.heyboxcommunity;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

final class OfficialAppVerifier {
    private OfficialAppVerifier() {}

    static boolean isOfficialPackageTrusted(Context context) {
        return packageSignatureMatches(context, OfficialContext.PACKAGE_NAME);
    }

    static boolean isProviderTrusted(Context context, Uri uri) {
        if (context == null || uri == null || uri.getAuthority() == null) return false;
        try {
            ProviderInfo provider = context.getPackageManager()
                    .resolveContentProvider(uri.getAuthority(), 0);
            return provider != null
                    && OfficialContext.PACKAGE_NAME.equals(provider.packageName)
                    && packageSignatureMatches(context, provider.packageName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean packageSignatureMatches(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return false;
        try {
            Signature[] actual = signaturesForPackage(context, packageName);
            if (actual == null || actual.length == 0) return false;
            Set<String> expected = digestSet(OfficialContext.officialSignatures());
            for (Signature signature : actual) {
                if (!expected.contains(digest(signature))) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Signature[] signaturesForPackage(Context context, String packageName)
            throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return currentSignatures(context, packageName);
        }
        return legacySignatures(context, packageName);
    }

    @TargetApi(Build.VERSION_CODES.P)
    private static Signature[] currentSignatures(Context context, String packageName)
            throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(
                packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        if (info.signingInfo == null) return null;
        return info.signingInfo.hasMultipleSigners()
                ? info.signingInfo.getApkContentsSigners()
                : info.signingInfo.getSigningCertificateHistory();
    }

    @SuppressWarnings("deprecation")
    private static Signature[] legacySignatures(Context context, String packageName)
            throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(
                packageName, PackageManager.GET_SIGNATURES);
        return info.signatures;
    }

    private static Set<String> digestSet(Signature[] signatures) throws Exception {
        Set<String> values = new HashSet<>();
        if (signatures == null) return values;
        for (Signature signature : signatures) values.add(digest(signature));
        return values;
    }

    private static String digest(Signature signature) throws Exception {
        byte[] value = MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray());
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) {
            int v = b & 0xff;
            if (v < 16) out.append('0');
            out.append(Integer.toHexString(v));
        }
        return out.toString();
    }
}
