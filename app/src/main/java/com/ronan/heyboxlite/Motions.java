package com.ronan.heyboxlite;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

/**
 * 通用动画工具。动效恒为完整挡（三挡调节已移除），
 * 所有动画只操作 alpha/translation/scale 这类 GPU 友好属性。
 */
final class Motions {
    private Motions() {}

    static boolean off() {
        return false;
    }

    static boolean full() {
        return true;
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

    static void resetTree(View view) {
        if (view == null) return;
        reset(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            resetTree(group.getChildAt(i));
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
        view.setTranslationY(full() ? translatePx : 0.0f);
        view.setScaleX(full() ? 0.992f : 1.0f);
        view.setScaleY(full() ? 0.992f : 1.0f);
        view.animate().alpha(1.0f).translationY(0.0f)
                .scaleX(1.0f).scaleY(1.0f)
                .setStartDelay(0L)
                .setDuration(MotionSpec.ENTER_MS)
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
        view.setTranslationY(full() ? translatePx : 0.0f);
        view.setScaleX(full() ? 0.985f : 1.0f);
        view.setScaleY(full() ? 0.985f : 1.0f);
        view.animate().alpha(1.0f).translationY(0.0f)
                .scaleX(1.0f).scaleY(1.0f)
                .setStartDelay(Math.max(0, index) * MotionSpec.STAGGER_MS)
                .setDuration(MotionSpec.ENTER_MS)
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
        content.setTranslationY(12.0f * content.getResources().getDisplayMetrics().density);
        content.setScaleX(0.92f);
        content.setScaleY(0.92f);
        content.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f)
                .translationY(0.0f)
                .setStartDelay(0L)
                .setDuration(MotionSpec.DIALOG_MS)
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
        view.setAlpha(0.72f);
        view.setScaleX(0.82f);
        view.setScaleY(0.82f);
        view.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f)
                .setDuration(210L)
                .setInterpolator(MotionSpec.SPRING)
                .start();
    }

    static void fadeThrough(View outgoing, View incoming) {
        if (incoming == null) return;
        if (outgoing != null) {
            outgoing.animate().cancel();
            outgoing.animate().alpha(0.0f).setDuration(90L)
                    .setInterpolator(MotionSpec.STANDARD).start();
        }
        incoming.animate().cancel();
        incoming.setAlpha(0.0f);
        incoming.setScaleX(0.96f);
        incoming.setScaleY(0.96f);
        incoming.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f)
                .setStartDelay(70L)
                .setDuration(180L)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .start();
    }
}
