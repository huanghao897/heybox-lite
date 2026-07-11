package com.openzen.heyboxcommunity;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

/**
 * 行内图片 span：把 drawable 垂直居中到当前文字行中线（官方评论徽章/表情的对齐方式）。
 * 相比 ImageSpan.ALIGN_BASELINE（底边贴基线、整体偏上），这里以文字视觉中线为基准，
 * 徽章、Cy、表情能和名字、冒号、正文对齐。
 */
final class CenteredImageSpan extends ReplacementSpan {
    private final Drawable drawable;

    CenteredImageSpan(Drawable drawable) {
        this.drawable = drawable;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        Rect bounds = drawable.getBounds();
        if (fm != null) {
            Paint.FontMetricsInt textMetrics = paint.getFontMetricsInt();
            int center = (textMetrics.ascent + textMetrics.descent) / 2;
            int half = bounds.height() / 2;
            fm.ascent = Math.min(textMetrics.ascent, center - half);
            fm.top = fm.ascent;
            fm.descent = Math.max(textMetrics.descent, center + half);
            fm.bottom = fm.descent;
        }
        return bounds.width();
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        Paint.FontMetricsInt textMetrics = paint.getFontMetricsInt();
        int center = y + (textMetrics.ascent + textMetrics.descent) / 2;
        int transY = center - drawable.getBounds().height() / 2;
        canvas.save();
        canvas.translate(x, transY);
        drawable.draw(canvas);
        canvas.restore();
    }
}
