package com.openzen.heyboxcommunity;

import android.view.View;
import android.view.ViewGroup;

/**
 * 全局动效等级与通用动画工具。
 * 等级由 SessionStore 持久化（首启按设备内存一次性判定），启动时注入到这里；
 * 所有动画只操作 alpha/translation/scale 这类 GPU 友好属性，关闭挡位时立即到位。
 */
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
        view.animate().alpha(1.0f).translationY(0.0f)
                .setStartDelay(0L)
                .setDuration(MotionSpec.ENTER_MS)
                .setInterpolator(MotionSpec.EASE_OUT)
                .start();
    }

    /** 列表首批入场：按序号交错延迟。仅供“本轮未入场过”的条目调用。 */
    static void listEnter(View view, int index, float translatePx) {
        if (view == null) return;
        view.animate().cancel();
        if (off()) {
            reset(view);
            return;
        }
        view.setAlpha(0.0f);
        view.setTranslationY(full() ? translatePx : 0.0f);
        view.animate().alpha(1.0f).translationY(0.0f)
                .setStartDelay(Math.max(0, index) * MotionSpec.STAGGER_MS)
                .setDuration(MotionSpec.ENTER_MS)
                .setInterpolator(MotionSpec.EASE_OUT)
                .withEndAction(() -> {
                    view.setAlpha(1.0f);
                    view.setTranslationY(0.0f);
                })
                .start();
    }

    /** 弹窗内容进场：轻缩放 + 淡入；完整挡带轻微回弹。 */
    static void dialogIn(View content) {
        if (content == null || off()) return;
        content.animate().cancel();
        content.setAlpha(0.0f);
        content.setScaleX(0.94f);
        content.setScaleY(0.94f);
        content.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f)
                .setStartDelay(0L)
                .setDuration(MotionSpec.DIALOG_MS)
                .setInterpolator(full() ? MotionSpec.SPRING : MotionSpec.EASE_OUT)
                .start();
    }
}
