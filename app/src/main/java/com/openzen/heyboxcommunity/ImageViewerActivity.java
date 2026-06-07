package com.openzen.heyboxcommunity;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public final class ImageViewerActivity extends Activity {
    static final String EXTRA_URL = "image_url";
    static final String EXTRA_ORIGIN_X = "origin_x";
    static final String EXTRA_ORIGIN_Y = "origin_y";
    static final String EXTRA_ORIGIN_WIDTH = "origin_width";
    static final String EXTRA_ORIGIN_HEIGHT = "origin_height";

    private FrameLayout root;
    private ZoomImageView image;
    private ProgressBar progress;
    private TextView original;
    private String url;
    private boolean closing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Compat.colorSystemBars(getWindow(), Color.rgb(26, 28, 31));
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        url = getIntent().getStringExtra(EXTRA_URL);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(222, 27, 29, 32));
        image = new ZoomImageView(this);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView back = control("‹ 返回");
        back.setOnClickListener(view -> closeViewer());
        FrameLayout.LayoutParams backParams =
                new FrameLayout.LayoutParams(dp(72), dp(38), Gravity.TOP | Gravity.LEFT);
        root.addView(back, backParams);

        original = control("查看原图");
        original.setOnClickListener(view -> loadOriginal());
        FrameLayout.LayoutParams originalParams =
                new FrameLayout.LayoutParams(dp(92), dp(38), Gravity.TOP | Gravity.RIGHT);
        root.addView(original, originalParams);

        progress = new ProgressBar(this);
        Compat.tint(progress, Color.argb(210, 235, 238, 241));
        root.addView(progress, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        setContentView(root);
        prepareEnterAnimation();
        loadPreview();
    }

    private void prepareEnterAnimation() {
        image.setScaleX(0.96f);
        image.setScaleY(0.96f);
        image.setAlpha(0f);
        root.setAlpha(0f);
        root.post(() -> {
            root.animate().alpha(1f).setDuration(140).start();
            image.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(190)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
    }

    private void loadPreview() {
        progress.setVisibility(View.VISIBLE);
        ImageLoader.load(url, 1600, bitmap -> {
            image.animate().cancel();
            image.setAlpha(0f);
            image.setScaleX(0.985f);
            image.setScaleY(0.985f);
            image.setImageBitmap(bitmap);
            image.post(image::fitImage);
            progress.animate().alpha(0f).setDuration(90).start();
            image.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(160)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            progress.postDelayed(() -> progress.setVisibility(View.GONE), 100);
        });
    }

    private void loadOriginal() {
        original.setEnabled(false);
        original.setText("加载中");
        progress.setAlpha(1f);
        progress.setVisibility(View.VISIBLE);
        ImageLoader.loadOriginal(url, 2400, bitmap -> {
            image.animate().cancel();
            image.setAlpha(0.72f);
            image.setImageBitmap(bitmap);
            image.post(image::fitImage);
            progress.setVisibility(View.GONE);
            original.setText("已是原图");
            image.animate().alpha(1f).setDuration(130).start();
        });
        image.postDelayed(() -> {
            if (progress.getVisibility() == View.VISIBLE) {
                original.setEnabled(true);
                original.setText("重试原图");
                Toast.makeText(this, "原图仍在加载或网络较慢", Toast.LENGTH_SHORT).show();
            }
        }, 15000);
    }

    private TextView control(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.argb(155, 44, 47, 51));
        return view;
    }

    private void closeViewer() {
        if (closing) return;
        closing = true;
        image.animate().scaleX(0.985f).scaleY(0.985f).alpha(0f)
                .setDuration(140)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        root.animate().alpha(0f).setDuration(150)
                .start();
        root.postDelayed(() -> {
            if (!isFinishing()) {
                finish();
                overridePendingTransition(0, 0);
            }
        }, 155);
    }

    @Override public void onBackPressed() {
        closeViewer();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
