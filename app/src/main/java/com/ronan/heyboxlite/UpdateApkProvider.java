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
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath(file == null ? "" : file.getName())
                .build();
    }

    private File fileFor(Uri uri) {
        if (getContext() == null || uri == null) return null;
        String name = uri.getLastPathSegment();
        if (TextUtils.isEmpty(name) || name.contains("/") || name.contains("\\")
                || name.contains("..") || !isApkName(name)) {
            return null;
        }
        try {
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

    private boolean isApkName(String name) {
        return name.length() <= 160
                && name.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private File existingFile(File root, String name) throws IOException {
        File file = fileUnder(root, name);
        return file != null && file.isFile() && file.canRead() ? file : null;
    }

    private File fileUnder(File root, String name) throws IOException {
        File canonicalRoot = root.getCanonicalFile();
        File file = new File(canonicalRoot, name).getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        if (!rootPath.endsWith(File.separator)) rootPath += File.separator;
        return file.getPath().startsWith(rootPath) ? file : null;
    }
}
