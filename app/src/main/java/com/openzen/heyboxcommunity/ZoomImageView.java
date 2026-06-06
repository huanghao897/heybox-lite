package com.openzen.heyboxcommunity;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

final class ZoomImageView extends ImageView {
    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private float scale = 1f;
    private float lastX;
    private float lastY;

    ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
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

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::fitImage);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress()) {
            if (scale > 1f) {
                matrix.postTranslate(event.getX() - lastX, event.getY() - lastY);
                setImageMatrix(matrix);
            }
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && event.getEventTime()
                - event.getDownTime() < 180) {
            performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
