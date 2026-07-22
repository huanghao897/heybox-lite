package com.ronan.heyboxlite;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

/** Global motion level and lightweight property animations. */
final class Motions {
    static final int LEVEL_OFF = 0;
    static final int LEVEL_LITE = 1;
    static final int LEVEL_FULL = 2;

    private static volatile int level = LEVEL_LITE;

    private Motions() {}

    static void setLevel(int value) {
        level = Math.max(LEVEL_OFF, Math.min(LEVEL_FULL, value));
    }

    static int level() {
        return level;
    }

    static boolean off() {
        return level == LEVEL_OFF;
    }

    static boolean full() {
        return level == LEVEL_FULL;
    }

    /** 取消 View 上的属性动画并复位常用变换，防止复用时状态污染。 */
    static void reset(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setAlpha(1.0f);
        view.setTranslationX(0.0f);
        view.setTranslationY(0.0f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
    }

    static void cancelTree(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            cancelTree(group.getChildAt(i));
        }
    }

    /** 面板/页面内容入场：淡入（完整挡带轻微上移）。关闭挡直接到位。 */
    static void enter(View view, float translatePx) {
        if (view == null) return;
        view.animate().cancel();
        if (off()) {
            reset(view);
            return;
        }
        view.setAlpha(0.0f);
        view.setTranslationY(full() ? translatePx : translatePx * 0.35f);
        view.setScaleX(full() ? 0.992f : 1.0f);
        view.setScaleY(full() ? 0.992f : 1.0f);
        view.animate().alpha(1.0f).translationY(0.0f)
                .scaleX(1.0f).scaleY(1.0f)
                .setStartDelay(0L)
                .setDuration(full() ? MotionSpec.ENTER_MS : MotionSpec.ENTER_LITE_MS)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .start();
    }

    /** 列表首批入场：按序号交错延迟。仅供“本轮未入场过”的条目调用。 */
    static void listEnter(View view, int index, float translatePx) {
        if (view == null) return;
        view.animate().cancel();
        if (off() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            reset(view);
            return;
        }
        view.setAlpha(0.0f);
        view.setTranslationY(full() ? translatePx : translatePx * 0.30f);
        view.setScaleX(full() ? 0.985f : 1.0f);
        view.setScaleY(full() ? 0.985f : 1.0f);
        view.animate().alpha(1.0f).translationY(0.0f)
                .scaleX(1.0f).scaleY(1.0f)
                .setStartDelay(Math.max(0, index) * MotionSpec.STAGGER_MS)
                .setDuration(full() ? MotionSpec.ENTER_MS : MotionSpec.ENTER_LITE_MS)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .withEndAction(() -> {
                    view.setAlpha(1.0f);
                    view.setTranslationY(0.0f);
                    view.setScaleX(1.0f);
                    view.setScaleY(1.0f);
                })
                .start();
    }

    /** 弹窗内容进场：轻缩放 + 淡入；完整挡带轻微回弹。 */
    static void dialogIn(View content) {
        if (content == null || off()) return;
        content.animate().cancel();
        content.setAlpha(0.0f);
        float density = content.getResources().getDisplayMetrics().density;
        content.setTranslationY((full() ? 12.0f : 4.0f) * density);
        content.setScaleX(full() ? 0.92f : 0.98f);
        content.setScaleY(full() ? 0.92f : 0.98f);
        content.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f)
                .translationY(0.0f)
                .setStartDelay(0L)
                .setDuration(full() ? MotionSpec.DIALOG_MS : MotionSpec.DIALOG_LITE_MS)
                .setInterpolator(full() ? MotionSpec.SPRING : MotionSpec.EASE_OUT)
                .start();
    }

    static void selected(View view) {
        if (view == null) return;
        view.animate().cancel();
        if (off() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            reset(view);
            return;
        }
        view.setAlpha(full() ? 0.72f : 0.86f);
        view.setScaleX(full() ? 0.82f : 0.94f);
        view.setScaleY(full() ? 0.82f : 0.94f);
        view.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f)
                .setDuration(full() ? 210L : 130L)
                .setInterpolator(full() ? MotionSpec.SPRING : MotionSpec.EASE_OUT)
                .start();
    }

    static void fadeThrough(View outgoing, View incoming) {
        if (incoming == null) return;
        if (off()) {
            reset(outgoing);
            reset(incoming);
            if (outgoing != null) outgoing.setAlpha(0.0f);
            return;
        }
        if (outgoing != null) {
            outgoing.animate().cancel();
            outgoing.animate().alpha(0.0f).setDuration(full() ? 90L : 70L)
                    .setInterpolator(MotionSpec.STANDARD).start();
        }
        incoming.animate().cancel();
        incoming.setAlpha(0.0f);
        incoming.setScaleX(full() ? 0.96f : 0.99f);
        incoming.setScaleY(full() ? 0.96f : 0.99f);
        incoming.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f)
                .setStartDelay(full() ? 70L : 40L)
                .setDuration(full() ? 180L : 120L)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .start();
    }
}
