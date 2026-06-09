package com.openzen.heyboxcommunity;

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

public final class DiagnosticsProvider extends ContentProvider {
    static final String AUTHORITY = "com.openzen.heyboxcommunity.diagnostics";

    @Override public boolean onCreate() {
        return true;
    }

    @Override public String getType(Uri uri) {
        return "text/plain";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = fileFor(uri);
        String[] columns = projection == null ? new String[]{
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        } : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                values[i] = file == null ? "" : file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                values[i] = file == null ? 0L : file.length();
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
        if (file == null || !file.isFile()) throw new FileNotFoundException();
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

    static Uri uriFor(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(file == null ? "" : file.getName())
                .build();
    }

    private File fileFor(Uri uri) {
        if (getContext() == null || uri == null) return null;
        String name = uri.getLastPathSegment();
        if (TextUtils.isEmpty(name) || name.contains("/") || name.contains("\\")) {
            return null;
        }
        File external = getContext().getExternalFilesDir(null);
        File diagnosticsDir = new File(external == null
                ? new File(getContext().getFilesDir(), "offline-cache")
                : external, "diagnostics");
        try {
            File root = diagnosticsDir.getCanonicalFile();
            File file = new File(root, name).getCanonicalFile();
            if (!file.getPath().startsWith(root.getPath())) return null;
            if (!file.getName().startsWith("heybox-lite-diagnostics-")
                    || !file.getName().endsWith(".txt")) {
                return null;
            }
            return file;
        } catch (Exception ignored) {
            return null;
        }
    }
}
