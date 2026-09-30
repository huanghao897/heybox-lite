package com.ronan.heyboxlite;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** One badge for both inline replies and comment headers. */
final class AuthorBadgeDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int foreground;
    private final int background;

    AuthorBadgeDrawable(ThemeTokens tokens) {
        foreground = tokens.accent;
        background = tokens.softAccent();
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override public void draw(Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        paint.setColor(background);
        canvas.drawRoundRect(bounds, bounds.height() / 5f, bounds.height() / 5f, paint);
        paint.setColor(foreground);
        paint.setTextSize(bounds.height() * 0.68f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText("作者", bounds.centerX(),
                bounds.centerY() - (metrics.ascent + metrics.descent) / 2f, paint);
    }

    @Override public int getIntrinsicWidth() { return 28; }
    @Override public int getIntrinsicHeight() { return 15; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
