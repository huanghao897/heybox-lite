package com.openzen.heyboxcommunity;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class UpdateDownloadClient {
    interface ProgressListener {
        void onProgress(int percent, boolean indeterminate);
    }

    private static final int MIN_APK_BYTES = 128 * 1024;

    private UpdateDownloadClient() {}

    static File download(Context context, String url, String version,
                         ProgressListener listener) throws Exception {
        HttpURLConnection connection = null;
        File output = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive,*/*");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + version);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("下载失败，HTTP " + status);
            }

            File directory = new File(context.getCacheDir(), "updates");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("无法创建更新缓存目录");
            }
            output = new File(directory, "heybox-Lite-update-" + version + "-"
                    + System.currentTimeMillis() + ".apk");

            int length = connection.getContentLength();
            long written = copy(connection, output, length, listener);
            if (written < MIN_APK_BYTES) {
                throw new IllegalStateException("下载内容异常，未得到有效 APK");
            }
            return output;
        } catch (Exception error) {
            if (output != null && output.exists()) output.delete();
            throw error;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static long copy(HttpURLConnection connection, File output, int length,
                             ProgressListener listener) throws Exception {
        long written = 0;
        byte[] buffer = new byte[16 * 1024];
        try (InputStream input = connection.getInputStream();
             FileOutputStream file = new FileOutputStream(output)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                file.write(buffer, 0, count);
                written += count;
                if (listener != null) {
                    int percent = length <= 0 ? 0
                            : Math.max(0, Math.min(100, (int) (written * 100 / length)));
                    listener.onProgress(percent, length <= 0);
                }
            }
        }
        return written;
    }
}
