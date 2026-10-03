package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.List;

/** Owns comment thumbnail layout and media loading; text/reply rendering stays separate. */
final class CommentMediaRenderer {
    interface Host {
        void openOriginalImage(ImageView source, String url);
    }

    private final Activity activity;
    private final SessionStore session;
    private final LocalCache localCache;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final float uiScale;
    private final Host host;

    CommentMediaRenderer(Activity activity, SessionStore session, LocalCache localCache,
                         ThemeTokens tokens, boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.localCache = localCache;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.uiScale = session.uiScale() / 100.0f;
        this.host = host;
    }

    void addImages(LinearLayout parent, List<CommentData.CommentImage> images,
                   boolean reply) {
        LinearLayout gallery = vertical(Color.TRANSPARENT);
        int columns = this.roundLayout ? 2 : 3;
        int size = this.roundLayout ? (reply ? 48 : 54) : (reply ? 68 : 82);
        for (int start = 0; start < images.size(); start += columns) {
            LinearLayout row = new LinearLayout(this.activity);
            row.setGravity(android.view.Gravity.LEFT);
            int end = Math.min(start + columns, images.size());
            for (int i = start; i < end; i++) {
                ImageView image = commentImage(images.get(i), size);
                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(dp(size), dp(size));
                if (i > start) params.leftMargin = dp(5);
                row.addView(image, params);
            }
            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(-1, dp(size));
            if (start > 0) rowParams.topMargin = dp(5);
            gallery.addView(row, rowParams);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        parent.addView(gallery, params);
    }

    private ImageView commentImage(CommentData.CommentImage source, int sizeDp) {
        ImageView image = new GifImageView(this.activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(image, UiComponents.round(this.activity,
                this.session.darkMode() ? Color.rgb(28, 30, 32)
                        : Color.rgb(235, 237, 240), 6, this.uiScale));
        Compat.clipToOutline(image);
        image.setOnClickListener(view -> this.host.openOriginalImage(image,
                source.originalUrl));
        LazyImageBinder.bind(image, () -> ImageLoader.intoMeasuredStable(image,
                source.previewUrl, Math.max(96, dp(sizeDp)), (success, bitmap) -> {
                    if (success && this.session.playGif()
                            && (source.animated || GifSupport.isGifUrl(source.originalUrl))) {
                        ImageLoader.intoGif(image, source.originalUrl, animated ->
                                this.localCache.log("comment gif "
                                        + (animated ? "started" : "failed")));
                    } else if (!success && image.getDrawable() == null) {
                        image.setImageDrawable(Compat.tintedDrawable(this.activity,
                                R.drawable.il_image, this.tokens.muted));
                        image.setPadding(dp(18), dp(18), dp(18), dp(18));
                    }
                }));
        return image;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this.activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources()
                .getDisplayMetrics().density * this.uiScale);
    }
}
