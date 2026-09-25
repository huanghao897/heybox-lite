package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

final class CrashTestController {
    private CrashTestController() {}

    static void confirm(Activity activity, SessionStore session, ThemeTokens tokens) {
        new LiteDialogPresenter(activity, session, tokens).show(
                "崩溃测试", "将主动结束本次运行并打开崩溃页，测试日志会自动上传。",
                "开始测试", () -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (activity.isFinishing()) return;
                    CrashBreadcrumbs.record("intentionalCrashTest: true");
                    throw new ManualCrashTestException();
                }, 180L),
                "取消", null, null, null);
    }

    static final class ManualCrashTestException extends RuntimeException {
        ManualCrashTestException() {
            super("User initiated crash test; intentionalCrashTest: true");
        }
    }
}
