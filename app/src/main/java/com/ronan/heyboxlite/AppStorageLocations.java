package com.ronan.heyboxlite;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Mirrors the storage owners' actual paths, including unavailable/secondary external volumes. */
final class AppStorageLocations {
    private AppStorageLocations() {}

    static DisposableStorage.Roots read(Context context) {
        File files = context.getFilesDir();
        List<File> caches = new ArrayList<>();
        caches.add(context.getCacheDir());
        caches.addAll(Arrays.asList(context.getExternalCacheDirs()));
        List<File> external = new ArrayList<>(Arrays.asList(context.getExternalFilesDirs(null)));
        ApplicationInfo info = context.getApplicationInfo();
        List<File> installed = new ArrayList<>();
        add(installed, info.sourceDir);
        if (info.splitSourceDirs != null) {
            for (String split : info.splitSourceDirs) add(installed, split);
        }
        add(installed, info.nativeLibraryDir);
        File data = files.getParentFile();
        List<File> runtime = Arrays.asList(new File(data, "app_native_signer"),
                context.getCodeCacheDir());
        List<File> retained = Arrays.asList(new File(data, "shared_prefs"),
                new File(data, "databases"), context.getNoBackupFilesDir());
        return new DisposableStorage.Roots(files, caches, external, installed, runtime, retained);
    }

    private static void add(List<File> paths, String path) {
        if (path != null && !path.isEmpty()) paths.add(new File(path));
    }
}
