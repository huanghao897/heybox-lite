package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Repositions measured chrome on every layout, including asynchronous size labels. */
final class ImageViewerRoundChrome extends FrameLayout {
    private View close;
    private TextView counter;
    private View download;
    private TextView original;
    private final Rect closeBounds = new Rect();

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
        counter.setSingleLine(true);
        counter.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        original.setMinWidth(layout.dp(44));
        original.setMinHeight(layout.dp(44));
        original.setPadding(layout.dp(10), original.getPaddingTop(),
                layout.dp(10), original.getPaddingBottom());
        original.setEllipsize(TextUtils.TruncateAt.END);
        layout.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int diameter = Math.min(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
        if (original != null && diameter > 0) {
            int maxWidth = Math.max(1, (int) (diameter * 0.68f));
            if (original.getMaxWidth() != maxWidth) original.setMaxWidth(maxWidth);
            Paint.FontMetricsInt font = original.getPaint().getFontMetricsInt();
            int fontPadding = Math.max(0, font.bottom - font.top - font.descent + font.ascent);
            int textHeight = (int) (diameter * 0.38f) - original.getCompoundPaddingTop()
                    - original.getCompoundPaddingBottom() - fontPadding;
            int lines = Math.max(1, Math.min(3, (int) (textHeight
                    / Math.max(1f, original.getPaint().getFontSpacing()))));
            if (original.getMaxLines() != lines) original.setMaxLines(lines);
            int counterWidth = Math.max(1, (int) (diameter * 0.4f));
            if (counter.getMaxWidth() != counterWidth) counter.setMaxWidth(counterWidth);
            sizeIcon(close);
            sizeIcon(download);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (visible(original) && visible(close) && visible(download)) {
            while (original.getMaxLines() > 1 && original.getMeasuredHeight()
                    > maximumOriginalHeight(getMeasuredWidth(), getMeasuredHeight())) {
                original.setMaxLines(original.getMaxLines() - 1);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        int diameter = Math.min(getWidth(), getHeight());
        float radius = Math.max(1f, diameter / 2f - dp(2));
        int edge = Math.max(dp(10), Math.round(diameter * 0.06f));
        int gap = dp(4);
        closeBounds.setEmpty();
        if (visible(close)) {
            int x = (getWidth() - close.getMeasuredWidth()) / 2;
            float halfWidth = Math.max(centerX - x, x + close.getMeasuredWidth() - centerX);
            int y = Math.max(edge, (int) Math.ceil(centerY - chord(radius, halfWidth)));
            place(close, x, y);
            closeBounds.set(close.getLeft(), close.getTop(), close.getRight(), close.getBottom());
        }

        float rowY = centerY;
        if (visible(original)) {
            int x = (getWidth() - original.getMeasuredWidth()) / 2;
            float halfWidth = Math.max(centerX - x, x + original.getMeasuredWidth() - centerX);
            int y = (int) Math.floor(centerY + chord(radius, halfWidth))
                    - original.getMeasuredHeight();
            place(original, x, y);
            int rowHeight = Math.max(visible(download) ? download.getMeasuredHeight() : 0,
                    visible(counter) ? counter.getMeasuredHeight() : 0);
            rowY = Math.min(rowY, y - gap - rowHeight / 2f);
        }
        if (visible(download)) {
            int y = Math.round(rowY - download.getMeasuredHeight() / 2f);
            float farY = Math.max(Math.abs(y - centerY),
                    Math.abs(y + download.getMeasuredHeight() - centerY));
            int x = Math.max(edge, (int) Math.ceil(centerX - chord(radius, farY)));
            place(download, x, y);
        }
        if (visible(counter)) {
            int y = Math.round(rowY - counter.getMeasuredHeight() / 2f);
            float farY = Math.max(Math.abs(y - centerY),
                    Math.abs(y + counter.getMeasuredHeight() - centerY));
            int end = Math.min(getWidth() - edge,
                    (int) Math.floor(centerX + chord(radius, farY)));
            if (!closeBounds.isEmpty() && y < closeBounds.bottom + gap
                    && y + counter.getMeasuredHeight() > closeBounds.top - gap) {
                // Keep large-font page counts beside, rather than beneath, the close target.
                counter.measure(MeasureSpec.makeMeasureSpec(
                        Math.max(1, end - closeBounds.right - gap), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }
            place(counter, end - counter.getMeasuredWidth(), y);
        }
    }

    private void sizeIcon(View view) {
        if (view == null) return;
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        params.width = dp(44);
        params.height = dp(44);
    }

    private int maximumOriginalHeight(int width, int height) {
        float centerX = width / 2f;
        float centerY = height / 2f;
        int diameter = Math.min(width, height);
        float radius = Math.max(1f, diameter / 2f - dp(2));
        int edge = Math.max(dp(10), Math.round(diameter * 0.06f));
        int closeX = (width - close.getMeasuredWidth()) / 2;
        float closeHalfWidth = Math.max(centerX - closeX,
                closeX + close.getMeasuredWidth() - centerX);
        int closeTop = Math.max(edge, (int) Math.ceil(centerY - chord(radius, closeHalfWidth)));
        int downloadTop = closeTop + close.getMeasuredHeight() + dp(4);
        int sideX = closeX - download.getMeasuredWidth();
        if (sideX >= edge) {
            // An earlier row is safe only while the whole download target fits beside close.
            downloadTop = Math.min(downloadTop,
                    (int) Math.ceil(centerY - chord(radius, centerX - sideX)));
        }
        int rowHeight = Math.max(download.getMeasuredHeight(),
                visible(counter) ? counter.getMeasuredHeight() : 0);
        int originalTop = (int) Math.ceil(downloadTop + dp(4)
                + (rowHeight + download.getMeasuredHeight()) / 2f);
        int originalX = (width - original.getMeasuredWidth()) / 2;
        float halfWidth = Math.max(centerX - originalX,
                originalX + original.getMeasuredWidth() - centerX);
        return (int) Math.floor(centerY + chord(radius, halfWidth)) - originalTop;
    }

    private static boolean visible(View view) {
        return view != null && view.getVisibility() != GONE;
    }

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
