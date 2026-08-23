package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class DetailImageGallery {
    interface ImageOpener {
        void open(ImageView source, String[] urls, int index);
    }

    private static final int RETAINED_PAGE_DISTANCE = 1;

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final float uiScale;
    private final float textScale;
    private final int targetPx;
    private final ImageOpener imageOpener;
    private final List<Page> pages = new ArrayList<>();

    DetailImageGallery(Activity activity, SessionStore session, ThemeTokens tokens,
                       boolean roundLayout, int targetPx, ImageOpener imageOpener) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.uiScale = session.uiScale() / 100.0f;
        this.textScale = session.textScale() / 100.0f;
        this.targetPx = targetPx;
        this.imageOpener = imageOpener;
    }

    void addTo(LinearLayout parent, List<String> urls) {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int pagerHeight = roundLayout
                ? Math.min(dp(150), Math.round(screenWidth * 0.58f))
                : Math.min(dp(270), Math.round(screenWidth * 0.85f));
        FrameLayout wrap = new FrameLayout(activity);
        Compat.setBackground(wrap, UiComponents.round(activity,
                placeholderColor(), 7, uiScale));
        Compat.clipToOutline(wrap);

        TextView counter = text("1/" + urls.size(), 10.0f, Color.WHITE);
        LinearLayout dots = new LinearLayout(activity);
        String[] allUrls = urls.toArray(new String[0]);
        ImagePagerCore core = new ImagePagerCore(activity, this::dp, page -> {
            counter.setText((page + 1) + "/" + urls.size());
            updateDots(dots, page);
            bindNear(page);
        });
        for (int index = 0; index < urls.size(); index++) {
            Page page = createPage(urls.get(index), allUrls, index);
            pages.add(page);
            core.addView(page.frame);
        }
        bindNear(0);
        wrap.addView(core, match());
        addCounter(wrap, counter);
        addDots(wrap, dots, urls.size());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, pagerHeight);
        params.topMargin = dp(10);
        parent.addView(wrap, params);
    }

    private Page createPage(String url, String[] allUrls, int position) {
        FrameLayout frame = new FrameLayout(activity);
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        frame.addView(image, match());
        image.setOnClickListener(view -> imageOpener.open(image, allUrls, position));
        return new Page(frame, image, url);
    }

    private void bindNear(int current) {
        for (int index = 0; index < pages.size(); index++) {
            Page page = pages.get(index);
            if (Math.abs(index - current) <= RETAINED_PAGE_DISTANCE) bind(page);
            else release(page);
        }
    }

    private void bind(Page page) {
        if (page.bound) return;
        page.bound = true;
        ImageLoader.intoMeasuredRevealStable(page.image, page.url, targetPx,
                (success, bitmap) -> {
                    if (!page.bound || success || page.image.getDrawable() != null) return;
                    page.failure = text("图片加载失败", 11.0f, tokens.muted);
                    page.failure.setGravity(Gravity.CENTER);
                    page.frame.addView(page.failure, match());
                });
    }

    private void release(Page page) {
        if (!page.bound) return;
        page.bound = false;
        ImageLoader.cancel(page.image);
        page.image.setImageDrawable(null);
        if (page.failure != null) {
            page.frame.removeView(page.failure);
            page.failure = null;
        }
    }

    private void addCounter(FrameLayout wrap, TextView counter) {
        counter.setGravity(Gravity.CENTER);
        counter.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        counter.setPadding(dp(7), dp(2), dp(7), dp(2));
        Compat.setBackground(counter, UiComponents.round(activity,
                0x8C000000, 9, uiScale));
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        params.topMargin = dp(7);
        params.rightMargin = dp(7);
        wrap.addView(counter, params);
    }

    private void addDots(FrameLayout wrap, LinearLayout dots, int count) {
        dots.setGravity(Gravity.CENTER);
        for (int index = 0; index < count; index++) {
            android.view.View dot = new android.view.View(activity);
            Compat.setBackground(dot, UiComponents.round(activity,
                    Color.WHITE, 3, uiScale));
            dot.setAlpha(index == 0 ? 1.0f : 0.4f);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(5), dp(5));
            params.leftMargin = dp(2);
            params.rightMargin = dp(2);
            dots.addView(dot, params);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(7);
        wrap.addView(dots, params);
    }

    private static void updateDots(LinearLayout dots, int page) {
        for (int index = 0; index < dots.getChildCount(); index++) {
            dots.getChildAt(index).setAlpha(index == page ? 1.0f : 0.4f);
        }
    }

    private TextView text(String value, float size, int color) {
        return UiComponents.label(activity, value, size, color, textScale);
    }

    private int placeholderColor() {
        return session.darkMode() ? Color.rgb(28, 30, 32) : Color.rgb(235, 237, 240);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources()
                .getDisplayMetrics().density * uiScale);
    }

    private static final class Page {
        final FrameLayout frame;
        final ImageView image;
        final String url;
        TextView failure;
        boolean bound;

        Page(FrameLayout frame, ImageView image, String url) {
            this.frame = frame;
            this.image = image;
            this.url = url;
        }
    }
}
