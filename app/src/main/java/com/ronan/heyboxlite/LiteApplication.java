package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import java.util.List;

public final class LiteApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        CrashReporter.install(this);
    }

    @Override public void onCreate() {
        super.onCreate();
        String process = processName();
        CrashReporter.setProcess(process);
        if (process.equals(getPackageName()) || process.endsWith(":crash")) {
            CrashUploads.schedule(this);
        }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                CrashBreadcrumbs.screen(activity.getClass().getSimpleName());
                CrashUploads.schedule(LiteApplication.this);
            }
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private String processName() {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes =
                manager == null ? null : manager.getRunningAppProcesses();
        if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.pid == Process.myPid()) return process.processName;
            }
        }
        return getPackageName();
    }
}
