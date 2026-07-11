package com.openzen.heyboxcommunity;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

final class ZoomImageView extends ImageView {
    private static final float MIN_DOUBLE_TAP_ZOOM = 2.35f;
    private static final float MAX_ZOOM = 12f;
    private final Matrix matrix = new Matrix();
    private final float[] startValues = new float[9];
    private final float[] endValues = new float[9];
    private final float[] animValues = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;
    private float scale = 1f;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private long lastTapAt;
    private float lastTapX;
    private float lastTapY;
    private boolean moved;
    private Runnable blankClickListener;
    private Runnable pendingSingleTap;
    private ValueAnimator matrixAnimator;
    private int tapZoomLevel;

    ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        cancelMatrixAnimation();
                        float factor = detector.getScaleFactor();
                        float next = Math.max(1f, Math.min(MAX_ZOOM, scale * factor));
                        factor = next / scale;
                        scale = next;
                        tapZoomLevel = 0;
                        matrix.postScale(factor, factor,
                                detector.getFocusX(), detector.getFocusY());
                        correctBounds();
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
        tapZoomLevel = 0;
        correctBounds();
        setImageMatrix(matrix);
    }

    void setOnBlankClickListener(Runnable listener) {
        blankClickListener = listener;
    }

    /** 是否已放大：用于外层横滑图集判断——放大时手势归缩放/平移，未放大时才翻页。 */
    boolean isZoomed() {
        return scale > 1.01f;
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
                cancelMatrixAnimation();
                matrix.postTranslate(event.getX() - lastX, event.getY() - lastY);
                correctBounds();
                setImageMatrix(matrix);
            }
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            correctBounds();
            setImageMatrix(matrix);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved
                    && !scaleDetector.isInProgress()) {
                handleTap(event.getX(), event.getY());
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                removePendingSingleTap();
            }
        }
        return true;
    }

    private void handleTap(float x, float y) {
        long now = System.currentTimeMillis();
        boolean doubleTap = now - lastTapAt < 320
                && Math.abs(x - lastTapX) < touchSlop * 2f
                && Math.abs(y - lastTapY) < touchSlop * 2f;
        if (doubleTap) {
            removePendingSingleTap();
            toggleDoubleTap(x, y);
            lastTapAt = 0L;
            return;
        }
        lastTapAt = now;
        lastTapX = x;
        lastTapY = y;
        removePendingSingleTap();
        pendingSingleTap = () -> {
            pendingSingleTap = null;
            performClick();
            if (tapZoomLevel >= 2) {
                animateFitImage();
            } else if (!isOnImage(x, y) && blankClickListener != null) {
                blankClickListener.run();
            }
        };
        postDelayed(pendingSingleTap, 260);
    }

    private void removePendingSingleTap() {
        if (pendingSingleTap != null) {
            removeCallbacks(pendingSingleTap);
            pendingSingleTap = null;
        }
    }

    private void toggleDoubleTap(float x, float y) {
        float widthZoom = widthFillZoom();
        if (tapZoomLevel <= 0 || scale <= 1.05f) {
            tapZoomLevel = 1;
            animateZoomTo(Math.max(MIN_DOUBLE_TAP_ZOOM, widthZoom), x, y);
        } else if (tapZoomLevel == 1) {
            tapZoomLevel = 2;
            animateZoomTo(Math.max(4.0f, Math.min(MAX_ZOOM, widthZoom * 1.55f)), x, y);
        } else {
            animateFitImage();
        }
    }

    private void animateZoomTo(float target, float x, float y) {
        target = Math.max(1f, Math.min(MAX_ZOOM, target));
        Matrix targetMatrix = new Matrix(matrix);
        float factor = target / Math.max(0.001f, scale);
        targetMatrix.postScale(factor, factor, x, y);
        correctBounds(targetMatrix);
        animateMatrixTo(targetMatrix, target, 220);
    }

    private float widthFillZoom() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return MIN_DOUBLE_TAP_ZOOM;
        float sx = getWidth() / (float) drawable.getIntrinsicWidth();
        float sy = getHeight() / (float) drawable.getIntrinsicHeight();
        float fit = Math.min(sx, sy);
        if (fit <= 0f) return MIN_DOUBLE_TAP_ZOOM;
        return Math.min(MAX_ZOOM, sx / fit);
    }

    private void animateFitImage() {
        Matrix targetMatrix = fitMatrix();
        if (targetMatrix == null) {
            fitImage();
            return;
        }
        tapZoomLevel = 0;
        animateMatrixTo(targetMatrix, 1f, 200);
    }

    private void animateMatrixTo(final Matrix targetMatrix, final float targetScale, int duration) {
        cancelMatrixAnimation();
        matrix.getValues(startValues);
        targetMatrix.getValues(endValues);
        matrixAnimator = ValueAnimator.ofFloat(0f, 1f);
        matrixAnimator.setDuration(duration);
        matrixAnimator.setInterpolator(new DecelerateInterpolator());
        matrixAnimator.addUpdateListener(animation -> {
            float fraction = (Float) animation.getAnimatedValue();
            for (int i = 0; i < animValues.length; i++) {
                animValues[i] = startValues[i] + ((endValues[i] - startValues[i]) * fraction);
            }
            matrix.setValues(animValues);
            setImageMatrix(matrix);
        });
        matrixAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
                matrixAnimator = null;
            }

            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (cancelled) {
                    return;
                }
                matrix.set(targetMatrix);
                scale = targetScale;
                setImageMatrix(matrix);
                matrixAnimator = null;
            }
        });
        matrixAnimator.start();
    }

    private void cancelMatrixAnimation() {
        if (matrixAnimator != null) {
            matrixAnimator.cancel();
            matrixAnimator = null;
        }
    }

    private void correctBounds() {
        correctBounds(matrix);
    }

    private void correctBounds(Matrix targetMatrix) {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;
        RectF bounds = new RectF(0, 0,
                drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        targetMatrix.mapRect(bounds);
        float dx = 0f;
        float dy = 0f;
        if (bounds.width() <= getWidth()) {
            dx = getWidth() / 2f - bounds.centerX();
        } else if (bounds.left > 0f) {
            dx = -bounds.left;
        } else if (bounds.right < getWidth()) {
            dx = getWidth() - bounds.right;
        }
        if (bounds.height() <= getHeight()) {
            dy = getHeight() / 2f - bounds.centerY();
        } else if (bounds.top > 0f) {
            dy = -bounds.top;
        } else if (bounds.bottom < getHeight()) {
            dy = getHeight() - bounds.bottom;
        }
        if (dx != 0f || dy != 0f) targetMatrix.postTranslate(dx, dy);
    }

    private Matrix fitMatrix() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return null;
        float sx = getWidth() / (float) drawable.getIntrinsicWidth();
        float sy = getHeight() / (float) drawable.getIntrinsicHeight();
        float fit = Math.min(sx, sy);
        float dx = (getWidth() - drawable.getIntrinsicWidth() * fit) / 2f;
        float dy = (getHeight() - drawable.getIntrinsicHeight() * fit) / 2f;
        Matrix target = new Matrix();
        target.postScale(fit, fit);
        target.postTranslate(dx, dy);
        return target;
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
