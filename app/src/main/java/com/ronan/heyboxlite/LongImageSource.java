package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

/** Owns the region decoder and any temporary compressed file; serializes close vs decode. */
final class LongImageSource {
    final int width;
    final int height;
    private final BitmapRegionDecoder decoder;
    private final File temporary;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed;
    private boolean disposed;

    private LongImageSource(BitmapRegionDecoder decoder, File temporary) {
        this.decoder = decoder;
        this.temporary = temporary;
        width = decoder.getWidth();
        height = decoder.getHeight();
    }

    static LongImageSource open(String url, File cacheDirectory) {
        if (ImageWorkToken.stopped()) return null;
        File file = ImageDiskCache.exactFile(url);
        File temporary = null;
        if (file == null || !file.isFile() || file.length() <= 0
                || file.length() > ImageLoader.MAX_DECODE_BYTES) {
            if (!ImageLoader.isNetworkConnected()) return null;
            temporary = ImageSourceIO.downloadFile(url, ImageLoader.MAX_DECODE_BYTES, cacheDirectory);
            file = temporary;
        }
        BitmapRegionDecoder decoder = null;
        try {
            if (file == null || ImageWorkToken.stopped()) return null;
            decoder = BitmapRegionDecoder.newInstance(file.getAbsolutePath(), false);
            if (decoder == null || ImageWorkToken.stopped() || decoder.getWidth() <= 0
                    || decoder.getHeight() <= decoder.getWidth() * 3L) return null;
            LongImageSource source = new LongImageSource(decoder, temporary);
            decoder = null;
            temporary = null;
            return source;
        } catch (Exception | OutOfMemoryError error) {
            return null;
        } finally {
            if (decoder != null) decoder.recycle();
            if (temporary != null) temporary.delete();
        }
    }

    Bitmap decode(Rect rect, int sample) {
        lock.lock();
        try {
            if (closed || ImageWorkToken.stopped()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return decoder.decodeRegion(rect, options);
        } finally {
            if (closed) dispose();
            lock.unlock();
        }
    }

    void close() {
        closed = true;
        if (lock.tryLock()) {
            try { dispose(); } finally { lock.unlock(); }
        }
    }

    private void dispose() {
        if (!disposed) {
            disposed = true;
            decoder.recycle();
            if (temporary != null) temporary.delete();
        }
    }
}
