package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

final class SettingRangeView extends View {
    interface Listener {
        void onValueChanged(int value);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final int min;
    private final int max;
    private final int step;
    private final int trackColor;
    private final int progressColor;
    private final int thumbColor;
    private final float density;
    private int value;
    private Listener listener;

    SettingRangeView(Context context, ThemeTokens tokens, int min, int max,
                     int step, int value) {
        super(context);
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.step = Math.max(1, step);
        this.density = context.getResources().getDisplayMetrics().density;
        this.trackColor = tokens.dark ? Color.rgb(58, 58, 62)
                : Color.rgb(209, 209, 214);
        this.progressColor = tokens.dark ? Color.rgb(119, 119, 125)
                : Color.rgb(99, 99, 104);
        this.thumbColor = tokens.dark ? Color.rgb(245, 245, 247) : Color.WHITE;
        this.value = snap(value);
        setFocusable(true);
        setClickable(true);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    int value() {
        return value;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(dp(220));
        int desiredHeight = Math.round(dp(48));
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = dp(2);
        float thumbRadius = dp(9);
        float start = getPaddingLeft() + thumbRadius;
        float end = getWidth() - getPaddingRight() - thumbRadius;
        float centerY = getHeight() / 2.0f;
        if (end <= start) return;

        track.set(start, centerY - radius, end, centerY + radius);
        paint.setColor(trackColor);
        canvas.drawRoundRect(track, radius, radius, paint);

        float fraction = (value - min) / (float) Math.max(1, max - min);
        float thumbX = start + (end - start) * fraction;
        track.right = thumbX;
        paint.setColor(progressColor);
        canvas.drawRoundRect(track, radius, radius, paint);

        paint.setColor(thumbColor);
        canvas.drawCircle(thumbX, centerY, thumbRadius, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                updateFromX(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromX(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
                updateFromX(event.getX());
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            setValue(value - step, true);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            setValue(value + step, true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateFromX(float x) {
        float thumbRadius = dp(9);
        float start = getPaddingLeft() + thumbRadius;
        float end = getWidth() - getPaddingRight() - thumbRadius;
        float fraction = end <= start ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, (x - start) / (end - start)));
        setValue(Math.round(min + (max - min) * fraction), true);
    }

    private void setValue(int value, boolean notify) {
        int snapped = snap(value);
        if (this.value == snapped) return;
        this.value = snapped;
        invalidate();
        if (notify && listener != null) listener.onValueChanged(snapped);
    }

    private int snap(int value) {
        int clamped = Math.max(min, Math.min(max, value));
        int steps = Math.round((clamped - min) / (float) step);
        return Math.max(min, Math.min(max, min + steps * step));
    }

    private float dp(float value) {
        return value * density;
    }
}
