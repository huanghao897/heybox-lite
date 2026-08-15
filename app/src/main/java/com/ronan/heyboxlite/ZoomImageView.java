package com.ronan.heyboxlite;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

final class ZoomImageView extends ImageView {
    interface GestureListener {
        void onPull(float dx, float dy, float progress);

        void onPullEnd(boolean dismiss);
    }

    private static final float MIN_DOUBLE_TAP_ZOOM = 2.35f;
    private static final float MAX_ZOOM = 12f;
    private static final long PHONE_DOUBLE_TAP_TIMEOUT_MS = 340L;
    private static final long WATCH_DOUBLE_TAP_TIMEOUT_MS = 480L;
    private final Matrix matrix = new Matrix();
    private final float[] startValues = new float[9];
    private final float[] endValues = new float[9];
    private final float[] animValues = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;
    private final long doubleTapTimeout;
    private final float doubleTapSlop;
    private float scale = 1f;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private long lastTapAt;
    private float lastTapX;
    private float lastTapY;
    private boolean moved;
    private boolean pulling;
    private boolean secondTap;
    private boolean roundDisplay;
    private Runnable blankClickListener;
    private GestureListener gestureListener;
    private ValueAnimator matrixAnimator;
    private int tapZoomLevel;
    private float animationStartScale;

    ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        doubleTapTimeout = RoundLayoutMetrics.isWatchDisplay(context)
                ? WATCH_DOUBLE_TAP_TIMEOUT_MS : PHONE_DOUBLE_TAP_TIMEOUT_MS;
        doubleTapSlop = Math.max(dp(24), touchSlop * 3.0f);
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                        if (pulling && gestureListener != null) {
                            gestureListener.onPullEnd(false);
                        }
                        pulling = false;
                        moved = true;
                        cancelMatrixAnimation();
                        return true;
                    }

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
        cancelMatrixAnimation();
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;
        float inset = roundDisplay
                ? Math.min(getWidth(), getHeight()) * 0.10f : 0.0f;
        float availableWidth = Math.max(1.0f, getWidth() - inset * 2.0f);
        float availableHeight = Math.max(1.0f, getHeight() - inset * 2.0f);
        float sx = availableWidth / (float) drawable.getIntrinsicWidth();
        float sy = availableHeight / (float) drawable.getIntrinsicHeight();
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

    void setGestureListener(GestureListener listener) {
        gestureListener = listener;
    }

    void setRoundDisplay(boolean value) {
        roundDisplay = value;
        if (getDrawable() != null) post(this::fitImage);
    }

    void cancelMotion() {
        cancelMatrixAnimation();
        pulling = false;
        secondTap = false;
        moved = false;
        lastTapAt = 0L;
    }

    boolean isSecondTapCandidate(float x, float y, long eventTime) {
        return isSecondTap(x, y, eventTime);
    }

    void imageDisplayRect(RectF out) {
        out.set(0f, 0f, 0f, 0f);
        Drawable drawable = getDrawable();
        if (drawable == null) return;
        RectF bounds = new RectF(0f, 0f,
                drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(bounds);
        out.set(bounds);
    }

    /** 是否已放大：用于外层横滑图集判断——放大时手势归缩放/平移，未放大时才翻页。 */
    boolean isZoomed() {
        return tapZoomLevel > 0 || scale > 1.01f;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::fitImage);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelMatrixAnimation();
                lastX = event.getX();
                lastY = event.getY();
                downX = lastX;
                downY = lastY;
                moved = false;
                pulling = false;
                secondTap = isSecondTap(event.getX(), event.getY(), event.getEventTime());
                if (secondTap) {
                    toggleDoubleTap(event.getX(), event.getY());
                    lastTapAt = 0L;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) return true;
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) moved = true;
                if (!pulling && !secondTap && scale <= 1.01f
                        && dy > touchSlop
                        && dy > Math.abs(dx) * 1.18f) {
                    pulling = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (pulling) {
                    float pullDistance = Math.max(0.0f, dy);
                    float progress = Math.min(1.0f,
                            pullDistance / Math.max(1.0f, getHeight()));
                    if (gestureListener != null) {
                        gestureListener.onPull(dx * 0.22f, pullDistance, progress);
                    }
                    return true;
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
            case MotionEvent.ACTION_UP:
                if (pulling) {
                    finishPull(event);
                    return true;
                }
                correctBounds();
                setImageMatrix(matrix);
                if (!moved && !scaleDetector.isInProgress() && !secondTap) {
                    handleSingleTap(event.getX(), event.getY(), event.getEventTime());
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (pulling && gestureListener != null) {
                    gestureListener.onPullEnd(false);
                }
                pulling = false;
                secondTap = false;
                lastTapAt = 0L;
                correctBounds();
                setImageMatrix(matrix);
                return true;
            default:
                return true;
        }
    }

    private void handleSingleTap(float x, float y, long eventTime) {
        performClick();
        if (tapZoomLevel >= 2) {
            animateFitImage();
            lastTapAt = 0L;
            return;
        }
        if (!isOnImage(x, y) && blankClickListener != null) {
            blankClickListener.run();
            lastTapAt = 0L;
            return;
        }
        lastTapAt = eventTime > 0L ? eventTime : SystemClock.uptimeMillis();
        lastTapX = x;
        lastTapY = y;
    }

    private boolean isSecondTap(float x, float y, long eventTime) {
        if (lastTapAt <= 0L) return false;
        long now = eventTime > 0L ? eventTime : SystemClock.uptimeMillis();
        return now - lastTapAt <= doubleTapTimeout
                && Math.abs(x - lastTapX) <= doubleTapSlop
                && Math.abs(y - lastTapY) <= doubleTapSlop;
    }

    private void finishPull(MotionEvent event) {
        float dy = event.getY() - downY;
        long elapsed = Math.max(1L, event.getEventTime() - event.getDownTime());
        float velocity = dy * 1000.0f / elapsed;
        boolean dismiss = dy > getHeight() * 0.18f || velocity > 720.0f;
        if (gestureListener != null) {
            gestureListener.onPullEnd(dismiss);
        }
        pulling = false;
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
        float sx = contentWidth() / (float) drawable.getIntrinsicWidth();
        float sy = contentHeight() / (float) drawable.getIntrinsicHeight();
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
        animationStartScale = scale;
        matrixAnimator = ValueAnimator.ofFloat(0f, 1f);
        matrixAnimator.setDuration(duration);
        matrixAnimator.setInterpolator(new DecelerateInterpolator());
        matrixAnimator.addUpdateListener(animation -> {
            float fraction = (Float) animation.getAnimatedValue();
            for (int i = 0; i < animValues.length; i++) {
                animValues[i] = startValues[i] + ((endValues[i] - startValues[i]) * fraction);
            }
            matrix.setValues(animValues);
            scale = animationStartScale
                    + ((targetScale - animationStartScale) * fraction);
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
        if (roundDisplay) {
            correctRoundBounds(targetMatrix, bounds);
            return;
        }

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

    private void correctRoundBounds(Matrix targetMatrix, RectF bounds) {
        float diameter = Math.min(getWidth(), getHeight());
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;
        float dx = roundAxisCorrection(bounds.left, bounds.right, centerX, diameter);
        float dy = roundAxisCorrection(bounds.top, bounds.bottom, centerY, diameter);
        if (dx != 0f || dy != 0f) targetMatrix.postTranslate(dx, dy);
    }

    private float roundAxisCorrection(float start, float end, float center, float diameter) {
        if (end - start <= diameter) return center - (start + end) / 2.0f;
        if (start > center) return center - start;
        if (end < center) return center - end;
        return 0f;
    }

    private Matrix fitMatrix() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return null;
        float sx = contentWidth() / (float) drawable.getIntrinsicWidth();
        float sy = contentHeight() / (float) drawable.getIntrinsicHeight();
        float fit = Math.min(sx, sy);
        float dx = (getWidth() - drawable.getIntrinsicWidth() * fit) / 2f;
        float dy = (getHeight() - drawable.getIntrinsicHeight() * fit) / 2f;
        Matrix target = new Matrix();
        target.postScale(fit, fit);
        target.postTranslate(dx, dy);
        return target;
    }

    private float contentWidth() {
        return Math.max(1.0f, getWidth() - contentInset() * 2.0f);
    }

    private float contentHeight() {
        return Math.max(1.0f, getHeight() - contentInset() * 2.0f);
    }

    private float contentInset() {
        return roundDisplay ? Math.min(getWidth(), getHeight()) * 0.10f : 0.0f;
    }

    private boolean isOnImage(float x, float y) {
        Drawable drawable = getDrawable();
        if (drawable == null) return false;
        RectF bounds = new RectF(0, 0,
                drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(bounds);
        return bounds.contains(x, y);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
