package com.openzen.heyboxcommunity;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public final class ImageViewerActivity extends Activity {
    static final String EXTRA_URL = "image_url";

    private ZoomImageView image;
    private ProgressBar progress;
    private TextView original;
    private String url;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Compat.colorSystemBars(getWindow(), Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        url = getIntent().getStringExtra(EXTRA_URL);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        image = new ZoomImageView(this);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView back = control("‹ 返回");
        back.setOnClickListener(view -> finish());
        FrameLayout.LayoutParams backParams =
                new FrameLayout.LayoutParams(dp(72), dp(38), Gravity.TOP | Gravity.LEFT);
        root.addView(back, backParams);

        original = control("查看原图");
        original.setOnClickListener(view -> loadOriginal());
        FrameLayout.LayoutParams originalParams =
                new FrameLayout.LayoutParams(dp(92), dp(38), Gravity.TOP | Gravity.RIGHT);
        root.addView(original, originalParams);

        progress = new ProgressBar(this);
        root.addView(progress, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        setContentView(root);
        loadPreview();
    }

    private void loadPreview() {
        progress.setVisibility(View.VISIBLE);
        ImageLoader.load(url, 1600, bitmap -> {
            image.setImageBitmap(bitmap);
            image.post(image::fitImage);
            progress.setVisibility(View.GONE);
        });
    }

    private void loadOriginal() {
        original.setEnabled(false);
        original.setText("加载中");
        progress.setVisibility(View.VISIBLE);
        ImageLoader.loadOriginal(url, 2400, bitmap -> {
            image.setImageBitmap(bitmap);
            image.post(image::fitImage);
            progress.setVisibility(View.GONE);
            original.setText("已是原图");
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
        view.setBackgroundColor(Color.argb(180, 20, 20, 20));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
