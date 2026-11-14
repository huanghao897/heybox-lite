package com.ronan.heyboxlite;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

/** OS lifecycle and launcher contract for the Compose image viewer. */
public final class ImageViewerActivity extends ComponentActivity {
    static final String EXTRA_URL = "image_url";
    static final String EXTRA_URLS = "image_urls";
    static final String EXTRA_INDEX = "image_index";
    static final String EXTRA_ORIGIN_X = "origin_x";
    static final String EXTRA_ORIGIN_Y = "origin_y";
    static final String EXTRA_ORIGIN_WIDTH = "origin_width";
    static final String EXTRA_ORIGIN_HEIGHT = "origin_height";
    static final String EXTRA_PREVIEW_ID = "preview_id";
    static final String EXTRA_LOAD_ORIGINAL_IMMEDIATELY = "load_original_immediately";
    private ComposeImageViewerHost viewer;

    static long preparePreview(String url, Bitmap bitmap, ImageView source) {
        return ImagePreviewStore.prepare(url, bitmap, source);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ImageLoader.init(this);
        Compat.colorSystemBars(getWindow(), Color.rgb(16, 17, 19));
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        viewer = new ComposeImageViewerHost(this, new SessionStore(this));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { viewer.back(); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (viewer != null) viewer.setActive(true);
    }

    @Override protected void onPause() {
        if (viewer != null) viewer.setActive(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (viewer != null) viewer.close();
        viewer = null;
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code != ImageViewerDownload.REQUEST_PERMISSION || viewer == null) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            ImageViewerDownload.save(this, viewer.currentUrl());
        } else Toast.makeText(this, "没有存储权限，无法保存图片", Toast.LENGTH_SHORT).show();
    }
}
