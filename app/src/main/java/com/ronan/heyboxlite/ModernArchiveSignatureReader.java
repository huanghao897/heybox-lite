package com.ronan.heyboxlite;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;

@TargetApi(Build.VERSION_CODES.P)
final class ModernArchiveSignatureReader {
    private ModernArchiveSignatureReader() {}

    static PackageInfo read(Context context, File apk) {
        return context.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
    }

    static Signature[] signatures(PackageInfo info) {
        return info == null || info.signingInfo == null
                ? null : info.signingInfo.getApkContentsSigners();
    }
}
