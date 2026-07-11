package com.openzen.heyboxcommunity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

final class DiagnosticsExporter {
    private static final String DIRECTORY = "heyboxlite";

    private DiagnosticsExporter() {}

    static boolean share(Activity activity, File file) {
        if (activity == null || file == null) return false;
        Uri uri = DiagnosticsProvider.uriFor(file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            share.setClipData(ClipData.newUri(
                    activity.getContentResolver(), file.getName(), uri));
        }
        share.putExtra(Intent.EXTRA_SUBJECT, "heybox Lite diagnostics");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_TEXT,
                "heybox Lite diagnostics txt\nTXT file: " + file.getName());
        try {
            activity.startActivity(Intent.createChooser(share, "分享诊断日志"));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static String save(Activity activity, String fileName, String diagnostics) {
        if (activity == null || fileName == null || fileName.isEmpty()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = createDownloadsDocument(activity, fileName);
            if (uri != null && write(activity, uri, diagnostics)) {
                return "Download/" + DIRECTORY + "/" + fileName;
            }
        }
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        File file = new File(new File(downloads, DIRECTORY), fileName);
        return write(file, diagnostics) ? file.getAbsolutePath() : null;
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private static Uri createDownloadsDocument(Activity activity, String fileName) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + DIRECTORY);
            return activity.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean write(Activity activity, Uri uri, String value) {
        try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
            if (output == null) return false;
            output.write(text(value));
            output.flush();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean write(File file, String value) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            try (OutputStream output = new FileOutputStream(file, false)) {
                output.write(text(value));
                output.flush();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[] text(String value) throws Exception {
        return (value == null ? "" : value).getBytes("UTF-8");
    }
}
