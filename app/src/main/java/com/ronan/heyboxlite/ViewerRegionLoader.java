package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;

/** Compressed screen-width long images use the same CDN transform as content previews. */
final class ViewerRegionLoader {
    private static final ImageTaskExecutor WORK = new ImageTaskExecutor(1, 8);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ViewerRegionLoader() {}

    static ImageLoadRequest open(Context context, String source, int targetPx, boolean original,
                                 ImageLoadRequest.Result<LongImageSource> callback) {
        String url = url(source, targetPx, original);
        File temporary = context.getApplicationContext().getCacheDir();
        return ImageLoadRequest.work(WORK, MAIN,
                () -> url.isEmpty() ? null : LongImageSource.open(url, temporary),
                callback, result -> { if (result != null) result.close(); });
    }

    static String url(String source, int targetPx, boolean original) {
        String url = ImageLoader.originalUrl(source);
        if (url.isEmpty() || original) return url;
        int width = Math.max(320, Math.min(1440, targetPx));
        return url + "?imageMogr2/auto-orient/ignore-error/1/thumbnail/" + width + "x/format/jpg";
    }
}
