package com.ronan.heyboxlite;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** 帖内图片外框：按图片宽高比在最小/最大高度间自适应，未知比例时用兜底高度。 */
final class PostImageFrame extends FrameLayout {
    private final int minHeight;
    private final int maxHeight;
    private final int fallbackHeight;
    private float aspect;

    PostImageFrame(Context context, Dp dp, int fallbackHeightDp) {
        super(context);
        this.aspect = 0.5625f;
        this.minHeight = dp.dp(Math.max(92, fallbackHeightDp - 36));
        this.maxHeight = dp.dp(fallbackHeightDp >= 140 ? 360 : 220);
        this.fallbackHeight = dp.dp(fallbackHeightDp);
        setMinimumHeight(this.minHeight);
    }

    void setImageSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float next = (float) height / (float) width;
        if (Math.abs(next - this.aspect) < 0.08f) {
            return;
        }
        this.aspect = Math.max(0.3f, Math.min(2.8f, next));
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int desired = width > 0 ? Math.round(width * this.aspect) : this.fallbackHeight;
        int height = Math.max(this.minHeight, Math.min(this.maxHeight, desired));
        super.onMeasure(widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    }
}
