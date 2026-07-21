package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class NativeLibraryLoader {
    private static final Set<String> LOADED = new HashSet<>();
    private static final int BUFFER_SIZE = 8192;
    @SuppressLint("StaticFieldLeak")
    private static Context hostContext;

    private NativeLibraryLoader() {}

    public static synchronized void init(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        hostContext = app == null ? context : app;
    }

    public static synchronized void load(Context context, String name) {
        if (LOADED.contains(name)) return;
        Context app = resolveHostContext(context);
        String abi = selectedAbi();
        if (!"arm64-v8a".equals(abi)) {
            throw new UnsatisfiedLinkError("unsupported ABI for bundled native signer: " + abi);
        }
        String fileName = "lib" + name + ".so";
        String assetName = "native/" + abi + "/" + fileName;
        File outDir = nativeDir(app);
        File out = new File(outDir, fileName);
        try {
            LocalCache.appendNativeSignLog(app, "native loader extract package="
                    + app.getPackageName()
                    + " asset=" + assetName
                    + " dir=" + outDir.getAbsolutePath());
            copyAsset(app, assetName, out);
            System.load(out.getAbsolutePath());
            LOADED.add(name);
        } catch (UnsatisfiedLinkError error) {
            throw error;
        } catch (Throwable error) {
            UnsatisfiedLinkError linkError = new UnsatisfiedLinkError(
                    "load native signer failed: " + error.getClass().getSimpleName()
                            + ": " + String.valueOf(error.getMessage()));
            linkError.initCause(error);
            throw linkError;
        }
    }

    public static synchronized boolean tryLoadFromOfficialApk(Context context, String name) {
        if (LOADED.contains(name)) return true;
        if (context == null) return false;
        try {
            Context app = context.getApplicationContext();
            android.content.pm.PackageManager pm = app.getPackageManager();
            android.content.pm.ApplicationInfo info;
            try {
                info = pm.getApplicationInfo(OfficialContext.PACKAGE_NAME, 0);
            } catch (Throwable notFound) {
                return false;
            }
            String apkPath = info == null ? null : info.sourceDir;
            if (apkPath == null || apkPath.isEmpty()) return false;
            String abi = selectedAbi();
            String entryName = "lib/" + abi + "/lib" + name + ".so";
            File outDir = nativeDir(app);
            File outFile = new File(outDir, "official_lib" + name + ".so");
            LocalCache.appendNativeSignLog(app, "native loader extracting from official apk="
                    + apkPath + " entry=" + entryName);
            extractFromApk(apkPath, entryName, outFile);
            System.load(outFile.getAbsolutePath());
            LOADED.add(name);
            LocalCache.appendNativeSignLog(app, "native loader loaded official " + name);
            return true;
        } catch (Throwable error) {
            LocalCache.appendNativeSignLog(context.getApplicationContext(),
                    "native loader official apk extract failed for " + name + ": "
                            + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
            return false;
        }
    }

    private static void extractFromApk(String apkPath, String entryName, File outFile) throws Exception {
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("cannot create native dir");
        }
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkPath)) {
            java.util.zip.ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) throw new IllegalStateException("entry not found: " + entryName);
            try (InputStream input = zip.getInputStream(entry);
                 FileOutputStream output = new FileOutputStream(outFile, false)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            }
        }
        outFile.setReadable(true, true);
        outFile.setExecutable(true, true);
    }

    private static File nativeDir(Context app) {
        try {
            return app.getDir("native_signer", Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            File files = app.getFilesDir();
            return new File(files == null ? new File(".") : files, "native_signer");
        }
    }

    private static Context resolveHostContext(Context context) {
        if (hostContext != null) return hostContext;
        if (context == null) {
            throw new UnsatisfiedLinkError("missing context for native signer");
        }
        Context app = context.getApplicationContext();
        Context resolved = app == null ? context : app;
        String packageName = resolved.getPackageName();
        if (OfficialContext.PACKAGE_NAME.equals(packageName)) {
            throw new UnsatisfiedLinkError("host context was not initialized");
        }
        hostContext = resolved;
        return resolved;
    }

    private static void copyAsset(Context context, String assetName, File out) throws Exception {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("cannot create native dir");
        }
        try (InputStream input = context.getAssets().open(assetName);
             FileOutputStream output = new FileOutputStream(out, false)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
        }
        boolean readable = out.setReadable(true, true) || out.canRead();
        boolean executable = out.setExecutable(true, true) || out.canExecute();
        if (!readable || !executable) {
            throw new IllegalStateException("cannot secure native library permissions");
        }
    }

    @SuppressWarnings("deprecation")
    private static String selectedAbi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && Build.SUPPORTED_ABIS != null
                && Build.SUPPORTED_ABIS.length > 0) {
            return cleanAbi(Build.SUPPORTED_ABIS[0]);
        }
        return cleanAbi(Build.CPU_ABI);
    }

    private static String cleanAbi(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
