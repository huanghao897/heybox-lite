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
    private static final int INVALID_POINTER_ID = -1;
    private static final long PHONE_DOUBLE_TAP_TIMEOUT_MS = 340L;
    private static final long WATCH_DOUBLE_TAP_TIMEOUT_MS = 480L;
    private final Matrix matrix = new Matrix();
    private final float[] startValues = new float[9];
    private final float[] endValues = new float[9];
    private final float[] animValues = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;
    private final float pullTouchThreshold;
    private final long doubleTapTimeout;
    private final float doubleTapSlop;
    private float scale = 1f;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private float downRawX;
    private float downRawY;
    private long lastTapAt;
    private float lastTapX;
    private float lastTapY;
    private boolean moved;
    private boolean pulling;
    private boolean secondTap;
    private boolean roundDisplay;
    private Runnable blankClickListener;
    private ImagePullListener gestureListener;
    private ValueAnimator matrixAnimator;
    private int tapZoomLevel;
    private float animationStartScale;
    private int activePointerId = INVALID_POINTER_ID;
    private boolean multiTouchGesture;
    private final Runnable pendingFitTap = () -> {
        if (tapZoomLevel >= 2 && lastTapAt > 0L && !multiTouchGesture) {
            animateFitImage();
        }
    };

    ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        pullTouchThreshold = touchSlop * ImageDismissPolicy.TOUCH_SLOP_MULTIPLIER;
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
                        multiTouchGesture = true;
                        cancelPendingSingleTap();
                        lastTapAt = 0L;
                        cancelMatrixAnimation();
                        return true;
                    }

                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        cancelMatrixAnimation();
                        float factor = ImageZoomPolicy.pinchFactor(detector.getScaleFactor(), roundDisplay);
                        float next = Math.max(1f, Math.min(
                                ImageZoomPolicy.MAX_ZOOM, scale * factor));
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
        cancelPendingSingleTap();
        lastTapAt = 0L;
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

    void setGestureListener(ImagePullListener listener) {
        gestureListener = listener;
    }

    void setRoundDisplay(boolean value) {
        roundDisplay = value;
        if (getDrawable() != null) post(this::fitImage);
    }

    void cancelMotion() {
        cancelMatrixAnimation();
        cancelPendingSingleTap();
        pulling = false;
        secondTap = false;
        multiTouchGesture = false;
        activePointerId = INVALID_POINTER_ID;
        moved = false;
        lastTapAt = 0L;
    }

    void cancelZoomAnimation() {
        cancelMatrixAnimation();
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
                if (gestureListener != null) gestureListener.onInteractionStart();
                cancelMatrixAnimation();
                boolean isSecondTap = isSecondTap(
                        event.getX(), event.getY(), event.getEventTime());
                cancelPendingSingleTap();
                if (!isSecondTap) lastTapAt = 0L;
                activePointerId = event.getPointerId(0);
                lastX = event.getX();
                lastY = event.getY();
                downX = lastX;
                downY = lastY;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                moved = false;
                pulling = false;
                secondTap = isSecondTap;
                multiTouchGesture = false;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pulling && gestureListener != null) {
                    gestureListener.onPullEnd(false);
                }
                pulling = false;
                moved = true;
                secondTap = false;
                multiTouchGesture = true;
                cancelPendingSingleTap();
                lastTapAt = 0L;
                requestParentTouch(true);
                updateLastPointerPosition(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = activePointerIndex(event);
                float currentX = event.getX(pointerIndex);
                float currentY = event.getY(pointerIndex);
                if (event.getPointerCount() > 1 || scaleDetector.isInProgress()) {
                    multiTouchGesture = true;
                    moved = true;
                    lastX = currentX;
                    lastY = currentY;
                    return true;
                }
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (secondTap) {
                    if (Math.abs(dx) <= doubleTapSlop && Math.abs(dy) <= doubleTapSlop) {
                        lastX = currentX;
                        lastY = currentY;
                        return true;
                    }
                    secondTap = false;
                    cancelPendingSingleTap();
                    lastTapAt = 0L;
                }
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) moved = true;
                if (!pulling && !secondTap && !multiTouchGesture && scale <= 1.01f
                        && Math.abs(dy) > pullTouchThreshold
                        && Math.abs(dy) > Math.abs(dx) * 1.18f) {
                    pulling = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (pulling) {
                    float pullDistance = ImageDismissPolicy.effectiveDistance(
                            dy, pullTouchThreshold);
                    float progress = ImageDismissPolicy.progress(pullDistance, getHeight());
                    if (gestureListener != null) {
                        gestureListener.onPull(dx * 0.5f, pullDistance, progress);
                    }
                    return true;
                }
                if (scale > 1f) {
                    cancelMatrixAnimation();
                    matrix.postTranslate(currentX - lastX, currentY - lastY);
                    correctBounds();
                    setImageMatrix(matrix);
                }
                lastX = currentX;
                lastY = currentY;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                multiTouchGesture = true;
                moved = true;
                secondTap = false;
                cancelPendingSingleTap();
                lastTapAt = 0L;
                selectRemainingPointer(event);
                return true;
            case MotionEvent.ACTION_UP:
                requestParentTouch(false);
                activePointerId = INVALID_POINTER_ID;
                if (pulling) {
                    finishPull(event);
                    return true;
                }
                if (multiTouchGesture) {
                    multiTouchGesture = false;
                    secondTap = false;
                    correctBounds();
                    setImageMatrix(matrix);
                    return true;
                }
                if (secondTap) {
                    boolean withinTapArea = Math.abs(event.getX() - downX) <= doubleTapSlop
                            && Math.abs(event.getY() - downY) <= doubleTapSlop;
                    secondTap = false;
                    lastTapAt = 0L;
                    if (withinTapArea) {
                        toggleDoubleTap(event.getX(), event.getY());
                    }
                    return true;
                }
                correctBounds();
                setImageMatrix(matrix);
                if (!moved && !scaleDetector.isInProgress() && !secondTap) {
                    handleSingleTap(event.getX(), event.getY(), event.getEventTime());
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                requestParentTouch(false);
                if (pulling && gestureListener != null) {
                    gestureListener.onPullEnd(false);
                }
                pulling = false;
                secondTap = false;
                multiTouchGesture = false;
                activePointerId = INVALID_POINTER_ID;
                cancelPendingSingleTap();
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
        if (!isOnImage(x, y) && blankClickListener != null) {
            blankClickListener.run();
            lastTapAt = 0L;
            return;
        }
        lastTapAt = eventTime > 0L ? eventTime : SystemClock.uptimeMillis();
        lastTapX = x;
        lastTapY = y;
        if (tapZoomLevel >= 2) {
            postDelayed(pendingFitTap, doubleTapTimeout + 16L);
        }
    }

    private boolean isSecondTap(float x, float y, long eventTime) {
        if (lastTapAt <= 0L) return false;
        long now = eventTime > 0L ? eventTime : SystemClock.uptimeMillis();
        return now - lastTapAt <= doubleTapTimeout
                && Math.abs(x - lastTapX) <= doubleTapSlop
                && Math.abs(y - lastTapY) <= doubleTapSlop;
    }

    private void finishPull(MotionEvent event) {
        float dy = ImageDismissPolicy.effectiveDistance(
                event.getRawY() - downRawY, pullTouchThreshold);
        boolean dismiss = ImageDismissPolicy.shouldDismiss(dy, getHeight());
        if (gestureListener != null) {
            gestureListener.onPullEnd(dismiss);
        }
        pulling = false;
    }

    private void toggleDoubleTap(float x, float y) {
        float widthZoom = widthFillZoom();
        tapZoomLevel = ImageZoomPolicy.nextLevel(tapZoomLevel);
        if (tapZoomLevel == 0) {
            animateFitImage();
            return;
        }
        animateZoomTo(ImageZoomPolicy.targetScale(tapZoomLevel, widthZoom), x, y);
    }

    private void animateZoomTo(float target, float x, float y) {
        target = Math.max(1f, Math.min(ImageZoomPolicy.MAX_ZOOM, target));
        Matrix targetMatrix = new Matrix(matrix);
        float factor = target / Math.max(0.001f, scale);
        targetMatrix.postScale(factor, factor, x, y);
        correctBounds(targetMatrix);
        animateMatrixTo(targetMatrix, target, 220);
    }

    private float widthFillZoom() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            return ImageZoomPolicy.MIN_DOUBLE_TAP_ZOOM;
        }
        float sx = contentWidth() / (float) drawable.getIntrinsicWidth();
        float sy = contentHeight() / (float) drawable.getIntrinsicHeight();
        float fit = Math.min(sx, sy);
        if (fit <= 0f) return ImageZoomPolicy.MIN_DOUBLE_TAP_ZOOM;
        return Math.min(ImageZoomPolicy.MAX_ZOOM, sx / fit);
    }

    private void animateFitImage() {
        cancelPendingSingleTap();
        lastTapAt = 0L;
        secondTap = false;
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

    private void cancelPendingSingleTap() {
        removeCallbacks(pendingFitTap);
    }

    private int activePointerIndex(MotionEvent event) {
        int index = event.findPointerIndex(activePointerId);
        return index >= 0 ? index : 0;
    }

    private void updateLastPointerPosition(MotionEvent event) {
        int index = activePointerIndex(event);
        lastX = event.getX(index);
        lastY = event.getY(index);
    }

    private void selectRemainingPointer(MotionEvent event) {
        int liftedIndex = event.getActionIndex();
        int liftedId = event.getPointerId(liftedIndex);
        if (liftedId != activePointerId) {
            updateLastPointerPosition(event);
            return;
        }
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == liftedIndex) continue;
            activePointerId = event.getPointerId(i);
            lastX = event.getX(i);
            lastY = event.getY(i);
            return;
        }
        activePointerId = INVALID_POINTER_ID;
    }

    private void requestParentTouch(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    @Override protected void onDetachedFromWindow() {
        cancelMotion();
        super.onDetachedFromWindow();
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
