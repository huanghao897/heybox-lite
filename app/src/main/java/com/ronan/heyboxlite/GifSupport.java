package com.ronan.heyboxlite;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * 详情页 GIF 动图播放。API 28+ 用系统 AnimatedImageDrawable（硬件加速），
 * 低版本用 Movie 软件渲染。超大 GIF 直接放弃，保持静态缩略图，防手表 OOM。
 */
@SuppressWarnings("deprecation")
final class GifSupport {
    static final int MAX_GIF_BYTES = 8 * 1024 * 1024;
    private static final int MAX_MOVIE_SIDE = 1280;
    private static final int FRAME_DELAY_MS = 33;

    private GifSupport() {}

    static boolean isGifUrl(String sourceUrl) {
        String original = ImageLoader.originalUrl(sourceUrl);
        return original.toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    /** 后台线程解码，返回可直接 setImageDrawable 的对象；失败返回 null。 */
    static Drawable decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_GIF_BYTES) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            Drawable modern = decodeModern(bytes);
            if (modern != null) return modern;
        }
        return decodeMovie(bytes);
    }

    /** 主线程：装载并开始播放。Movie 路径需要软件层。 */
    static void apply(ImageView view, Drawable drawable) {
        if (view == null || drawable == null) return;
        if (drawable instanceof MovieDrawable) {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        view.setImageDrawable(drawable);
        if (Build.VERSION.SDK_INT >= 28 && drawable instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) drawable).start();
        }
    }

    @TargetApi(28)
    private static Drawable decodeModern(byte[] bytes) {
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(ByteBuffer.wrap(bytes));
            return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int side = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                if (side > MAX_MOVIE_SIDE) {
                    float scale = MAX_MOVIE_SIDE / (float) side;
                    decoder.setTargetSize(
                            Math.max(1, Math.round(info.getSize().getWidth() * scale)),
                            Math.max(1, Math.round(info.getSize().getHeight() * scale)));
                }
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable decodeMovie(byte[] bytes) {
        try {
            Movie movie = Movie.decodeByteArray(bytes, 0, bytes.length);
            if (movie == null || movie.width() <= 0 || movie.height() <= 0
                    || movie.duration() <= 0
                    || Math.max(movie.width(), movie.height()) > MAX_MOVIE_SIDE) {
                return null;
            }
            return new MovieDrawable(movie);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class MovieDrawable extends Drawable {
        private final Movie movie;
        private final long startedAt = SystemClock.uptimeMillis();
        private final Runnable tick = this::invalidateSelf;

        MovieDrawable(Movie movie) {
            this.movie = movie;
        }

        @Override
        public void draw(Canvas canvas) {
            int duration = Math.max(1, movie.duration());
            movie.setTime((int) ((SystemClock.uptimeMillis() - this.startedAt) % duration));
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / (float) movie.width(),
                    bounds.height() / (float) movie.height());
            movie.draw(canvas, 0.0f, 0.0f);
            canvas.restoreToCount(save);
            scheduleSelf(this.tick, SystemClock.uptimeMillis() + FRAME_DELAY_MS);
        }

        @Override
        public int getIntrinsicWidth() {
            return movie.width();
        }

        @Override
        public int getIntrinsicHeight() {
            return movie.height();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
