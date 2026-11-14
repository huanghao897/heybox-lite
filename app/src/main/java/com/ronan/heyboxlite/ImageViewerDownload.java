package com.ronan.heyboxlite;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ImageViewerDownload {
    static final int REQUEST_PERMISSION = 41;
    private ImageViewerDownload() {}

    static void save(Activity activity, String url) {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
            return;
        }
        String source = ImageLoader.originalUrl(url);
        if (source.isEmpty()) {
            Toast.makeText(activity, "图片地址不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Activity.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Download service unavailable");
            String extension = extension(source);
            String name = "heybox-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                    .format(new Date()) + "." + extension;
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(source));
            request.setTitle("heybox Lite 图片");
            request.setDescription("正在保存到相册");
            request.setMimeType("jpg".equals(extension) || "jpeg".equals(extension)
                    ? "image/jpeg" : "image/" + extension);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "heybox Lite/" + name);
            manager.enqueue(request);
            Toast.makeText(activity, "正在保存到相册", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            Toast.makeText(activity, "保存图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    private static String extension(String url) {
        String value = url.toLowerCase(Locale.ROOT);
        for (String type : new String[]{"gif", "png", "webp", "jpeg"}) {
            if (value.endsWith("." + type)) return type;
        }
        return "jpg";
    }
}
