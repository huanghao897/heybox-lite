package com.openzen.heyboxcommunity;

import android.view.View;
import android.widget.FrameLayout;

/**
 * 真实双 View 页面转场：切换期间新旧页面同时存在于容器中一起移动，
 * 顶层加透明遮罩挡触摸；动画结束移除旧页并复位其变换（旧页可能是缓存 View，之后还会复用）。
 *
 * 职责边界：只负责移动已经构建好的 View，不创建页面、不保存业务状态。
 * 新页面先随容器完成一次测量布局（post 之后）才开始动画，避免首帧卡顿。
 * 动画等级为“关闭”、容器为空、或旧内容只是加载圈时，直接硬切。
 */
final class PageTransitionController {
    private static Runnable finishCurrent;

    private PageTransitionController() {}

    /** 立即完成正在进行的转场（切换动画等级、或新转场开始前调用）。 */
    static void finishNow() {
        Runnable pending = finishCurrent;
        finishCurrent = null;
        if (pending != null) pending.run();
    }

    static boolean isRunning() {
        return finishCurrent != null;
    }

    static void run(FrameLayout container, View next, boolean forward) {
        if (container == null || next == null) return;
        finishNow();
        Motions.reset(next);
        View old = container.getChildCount() == 0
                ? null : container.getChildAt(container.getChildCount() - 1);
        boolean oldIsLoading = old != null && "loading".equals(old.getTag());
        if (Motions.off() || old == null || old == next || container.getWidth() <= 0) {
            container.removeAllViews();
            container.addView(next, params());
            return;
        }
        if (oldIsLoading) {
            // 加载圈 → 内容：不值得滑动，只做一次淡入
            container.removeAllViews();
            container.addView(next, params());
            Motions.enter(next, 0.0f);
            return;
        }

        final View oldView = old;
        // 旧页原地不动（避免 detach/attach 丢列表位置），把新页和触摸遮罩叠加上去
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            if (child != oldView) {
                container.removeViewAt(i);
            }
        }
        container.addView(next, params());
        final View blocker = new View(container.getContext());
        blocker.setClickable(true);
        container.addView(blocker, params());

        final Runnable finish = () -> {
            oldView.animate().cancel();
            next.animate().cancel();
            container.removeView(blocker);
            container.removeView(oldView);
            Motions.reset(oldView);
            Motions.reset(next);
        };
        finishCurrent = finish;
        final Runnable end = () -> {
            if (finishCurrent == finish) {
                finishCurrent = null;
                finish.run();
            }
        };

        int width = container.getWidth();
        if (Motions.full()) {
            // 前进：新页从右滑入，旧页向左视差退出；返回：镜像
            next.setTranslationX(forward ? width : -width * 0.30f);
            next.setAlpha(forward ? 1.0f : 0.88f);
            next.post(() -> {
                next.animate().translationX(0.0f).alpha(1.0f)
                        .setDuration(MotionSpec.TRANSITION_FULL_MS)
                        .setInterpolator(MotionSpec.EASE_OUT)
                        .withEndAction(end)
                        .start();
                oldView.animate()
                        .translationX(forward ? -width * 0.30f : width)
                        .alpha(forward ? 0.72f : 1.0f)
                        .setDuration(MotionSpec.TRANSITION_FULL_MS)
                        .setInterpolator(MotionSpec.EASE_OUT)
                        .start();
            });
        } else {
            // 精简：交叉淡入
            next.setAlpha(0.0f);
            next.post(() -> next.animate().alpha(1.0f)
                    .setDuration(MotionSpec.TRANSITION_LITE_MS)
                    .setInterpolator(MotionSpec.EASE_OUT)
                    .withEndAction(end)
                    .start());
        }
    }

    private static FrameLayout.LayoutParams params() {
        return new FrameLayout.LayoutParams(-1, -1);
    }
}
