package com.ronan.heyboxlite;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.P)
final class ModernSignatureReader {
    private ModernSignatureReader() {}

    static Signature[] read(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
    }
}
