package com.ronan.heyboxlite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

public final class UpdateApkProvider extends ContentProvider {
    private static final String AUTHORITY_SUFFIX = ".updateapk";
    private static final String MIME_APK = "application/vnd.android.package-archive";

    @Override public boolean onCreate() {
        return true;
    }

    @Override public String getType(Uri uri) {
        return MIME_APK;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = fileFor(uri);
        String[] columns = projection == null ? new String[]{
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        } : projection;
        if (file == null) return new MatrixCursor(columns, 0);
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                values[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                values[i] = file.length();
            } else {
                values[i] = null;
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        File file = fileFor(uri);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("更新 APK 不存在或已失效");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        return 0;
    }

    static Uri uriFor(android.content.Context context, File file) {
        Uri.Builder builder = new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX);
        String location = locationFor(context, file);
        if (!location.isEmpty()) builder.appendPath(location);
        builder.appendPath(file == null ? "" : file.getName());
        return builder.build();
    }

    private File fileFor(Uri uri) {
        if (getContext() == null || uri == null) return null;
        java.util.List<String> segments = uri.getPathSegments();
        if (segments == null || segments.isEmpty() || segments.size() > 2) return null;
        String name = segments.get(segments.size() - 1);
        if (TextUtils.isEmpty(name) || name.contains("/") || name.contains("\\")
                || name.contains("..") || !isApkName(name)) {
            return null;
        }
        try {
            if (segments.size() == 2) {
                File root = rootFor(segments.get(0));
                return root == null ? null : existingFile(root, name);
            }

            // Keep accepting one-segment URIs from releases already installed.
            File internalFile = existingFile(
                    new File(getContext().getFilesDir(), "updates"), name);
            if (internalFile != null) return internalFile;

            File cacheFile = existingFile(new File(getContext().getCacheDir(), "updates"), name);
            if (cacheFile != null) return cacheFile;

            File externalRoot = getContext().getExternalFilesDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (externalRoot != null) {
                File externalFile = existingFile(new File(externalRoot, "heyboxlite"), name);
                if (externalFile != null) return externalFile;
            }
            return null;
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private File rootFor(String location) {
        if ("files".equals(location)) {
            return new File(getContext().getFilesDir(), "updates");
        }
        if ("cache".equals(location)) {
            return new File(getContext().getCacheDir(), "updates");
        }
        if ("external".equals(location)) {
            File externalRoot = getContext().getExternalFilesDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            return externalRoot == null ? null : new File(externalRoot, "heyboxlite");
        }
        return null;
    }

    private static String locationFor(android.content.Context context, File file) {
        if (context == null || file == null) return "";
        try {
            File candidate = file.getCanonicalFile();
            if (under(new File(context.getFilesDir(), "updates"), candidate)) return "files";
            if (under(new File(context.getCacheDir(), "updates"), candidate)) return "cache";
            File externalRoot = context.getExternalFilesDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (externalRoot != null
                    && under(new File(externalRoot, "heyboxlite"), candidate)) {
                return "external";
            }
        } catch (IOException | SecurityException ignored) {
            // Fall back to the legacy one-segment URI and let fileFor search known roots.
        }
        return "";
    }

    private static boolean under(File root, File candidate) throws IOException {
        String rootPath = root.getCanonicalFile().getPath();
        if (!rootPath.endsWith(File.separator)) rootPath += File.separator;
        return candidate.getPath().startsWith(rootPath);
    }

    private boolean isApkName(String name) {
        return name.length() <= 160
                && name.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private File existingFile(File root, String name) throws IOException {
        File file = fileUnder(root, name);
        return file != null && file.isFile() && file.canRead() ? file : null;
    }

    private File fileUnder(File root, String name) throws IOException {
        if (root == null) return null;
        File canonicalRoot = root.getCanonicalFile();
        File file = new File(canonicalRoot, name).getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        if (!rootPath.endsWith(File.separator)) rootPath += File.separator;
        return file.getPath().startsWith(rootPath) ? file : null;
    }
}
