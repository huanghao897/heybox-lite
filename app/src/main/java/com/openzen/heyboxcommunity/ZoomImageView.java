package com.openzen.heyboxcommunity;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.widget.ImageView;

final class ZoomImageView extends ImageView {
    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;
    private float scale = 1f;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private boolean moved;
    private Runnable blankClickListener;

    ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();
                        float next = Math.max(1f, Math.min(6f, scale * factor));
                        factor = next / scale;
                        scale = next;
                        matrix.postScale(factor, factor,
                                detector.getFocusX(), detector.getFocusY());
                        setImageMatrix(matrix);
                        return true;
                    }
                });
    }

    void fitImage() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;
        float sx = getWidth() / (float) drawable.getIntrinsicWidth();
        float sy = getHeight() / (float) drawable.getIntrinsicHeight();
        float fit = Math.min(sx, sy);
        float dx = (getWidth() - drawable.getIntrinsicWidth() * fit) / 2f;
        float dy = (getHeight() - drawable.getIntrinsicHeight() * fit) / 2f;
        matrix.reset();
        matrix.postScale(fit, fit);
        matrix.postTranslate(dx, dy);
        scale = 1f;
        setImageMatrix(matrix);
    }

    void setOnBlankClickListener(Runnable listener) {
        blankClickListener = listener;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::fitImage);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            downX = lastX;
            downY = lastY;
            moved = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress()) {
            if (Math.abs(event.getX() - downX) > touchSlop
                    || Math.abs(event.getY() - downY) > touchSlop) {
                moved = true;
            }
            if (scale > 1f) {
                matrix.postTranslate(event.getX() - lastX, event.getY() - lastY);
                setImageMatrix(matrix);
            }
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved
                && event.getEventTime() - event.getDownTime() < 220) {
            performClick();
            if (!isOnImage(event.getX(), event.getY()) && blankClickListener != null) {
                blankClickListener.run();
            }
        }
        return true;
    }

    private boolean isOnImage(float x, float y) {
        Drawable drawable = getDrawable();
        if (drawable == null) return false;
        RectF bounds = new RectF(0, 0,
                drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(bounds);
        return bounds.contains(x, y);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
