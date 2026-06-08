package com.openzen.heyboxcommunity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

final class LoadingSpinnerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int color = 0xffffffff;
    private boolean spinning = true;

    LoadingSpinnerView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setColor(int value) {
        color = value;
        invalidate();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        spinning = visibility == VISIBLE;
        if (spinning) invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        spinning = getVisibility() == VISIBLE;
        if (spinning) invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        spinning = false;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float stroke = Math.max(2f, size / 11f);
        float inset = stroke + 1f;
        arc.set(inset, inset, getWidth() - inset, getHeight() - inset);
        paint.setStrokeWidth(stroke);
        paint.setColor(color);
        paint.setAlpha(210);
        float rotation = (SystemClock.uptimeMillis() % 900L) * 360f / 900f;
        canvas.drawArc(arc, rotation, 255f, false, paint);
        if (spinning && getVisibility() == VISIBLE) {
            postInvalidateDelayed(16L);
        }
    }
}
