package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Small painted controls share the bottom arc; the image keeps its full viewport. */
final class ImageViewerRoundChrome extends FrameLayout {
    private View close;
    private TextView counter;
    private View download;
    private TextView original;

    private ImageViewerRoundChrome(Context context) {
        super(context);
    }

    static FrameLayout create(Context context, boolean round) {
        return round ? new ImageViewerRoundChrome(context) : new FrameLayout(context);
    }

    static void bind(FrameLayout root, View close, TextView counter, View download,
                     TextView original) {
        if (!(root instanceof ImageViewerRoundChrome)) return;
        ImageViewerRoundChrome layout = (ImageViewerRoundChrome) root;
        layout.close = close;
        layout.counter = counter;
        layout.download = download;
        layout.original = original;
        original.setVisibility(GONE);
        counter.setTextSize(10);
        counter.setIncludeFontPadding(false);
        counter.setPadding(layout.dp(6), layout.dp(2), layout.dp(6), layout.dp(2));
        counter.setSingleLine(true);
        counter.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        layout.compactIcon(close);
        layout.compactIcon(download);
        layout.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int diameter = Math.min(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
        if (counter != null && diameter > 0) {
            counter.setMaxWidth(Math.max(1, Math.round(diameter * 0.6f)));
            original.setVisibility(GONE);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        int diameter = Math.min(getWidth(), getHeight());
        float radius = Math.max(1f, diameter / 2f - dp(2));
        int gap = dp(4);
        int rowWidth = width(close) + width(download)
                + (visible(close) && visible(download) ? gap : 0);
        int rowHeight = Math.max(height(close), height(download));
        int rowLeft = (getWidth() - rowWidth) / 2;
        float farX = Math.max(centerX - rowLeft, rowLeft + rowWidth - centerX);
        int rowBottom = Math.min(getHeight() - dp(6),
                (int) Math.floor(centerY + chord(radius, farX)));
        if (visible(close)) place(close, rowLeft, rowBottom - rowHeight);
        if (visible(download)) place(download, rowLeft + rowWidth - width(download),
                rowBottom - rowHeight);
        if (visible(counter)) {
            int x = (getWidth() - counter.getMeasuredWidth()) / 2;
            float halfWidth = Math.max(centerX - x, x + counter.getMeasuredWidth() - centerX);
            int y = Math.max(dp(6), (int) Math.ceil(centerY - chord(radius, halfWidth)));
            place(counter, x, y);
        }
    }

    private void compactIcon(View view) {
        if (view == null) return;
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        // Paint a 28dp circle and a 16dp icon inside the existing 44dp touch target.
        params.width = dp(44);
        params.height = dp(44);
        Drawable background = view.getBackground();
        if (background != null) Compat.setBackground(view, new InsetDrawable(background, dp(8)));
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
    }

    private static boolean visible(View view) {
        return view != null && view.getVisibility() != GONE;
    }

    private static int width(View view) { return visible(view) ? view.getMeasuredWidth() : 0; }
    private static int height(View view) { return visible(view) ? view.getMeasuredHeight() : 0; }

    private static float chord(float radius, float distance) {
        return (float) Math.sqrt(Math.max(0f, radius * radius - distance * distance));
    }

    private static void place(View view, int x, int y) {
        view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
