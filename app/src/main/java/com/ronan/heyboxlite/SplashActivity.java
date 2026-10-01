package com.ronan.heyboxlite;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public final class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SessionStore session;
    private SplashSceneView scene;
    private long startedAt;
    private boolean opening;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CrashReporter.install(this);
        session = new SessionStore(this);
        Motions.setLevel(session.motionLevel());
        if (!session.splashEnabled()) {
            openMain();
            return;
        }
        startedAt = System.currentTimeMillis();
        this.scene = new SplashSceneView(this, session.splashText(),
                session.darkMode(), session.uiScale() / 100.0f,
                session.textScale() / 100.0f, false);
        int background = session.darkMode()
                ? android.graphics.Color.rgb(11, 11, 12)
                : android.graphics.Color.rgb(244, 244, 246);
        setContentView(this.scene);
        Compat.colorSystemBars(getWindow(), background);
        this.scene.playEntrance();
        long remaining = session.splashDuration()
                - (System.currentTimeMillis() - startedAt)
                - this.scene.fadeOutDuration();
        handler.postDelayed(this::startExit, Math.max(120L, remaining));
    }

    private void startExit() {
        if (isFinishing() || this.scene == null) return;
        this.scene.fadeOut(this::openMain);
    }

    private void openMain() {
        if (isFinishing() || this.opening) return;
        this.opening = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(0, 0);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (scene != null) scene.cancelMotion();
        super.onDestroy();
    }
}
