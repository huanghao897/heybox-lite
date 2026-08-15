package com.ronan.heyboxlite;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ImageViewerActivity extends Activity {
    private static final int REQUEST_SAVE_IMAGE = 41;
    static final String EXTRA_URL = "image_url";
    static final String EXTRA_URLS = "image_urls";
    static final String EXTRA_INDEX = "image_index";
    static final String EXTRA_ORIGIN_X = "origin_x";
    static final String EXTRA_ORIGIN_Y = "origin_y";
    static final String EXTRA_ORIGIN_WIDTH = "origin_width";
    static final String EXTRA_ORIGIN_HEIGHT = "origin_height";

    private FrameLayout root;
    private View backdrop;
    private GalleryPager pager;
    private FrameLayout[] imagePages;
    private ZoomImageView[] images;
    private LoadingSpinnerView[] spinners;
    private boolean[] loaded;
    private boolean[] originalLoaded;
    private boolean[] longCandidates;
    private boolean[] longLoading;
    private int[] longRequestIds;
    private ScrollView[] longViews;
    private long[] sizes;
    private String[] urls;
    private int current;
    private TextView counter;
    private TextView original;
    private ImageView download;
    private ImageView closeButton;
    private boolean closing;
    private boolean destroyed;
    private boolean roundDisplay;
    private boolean pageTransitionStarted;
    private boolean pullingImage;
    private SessionStore session;
    private static String pendingPreviewUrl;
    private static WeakReference<Bitmap> pendingPreviewBitmap;

    static void preparePreview(String sourceUrl, Bitmap bitmap) {
        pendingPreviewUrl = sourceUrl;
        pendingPreviewBitmap = new WeakReference<>(bitmap);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ImageLoader.init(this);
        Compat.colorSystemBars(getWindow(), Color.rgb(26, 28, 31));
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        session = new SessionStore(this);
        Motions.setLevel(session.motionLevel());
        roundDisplay = session.usesRoundLayout();

        urls = resolveUrls();
        current = Math.max(0, Math.min(urls.length - 1, getIntent().getIntExtra(EXTRA_INDEX, 0)));
        int count = urls.length;
        imagePages = new FrameLayout[count];
        images = new ZoomImageView[count];
        spinners = new LoadingSpinnerView[count];
        loaded = new boolean[count];
        originalLoaded = new boolean[count];
        longCandidates = new boolean[count];
        longLoading = new boolean[count];
        longRequestIds = new int[count];
        longViews = new ScrollView[count];
        sizes = new long[count];
        for (int i = 0; i < count; i++) sizes[i] = -1L;

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        backdrop = new View(this);
        backdrop.setBackgroundColor(Color.rgb(16, 17, 19));
        backdrop.setAlpha(Motions.off() ? 1.0f : 0.0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        pager = new GalleryPager(this);
        for (int i = 0; i < count; i++) {
            FrameLayout page = new FrameLayout(this);
            ZoomImageView view = new ZoomImageView(this);
            view.setRoundDisplay(roundDisplay);
            view.setOnBlankClickListener(this::closeViewer);
            final int pageIndex = i;
            view.setGestureListener(new ZoomImageView.GestureListener() {
                @Override public void onPull(float dx, float dy, float progress) {
                    updatePull(pageIndex, dx, dy, progress);
                }

                @Override public void onPullEnd(boolean dismiss, float direction) {
                    finishPull(pageIndex, dismiss, direction);
                }
            });
            page.addView(view, new FrameLayout.LayoutParams(-1, -1));
            LoadingSpinnerView spinner = new LoadingSpinnerView(this);
            spinner.setColor(Color.argb(210, 235, 238, 241));
            page.addView(spinner, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            pager.addView(page, new ViewGroup.LayoutParams(-1, -1));
            imagePages[i] = page;
            images[i] = view;
            spinners[i] = spinner;
        }
        pager.setPage(current);
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        // 右上角页码：仅多图显示
        counter = new TextView(this);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(12);
        counter.setGravity(Gravity.CENTER);
        counter.setTypeface(counter.getTypeface(), android.graphics.Typeface.BOLD);
        counter.setPadding(dp(10), dp(3), dp(10), dp(3));
        GradientDrawable counterBg = new GradientDrawable();
        counterBg.setColor(Color.argb(120, 0, 0, 0));
        counterBg.setCornerRadius(dp(12));
        Compat.setBackground(counter, counterBg);
        FrameLayout.LayoutParams counterParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.END);
        counterParams.topMargin = chromeInset();
        counterParams.rightMargin = chromeInset();
        root.addView(counter, counterParams);
        counter.setVisibility(count > 1 ? View.VISIBLE : View.GONE);

        // 紧凑下载按钮：左下角小圆钮，不占地方、不遮挡图片
        download = new ImageView(this);
        download.setImageResource(R.drawable.ic_download);
        download.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        download.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable downloadBg = new GradientDrawable();
        downloadBg.setColor(Color.argb(120, 0, 0, 0));
        downloadBg.setShape(GradientDrawable.OVAL);
        Compat.setBackground(download, downloadBg);
        download.setOnClickListener(view -> saveImage());
        FrameLayout.LayoutParams downloadParams = new FrameLayout.LayoutParams(dp(40), dp(40),
                Gravity.BOTTOM | Gravity.START);
        downloadParams.leftMargin = chromeInset();
        downloadParams.bottomMargin = chromeInset();
        root.addView(download, downloadParams);

        if (roundDisplay) {
            closeButton = new ImageView(this);
            Drawable closeIcon = Compat.tintedDrawable(this, R.drawable.ic_close, Color.WHITE);
            if (closeIcon != null) closeButton.setImageDrawable(closeIcon);
            closeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            closeButton.setPadding(dp(10), dp(10), dp(10), dp(10));
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setColor(Color.argb(120, 0, 0, 0));
            closeBg.setShape(GradientDrawable.OVAL);
            Compat.setBackground(closeButton, closeBg);
            closeButton.setContentDescription("关闭图片");
            closeButton.setOnClickListener(view -> {
                UiComponents.press(view);
                closeViewer();
            });
            FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                    dp(40), dp(40), Gravity.TOP | Gravity.START);
            closeParams.leftMargin = chromeInset();
            closeParams.topMargin = chromeInset();
            root.addView(closeButton, closeParams);
        }

        // 查看原图（大小）：仅在允许查看原图时显示，居中底部
        original = new TextView(this);
        original.setTextColor(Color.WHITE);
        original.setTextSize(12);
        original.setGravity(Gravity.CENTER);
        original.setText("查看原图");
        original.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable originalBg = new GradientDrawable();
        originalBg.setColor(Color.argb(120, 0, 0, 0));
        originalBg.setCornerRadius(dp(16));
        Compat.setBackground(original, originalBg);
        original.setOnClickListener(view -> loadOriginal());
        FrameLayout.LayoutParams originalParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        originalParams.bottomMargin = chromeInset();
        root.addView(original, originalParams);
        original.setVisibility(session.originalImages() ? View.VISIBLE : View.GONE);

        setContentView(root);
        if (roundDisplay) root.post(this::positionRoundChrome);
        prepareEnterAnimation();
        bindPage(current, true);
        preloadNeighbors(current);
    }

    private String[] resolveUrls() {
        String[] many = getIntent().getStringArrayExtra(EXTRA_URLS);
        if (many != null && many.length > 0) return many;
        String single = getIntent().getStringExtra(EXTRA_URL);
        return new String[]{single == null ? "" : single};
    }

    private void prepareEnterAnimation() {
        backdrop.animate().cancel();
        pager.animate().cancel();
        pageTransitionStarted = false;
        setChromeAlpha(Motions.off() ? 1.0f : 0.0f);
        if (Motions.off() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            backdrop.setAlpha(1.0f);
            setChromeAlpha(1.0f);
            return;
        }
        backdrop.setAlpha(0.0f);
        root.post(() -> {
            if (destroyed || isFinishing()) return;
            long duration = Motions.full() ? 240L : 205L;
            backdrop.animate().alpha(1.0f).setDuration(duration).start();
            root.postDelayed(() -> {
                if (!destroyed && !isFinishing() && !pullingImage) setChromeAlpha(1.0f);
            }, Math.min(80L, duration / 3L));
        });
    }

    private void startPageEnter(int index) {
        if (pageTransitionStarted || destroyed || isFinishing()
                || index != current || images[index].getDrawable() == null) return;
        if (root.getWidth() == 0 || root.getHeight() == 0
                || imagePages[index].getWidth() == 0) {
            images[index].post(() -> startPageEnter(index));
            return;
        }
        pageTransitionStarted = true;
        FrameLayout page = imagePages[index];
        if (Motions.off()) {
            Motions.reset(page);
            return;
        }
        ZoomImageView image = images[index];
        android.graphics.RectF imageRect = new android.graphics.RectF();
        image.imageDisplayRect(imageRect);
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        float originX = getIntent().getIntExtra(EXTRA_ORIGIN_X, root.getWidth() / 2)
                - rootLocation[0];
        float originY = getIntent().getIntExtra(EXTRA_ORIGIN_Y, root.getHeight() / 2)
                - rootLocation[1];
        float originWidth = getIntent().getIntExtra(EXTRA_ORIGIN_WIDTH, 0);
        float originHeight = getIntent().getIntExtra(EXTRA_ORIGIN_HEIGHT, 0);
        float scale = 0.94f;
        if (imageRect.width() > 0.0f && imageRect.height() > 0.0f
                && originWidth > 0.0f && originHeight > 0.0f) {
            scale = Math.max(0.08f, Math.min(0.96f,
                    Math.min(originWidth / imageRect.width(),
                            originHeight / imageRect.height())));
        }
        page.setPivotX(page.getWidth() / 2.0f);
        page.setPivotY(page.getHeight() / 2.0f);
        page.setScaleX(scale);
        page.setScaleY(scale);
        page.setTranslationX(originX - page.getWidth() / 2.0f);
        page.setTranslationY(originY - page.getHeight() / 2.0f);
        long duration = Motions.full() ? 240L : 205L;
        page.animate().scaleX(1.0f).scaleY(1.0f)
                .translationX(0.0f).translationY(0.0f)
                .setDuration(duration)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .withEndAction(() -> Motions.reset(page))
                .start();
    }

    private void updatePull(int index, float dx, float dy, float progress) {
        if (destroyed || closing || index != current) return;
        FrameLayout page = imagePages[index];
        if (!pullingImage) {
            pullingImage = true;
            page.animate().cancel();
            backdrop.animate().cancel();
        }
        float scale = 1.0f - Math.min(0.16f, progress * 0.20f);
        page.setTranslationX(dx);
        page.setTranslationY(dy);
        page.setScaleX(scale);
        page.setScaleY(scale);
        backdrop.setAlpha(1.0f - Math.min(0.82f, progress * 0.82f));
        setChromeAlpha(1.0f - Math.min(0.75f, progress * 0.75f));
    }

    private void finishPull(int index, boolean dismiss, float direction) {
        if (destroyed || index != current) return;
        pullingImage = false;
        FrameLayout page = imagePages[index];
        if (dismiss) {
            closing = true;
            long duration = Motions.off() ? 0L : (Motions.full() ? 220L : 180L);
            page.animate().translationY(direction * Math.max(1, root.getHeight()))
                    .translationX(page.getTranslationX() * 1.25f)
                    .scaleX(0.84f).scaleY(0.84f).alpha(0.0f)
                    .setDuration(duration)
                    .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                    .withEndAction(this::finishViewer)
                    .start();
            backdrop.animate().alpha(0.0f).setDuration(duration).start();
            setChromeAlpha(0.0f);
            return;
        }
        page.animate().translationX(0.0f).translationY(0.0f)
                .scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                .setDuration(Motions.off() ? 0L : 190L)
                .setInterpolator(MotionSpec.EASE_OUT)
                .withEndAction(() -> Motions.reset(page))
                .start();
        backdrop.animate().alpha(1.0f).setDuration(Motions.off() ? 0L : 190L).start();
        setChromeAlpha(1.0f);
    }

    private void setChromeAlpha(float alpha) {
        if (counter != null) counter.setAlpha(alpha);
        if (download != null) download.setAlpha(alpha);
        if (original != null) original.setAlpha(alpha);
        if (closeButton != null) closeButton.setAlpha(alpha);
    }

    /** 翻到某页时同步：加载预览、更新页码、复位并刷新“查看原图（大小）”。 */
    private void onPageSelected(int index) {
        if (index == current) return;
        int previous = current;
        cancelLongLoad(previous);
        current = index;
        bindPage(index, false);
        preloadNeighbors(index);
        counter.setText((index + 1) + "/" + urls.length);
        refreshOriginalLabel();
        pager.postDelayed(() -> {
            if (!destroyed && current != previous) releaseLongPage(previous);
        }, 300L);
    }

    private void bindPage(int index, boolean startup) {
        counter.setText((index + 1) + "/" + urls.length);
        if (startup && showPreparedPreview(index)) {
            probeSize(index);
            refreshOriginalLabel();
            return;
        }
        ensurePreview(index);
        probeSize(index);
        refreshOriginalLabel();
    }

    private void preloadNeighbors(int index) {
        if (index - 1 >= 0) ensurePreview(index - 1);
        if (index + 1 < urls.length) ensurePreview(index + 1);
    }

    private void ensurePreview(int index) {
        if (loaded[index]) {
            maybeLoadLongPage(index);
            return;
        }
        loaded[index] = true;
        final ZoomImageView view = images[index];
        final LoadingSpinnerView spinner = spinners[index];
        spinner.setVisibility(View.VISIBLE);
        ImageLoader.load(urls[index], previewTarget(), bitmap -> {
            if (destroyed || isFinishing()) return;
            spinner.animate().alpha(0f).setDuration(90).start();
            spinner.postDelayed(() -> spinner.setVisibility(View.GONE), 100);
            if (bitmap == null) {
                loaded[index] = false;
                if (view.getDrawable() == null) {
                    Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            view.setImageBitmap(bitmap);
            view.post(() -> {
                view.fitImage();
                if (index == current) startPageEnter(index);
            });
            updateLongCandidate(index, bitmap);
        });
    }

    private boolean showPreparedPreview(int index) {
        Bitmap bitmap = null;
        if (urls[index] != null && urls[index].equals(pendingPreviewUrl)) {
            bitmap = pendingPreviewBitmap == null ? null : pendingPreviewBitmap.get();
        }
        pendingPreviewUrl = null;
        pendingPreviewBitmap = null;
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        loaded[index] = true;
        spinners[index].setVisibility(View.GONE);
        images[index].setImageBitmap(bitmap);
        images[index].post(() -> {
            images[index].fitImage();
            startPageEnter(index);
        });
        updateLongCandidate(index, bitmap);
        return true;
    }

    private void updateLongCandidate(int index, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || GifSupport.isGifUrl(urls[index])) return;
        longCandidates[index] = bitmap.getWidth() > 0
                && bitmap.getHeight() > bitmap.getWidth() * 3L;
        maybeLoadLongPage(index);
    }

    private void maybeLoadLongPage(int index) {
        if (destroyed || index != current || !longCandidates[index]
                || longLoading[index] || longViews[index] != null) {
            return;
        }
        longLoading[index] = true;
        int requestId = ++longRequestIds[index];
        ImageLoader.loadLongImageTiles(urls[index], longImageTargetWidth(),
                new ImageLoader.LongImageCallback() {
                    @Override
                    public boolean cancelled() {
                        return destroyed || index != current
                                || requestId != longRequestIds[index];
                    }

                    @Override
                    public void onStart(int width, int height, int tileCount) {
                        if (cancelled()) return;
                        ScrollView scroll = new ScrollView(ImageViewerActivity.this);
                        scroll.setFillViewport(false);
                        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                        int inset = roundImageInset();
                        if (inset > 0) {
                            scroll.setPadding(inset, inset, inset, inset);
                            scroll.setClipToPadding(false);
                        }
                        LinearLayout tiles = new LinearLayout(ImageViewerActivity.this);
                        tiles.setOrientation(LinearLayout.VERTICAL);
                        scroll.addView(tiles, new ScrollView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                        scroll.setVisibility(View.INVISIBLE);
                        imagePages[index].addView(scroll,
                                new FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT));
                        longViews[index] = scroll;
                    }

                    @Override
                    public void onTile(int tileIndex, Bitmap bitmap) {
                        ScrollView scroll = longViews[index];
                        if (cancelled() || scroll == null || bitmap == null) {
                            recycle(bitmap);
                            return;
                        }
                        View child = scroll.getChildAt(0);
                        if (!(child instanceof LinearLayout)) {
                            recycle(bitmap);
                            return;
                        }
                        ImageView tile = new ImageView(ImageViewerActivity.this);
                        tile.setScaleType(ImageView.ScaleType.FIT_XY);
                        tile.setImageBitmap(bitmap);
                        int width = longImageTargetWidth();
                        int height = Math.max(1, Math.round(
                                width * (bitmap.getHeight() / (float) bitmap.getWidth())));
                        ((LinearLayout) child).addView(tile,
                                new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT, height));
                        if (tileIndex == 0) {
                            images[index].setVisibility(View.INVISIBLE);
                            scroll.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onComplete(boolean success) {
                        if (requestId != longRequestIds[index]) return;
                        longLoading[index] = false;
                        if (!success || longViews[index] == null) {
                            releaseLongPage(index);
                            return;
                        }
                        originalLoaded[index] = true;
                        if (index == current) refreshOriginalLabel();
                    }
                });
    }

    private void releaseLongPage(int index) {
        if (index < 0 || index >= longViews.length) return;
        cancelLongLoad(index);
        ScrollView scroll = longViews[index];
        longViews[index] = null;
        if (scroll != null) {
            View child = scroll.getChildAt(0);
            if (child instanceof ViewGroup) {
                ViewGroup tiles = (ViewGroup) child;
                for (int i = 0; i < tiles.getChildCount(); i++) {
                    View tile = tiles.getChildAt(i);
                    if (tile instanceof ImageView) {
                        Drawable drawable = ((ImageView) tile).getDrawable();
                        if (drawable instanceof BitmapDrawable) {
                            recycle(((BitmapDrawable) drawable).getBitmap());
                        }
                        ((ImageView) tile).setImageDrawable(null);
                    }
                }
            }
            imagePages[index].removeView(scroll);
        }
        images[index].setVisibility(View.VISIBLE);
    }

    private void cancelLongLoad(int index) {
        longRequestIds[index]++;
        longLoading[index] = false;
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private void probeSize(int index) {
        // 大小只用于“查看原图”按钮，按钮不显示时无需请求
        if (!session.originalImages()) return;
        if (sizes[index] >= 0L) {
            refreshOriginalLabel();
            return;
        }
        ImageLoader.probeOriginalSize(urls[index], bytes -> {
            if (destroyed || isFinishing()) return;
            sizes[index] = bytes;
            if (index == current) refreshOriginalLabel();
        });
    }

    private void refreshOriginalLabel() {
        if (original.getVisibility() != View.VISIBLE) return;
        if (originalLoaded[current]) {
            original.setText("已是原图");
            original.setEnabled(false);
            return;
        }
        original.setEnabled(true);
        long size = sizes[current];
        original.setText(size > 0 ? "查看原图（" + formatSize(size) + "）" : "查看原图");
    }

    private String formatSize(long bytes) {
        if (bytes <= 0L) return "";
        if (bytes < 1024L) return bytes + "B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return trimDecimal(kb) + "KB";
        return trimDecimal(kb / 1024.0) + "MB";
    }

    private String trimDecimal(double value) {
        String text = String.format(Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private void loadOriginal() {
        final int index = current;
        if (originalLoaded[index]) return;
        original.setEnabled(false);
        original.setText("加载中");
        final LoadingSpinnerView spinner = spinners[index];
        spinner.setAlpha(1f);
        spinner.setVisibility(View.VISIBLE);
        final ZoomImageView view = images[index];
        ImageLoader.loadOriginal(urls[index], 2400, bitmap -> {
            if (destroyed || isFinishing()) return;
            spinner.setVisibility(View.GONE);
            if (bitmap == null) {
                original.setEnabled(true);
                original.setText("重试原图");
                Toast.makeText(this, "原图加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
            originalLoaded[index] = true;
            view.animate().cancel();
            view.setAlpha(0.72f);
            view.setImageBitmap(bitmap);
            view.post(view::fitImage);
            view.animate().alpha(1f).setDuration(130).start();
            updateLongCandidate(index, bitmap);
            if (index == current) refreshOriginalLabel();
        });
    }

    private void saveImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_SAVE_IMAGE);
            return;
        }
        String source = ImageLoader.originalUrl(urls[current]);
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

    private void closeViewer() {
        if (closing) return;
        closing = true;
        FrameLayout page = imagePages[current];
        page.animate().cancel();
        backdrop.animate().cancel();
        setChromeAlpha(0.0f);
        if (Motions.off()) {
            finishViewer();
            return;
        }

        float scale = 0.94f;
        float translationX = 0.0f;
        float translationY = 0.0f;
        int requestedIndex = Math.max(0, Math.min(urls.length - 1,
                getIntent().getIntExtra(EXTRA_INDEX, 0)));
        if (current == requestedIndex && !images[current].isZoomed()) {
            android.graphics.RectF imageRect = new android.graphics.RectF();
            images[current].imageDisplayRect(imageRect);
            int[] rootLocation = new int[2];
            root.getLocationOnScreen(rootLocation);
            float originWidth = getIntent().getIntExtra(EXTRA_ORIGIN_WIDTH, 0);
            float originHeight = getIntent().getIntExtra(EXTRA_ORIGIN_HEIGHT, 0);
            if (imageRect.width() > 0.0f && imageRect.height() > 0.0f
                    && originWidth > 0.0f && originHeight > 0.0f) {
                scale = Math.max(0.08f, Math.min(0.96f,
                        Math.min(originWidth / imageRect.width(),
                                originHeight / imageRect.height())));
                float originX = getIntent().getIntExtra(
                        EXTRA_ORIGIN_X, root.getWidth() / 2) - rootLocation[0];
                float originY = getIntent().getIntExtra(
                        EXTRA_ORIGIN_Y, root.getHeight() / 2) - rootLocation[1];
                translationX = originX - page.getWidth() / 2.0f;
                translationY = originY - page.getHeight() / 2.0f;
            }
        }
        long duration = Motions.full() ? 220L : 180L;
        page.animate().scaleX(scale).scaleY(scale).translationX(translationX)
                .translationY(translationY).alpha(0.0f)
                .setDuration(duration)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .withEndAction(this::finishViewer)
                .start();
        backdrop.animate().alpha(0.0f).setDuration(duration).start();
    }

    private void finishViewer() {
        if (destroyed || isFinishing()) return;
        finish();
        overridePendingTransition(0, 0);
    }

    @Override public void onBackPressed() {
        closeViewer();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (root != null) root.animate().cancel();
        if (backdrop != null) backdrop.animate().cancel();
        if (pager != null) {
            pager.animate().cancel();
            pager.cancelSettle();
        }
        if (imagePages != null) {
            for (FrameLayout page : imagePages) {
                if (page != null) page.animate().cancel();
            }
        }
        if (longViews != null) {
            for (int i = 0; i < longViews.length; i++) releaseLongPage(i);
        }
        if (images != null) {
            for (ZoomImageView view : images) {
                if (view != null) ImageLoader.cancel(view);
            }
        }
        super.onDestroy();
    }

    private int previewTarget() {
        int width = getResources().getDisplayMetrics().widthPixels;
        return Math.max(480, Math.min(1440, Math.round(width * 1.25f)));
    }

    private int chromeInset() {
        return dp(roundDisplay ? 36 : 16);
    }

    private void positionRoundChrome() {
        if (destroyed || root == null || root.getWidth() == 0 || root.getHeight() == 0) return;
        int edge = Math.max(dp(10), Math.round(Math.min(root.getWidth(), root.getHeight()) * 0.06f));
        int iconSize = dp(44);

        if (closeButton != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) closeButton.getLayoutParams();
            params.width = iconSize;
            params.height = iconSize;
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.leftMargin = 0;
            params.rightMargin = 0;
            params.topMargin = edge;
            params.bottomMargin = 0;
            closeButton.setLayoutParams(params);
        }
        if (counter != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) counter.getLayoutParams();
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            params.leftMargin = 0;
            params.rightMargin = edge;
            params.topMargin = 0;
            params.bottomMargin = 0;
            counter.setLayoutParams(params);
        }
        if (download != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) download.getLayoutParams();
            params.width = iconSize;
            params.height = iconSize;
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            params.leftMargin = edge;
            params.rightMargin = 0;
            params.topMargin = 0;
            params.bottomMargin = 0;
            download.setLayoutParams(params);
        }
        if (original != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) original.getLayoutParams();
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.leftMargin = 0;
            params.rightMargin = 0;
            params.topMargin = 0;
            params.bottomMargin = edge;
            original.setLayoutParams(params);
        }
    }

    private int roundImageInset() {
        if (!roundDisplay) return 0;
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        return Math.round(Math.min(width, height) * 0.10f);
    }

    private int longImageTargetWidth() {
        int width = getResources().getDisplayMetrics().widthPixels;
        return Math.max(320, width - roundImageInset() * 2);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 横滑图集：一屏一张，未放大时水平拖动翻页，放大时手势交给缩放/平移。 */
    private final class GalleryPager extends ViewGroup {
        private final int touchSlop;
        private float startX;
        private float startY;
        private long startTime;
        private int startScrollX;
        private int page;
        private boolean dragging;
        private boolean ignoring;
        private boolean preservingSecondTap;
        private ValueAnimator settleAnimator;

        GalleryPager(Context context) {
            super(context);
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        void setPage(int value) {
            this.page = value;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            int childWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int childHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidth, childHeight);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).layout(i * width, 0, (i + 1) * width, height);
            }
            if (!this.dragging && this.settleAnimator == null) {
                scrollTo(this.page * width, 0);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (getChildCount() < 2) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelSettle();
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.startTime = event.getEventTime();
                    this.startScrollX = getScrollX();
                    this.dragging = false;
                    this.ignoring = false;
                    this.preservingSecondTap = images[current].isSecondTapCandidate(
                            event.getX(), event.getY(), event.getEventTime());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (this.ignoring || this.dragging) break;
                    if (this.preservingSecondTap) {
                        this.ignoring = true;
                        break;
                    }
                    if (event.getPointerCount() > 1
                            || images[current].isZoomed()) {
                        this.ignoring = true;
                        break;
                    }
                    float dx = event.getX() - this.startX;
                    float dy = event.getY() - this.startY;
                    if (Math.abs(dx) > this.touchSlop && Math.abs(dx) > Math.abs(dy) * 1.1f) {
                        this.dragging = true;
                        this.startX = event.getX();
                        this.startScrollX = getScrollX();
                    } else if (Math.abs(dy) > this.touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        this.ignoring = true;
                    }
                    break;
            }
            return this.dragging;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelSettle();
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.startTime = event.getEventTime();
                    this.startScrollX = getScrollX();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (this.dragging) dragTo(event.getX() - this.startX);
                    break;
                case MotionEvent.ACTION_UP:
                    if (this.dragging) {
                        finishDrag(event);
                        performClick();
                    }
                    this.dragging = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    if (this.dragging) settleTo(this.page, true);
                    this.dragging = false;
                    break;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        private void dragTo(float dx) {
            int width = Math.max(1, getWidth());
            int max = Math.max(0, (getChildCount() - 1) * width);
            int next = Math.max(0, Math.min(max, this.startScrollX - Math.round(dx)));
            scrollTo(next, 0);
        }

        private void finishDrag(MotionEvent event) {
            int width = Math.max(1, getWidth());
            float dx = event.getX() - this.startX;
            long duration = Math.max(1L, event.getEventTime() - this.startTime);
            float velocity = (dx * 1000.0f) / duration;
            int target = Math.round(getScrollX() / (float) width);
            if (Math.abs(velocity) > dp(320)) {
                target = velocity < 0.0f ? this.page + 1 : this.page - 1;
            }
            settleTo(Math.max(0, Math.min(getChildCount() - 1, target)), true);
        }

        private void settleTo(int next, boolean animate) {
            cancelSettle();
            if (next != this.page) {
                this.page = next;
                onPageSelected(next);
            }
            int destination = next * Math.max(1, getWidth());
            if (!animate || Math.abs(destination - getScrollX()) < dp(2)) {
                scrollTo(destination, 0);
                return;
            }
            int fromX = getScrollX();
            int duration = Math.max(150, Math.min(280, Math.abs(destination - fromX) / 3));
            this.settleAnimator = ValueAnimator.ofInt(fromX, destination);
            this.settleAnimator.setDuration(duration);
            this.settleAnimator.setInterpolator(new DecelerateInterpolator());
            this.settleAnimator.addUpdateListener(animation ->
                    scrollTo((Integer) animation.getAnimatedValue(), 0));
            this.settleAnimator.start();
        }

        private void cancelSettle() {
            if (this.settleAnimator != null) {
                this.settleAnimator.cancel();
                this.settleAnimator = null;
            }
        }
    }
}
