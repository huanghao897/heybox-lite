package com.ronan.heyboxlite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public final class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SessionStore session;
    private TextView message;
    private String splashText;
    private int frame;
    private long startedAt;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CrashReporter.install(this);
        session = new SessionStore(this);
        if (!session.splashEnabled()) {
            openMain();
            return;
        }
        startedAt = System.currentTimeMillis();
        splashText = session.splashText();
        int secondary = parseColor(session.secondaryColor(), Color.rgb(145, 145, 152));
        int background = session.darkMode() ? Color.rgb(14, 15, 16) : Color.rgb(246, 247, 249);
        int foreground = session.darkMode() ? Color.WHITE : Color.rgb(24, 26, 28);
        boolean dark = session.darkMode();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(background);
        if (!dark) {
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.splash_logo);
            mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            FrameLayout.LayoutParams markParams =
                    new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER);
            markParams.bottomMargin = dp(54);
            root.addView(mark, markParams);
        }

        message = new TextView(this);
        message.setTextColor(foreground);
        message.setTextSize(14);
        message.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        message.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textParams =
                new FrameLayout.LayoutParams(-1, dp(48), Gravity.CENTER);
        textParams.topMargin = dp(dark ? 0 : 58);
        textParams.leftMargin = dp(18);
        textParams.rightMargin = dp(18);
        root.addView(message, textParams);

        TextView line = new TextView(this);
        line.setBackgroundColor(secondary);
        FrameLayout.LayoutParams lineParams =
                new FrameLayout.LayoutParams(dp(58), dp(2), Gravity.CENTER);
        lineParams.topMargin = dp(dark ? 46 : 104);
        root.addView(line, lineParams);
        setContentView(root);
        Compat.colorSystemBars(getWindow(), background);
        animateText();
    }

    private void animateText() {
        if (frame <= splashText.length()) {
            message.setText(splashText.substring(0, frame) + (frame < splashText.length() ? "_" : ""));
            frame++;
            handler.postDelayed(this::animateText,
                    Math.max(28, session.splashDuration() / Math.max(1, splashText.length() + 4)));
        } else {
            long remaining = session.splashDuration() - (System.currentTimeMillis() - startedAt);
            handler.postDelayed(this::openMain, Math.max(120, remaining));
        }
    }

    private void openMain() {
        if (isFinishing()) return;
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static int parseColor(String value, int fallback) {
        try {
            return value == null || value.isEmpty() ? fallback : Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
