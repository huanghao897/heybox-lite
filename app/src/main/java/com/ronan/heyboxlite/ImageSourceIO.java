package com.ronan.heyboxlite;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Bounded compressed input. Long images stream to a private file, not two heap byte arrays. */
final class ImageSourceIO {
    private ImageSourceIO() {}

    static byte[] download(String url, int maxBytes) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(48 * 1024)) {
            return copy(url, maxBytes, output) ? output.toByteArray() : null;
        } catch (Exception | OutOfMemoryError error) {
            return null;
        }
    }

    static File downloadFile(String url, int maxBytes, File directory) {
        if (ImageWorkToken.stopped() || directory == null) return null;
        File file = null;
        boolean complete = false;
        try {
            file = File.createTempFile("long-image-", ".img", directory);
            try (FileOutputStream output = new FileOutputStream(file)) {
                complete = copy(url, maxBytes, output);
            }
            return complete ? file : null;
        } catch (Exception error) {
            return null;
        } finally {
            if (!complete && file != null) file.delete();
        }
    }

    private static boolean copy(String url, int limit, OutputStream output) throws Exception {
        if (ImageWorkToken.stopped()) return false;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        ImageWorkToken.abortWith(connection::disconnect);
        try {
            if (ImageWorkToken.stopped()) return false;
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setUseCaches(true);
            HeaderProvider.applyPublic(connection);
            int status = connection.getResponseCode();
            if (ImageWorkToken.stopped() || status < 200 || status >= 300
                    || connection.getContentLength() > limit) return false;
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[8192];
                int total = 0;
                while (!ImageWorkToken.stopped()) {
                    int count = input.read(buffer);
                    if (count < 0) return !ImageWorkToken.stopped();
                    if (count > limit - total) return false;
                    output.write(buffer, 0, count);
                    total += count;
                }
                return false;
            }
        } finally {
            ImageWorkToken.abortWith(null);
            connection.disconnect();
        }
    }
}
