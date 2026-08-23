package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class ImagePreviewStore {
    static final class Preview {
        final String url;
        final WeakReference<Bitmap> bitmap;
        final WeakReference<ImageView> source;

        Preview(String url, Bitmap bitmap, ImageView source) {
            this.url = url == null ? "" : url;
            this.bitmap = new WeakReference<>(bitmap);
            this.source = new WeakReference<>(source);
        }
    }

    private static final int MAX_PENDING = 4;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final LinkedHashMap<Long, Preview> PENDING = new LinkedHashMap<>();

    private ImagePreviewStore() {}

    static synchronized long prepare(String url, Bitmap bitmap, ImageView source) {
        long id = NEXT_ID.incrementAndGet();
        PENDING.put(id, new Preview(url, bitmap, source));
        while (PENDING.size() > MAX_PENDING) {
            Iterator<Map.Entry<Long, Preview>> iterator = PENDING.entrySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        return id;
    }

    static synchronized Preview claim(long id) {
        return id <= 0L ? null : PENDING.remove(id);
    }
}
