package com.openzen.heyboxcommunity;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ImageViewerActivity extends Activity {
    private static final int REQUEST_SAVE_IMAGE = 41;
    static final String EXTRA_URL = "image_url";
    static final String EXTRA_ORIGIN_X = "origin_x";
    static final String EXTRA_ORIGIN_Y = "origin_y";
    static final String EXTRA_ORIGIN_WIDTH = "origin_width";
    static final String EXTRA_ORIGIN_HEIGHT = "origin_height";

    private FrameLayout root;
    private ZoomImageView image;
    private LoadingSpinnerView progress;
    private TextView original;
    private TextView save;
    private String url;
    private boolean closing;
    private boolean destroyed;
    private SessionStore session;
    private static String pendingPreviewUrl;
    private static Bitmap pendingPreviewBitmap;

    static void preparePreview(String sourceUrl, Bitmap bitmap) {
        pendingPreviewUrl = sourceUrl;
        pendingPreviewBitmap = bitmap;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ImageLoader.init(this);
        Compat.colorSystemBars(getWindow(), Color.rgb(26, 28, 31));
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        session = new SessionStore(this);
        url = getIntent().getStringExtra(EXTRA_URL);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(222, 27, 29, 32));
        image = new ZoomImageView(this);
        image.setOnBlankClickListener(this::closeViewer);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8), 0, dp(8), 0);

        TextView back = control("返回");
        back.setOnClickListener(view -> closeViewer());
        controls.addView(back, new LinearLayout.LayoutParams(0, dp(38), 1f));

        original = control("原图");
        original.setOnClickListener(view -> loadOriginal());
        if (session.originalImages()) {
            LinearLayout.LayoutParams originalParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
            originalParams.leftMargin = dp(8);
            controls.addView(original, originalParams);
        }
        save = control("保存图片");
        save.setOnClickListener(view -> saveImage());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        saveParams.leftMargin = dp(8);
        controls.addView(save, saveParams);

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(-1, dp(46), Gravity.BOTTOM);
        controlsParams.leftMargin = dp(12);
        controlsParams.rightMargin = dp(12);
        controlsParams.bottomMargin = dp(12);
        root.addView(controls, controlsParams);

        progress = new LoadingSpinnerView(this);
        progress.setColor(Color.argb(210, 235, 238, 241));
        root.addView(progress, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        setContentView(root);
        prepareEnterAnimation();
        if (!showPreparedPreview()) {
            loadPreview();
        }
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
        ImageLoader.load(url, previewTarget(), bitmap -> {
            if (destroyed || isFinishing()) return;
            if (bitmap == null) {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
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

    private boolean showPreparedPreview() {
        Bitmap bitmap = null;
        if (url != null && url.equals(pendingPreviewUrl)) {
            bitmap = pendingPreviewBitmap;
        }
        pendingPreviewUrl = null;
        pendingPreviewBitmap = null;
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        progress.setVisibility(View.GONE);
        image.animate().cancel();
        image.setAlpha(1f);
        image.setScaleX(1f);
        image.setScaleY(1f);
        image.setImageBitmap(bitmap);
        image.post(image::fitImage);
        return true;
    }

    private void loadOriginal() {
        original.setEnabled(false);
        original.setText("加载中");
        progress.setAlpha(1f);
        progress.setVisibility(View.VISIBLE);
        ImageLoader.loadOriginal(url, 2400, bitmap -> {
            if (destroyed || isFinishing()) return;
            if (bitmap == null) {
                progress.setVisibility(View.GONE);
                original.setEnabled(true);
                original.setText("重试原图");
                Toast.makeText(this, "原图加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
            image.animate().cancel();
            image.setAlpha(0.72f);
            image.setImageBitmap(bitmap);
            image.post(image::fitImage);
            progress.setVisibility(View.GONE);
            original.setText("已是原图");
            image.animate().alpha(1f).setDuration(130).start();
        });
        image.postDelayed(() -> {
            if (destroyed || isFinishing()) return;
            if (progress.getVisibility() == View.VISIBLE) {
                original.setEnabled(true);
                original.setText("重试原图");
                Toast.makeText(this, "原图仍在加载或网络较慢", Toast.LENGTH_SHORT).show();
            }
        }, 15000);
    }

    private void saveImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_SAVE_IMAGE);
            return;
        }
        String source = ImageLoader.originalUrl(url);
        if (source.isEmpty()) {
            Toast.makeText(this, "图片地址不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException();
            String extension = imageExtension(source);
            String fileName = "heybox-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                    .format(new Date()) + "." + extension;
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(source));
            request.setTitle("heybox Lite 图片");
            request.setDescription("正在保存到相册");
            request.setMimeType(imageMimeType(extension));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES,
                    "heybox Lite/" + fileName);
            request.allowScanningByMediaScanner();
            manager.enqueue(request);
            Toast.makeText(this, "正在保存到相册", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String imageExtension(String source) {
        String lower = source.toLowerCase(Locale.US);
        if (lower.endsWith(".gif")) return "gif";
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".webp")) return "webp";
        if (lower.endsWith(".jpeg")) return "jpeg";
        return "jpg";
    }

    private String imageMimeType(String extension) {
        if ("gif".equals(extension)) return "image/gif";
        if ("png".equals(extension)) return "image/png";
        if ("webp".equals(extension)) return "image/webp";
        return "image/jpeg";
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_SAVE_IMAGE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveImage();
        } else {
            Toast.makeText(this, "没有存储权限，无法保存图片", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView control(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(142, 42, 45, 49));
        background.setCornerRadius(dp(19));
        Compat.setBackground(view, background);
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

    @Override protected void onDestroy() {
        destroyed = true;
        if (image != null) {
            ImageLoader.cancel(image);
        }
        super.onDestroy();
    }

    private int previewTarget() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        return Math.max(720, Math.min(1800, Math.max(width, height)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
