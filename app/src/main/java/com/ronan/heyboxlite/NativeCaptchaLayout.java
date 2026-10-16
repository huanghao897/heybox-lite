package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Programmatic-only layout: the host supplies its shape; the verification page is unchanged. */
@SuppressLint("ViewConstructor")
final class NativeCaptchaLayout extends LinearLayout {
    private final boolean round;
    private FrameLayout toolbar;
    private TextView status;
    private int minimumToolbarHeight;

    NativeCaptchaLayout(Context context, boolean round) {
        super(context);
        this.round = round;
        setOrientation(VERTICAL);
    }

    void bindToolbar(FrameLayout toolbar, TextView status) {
        this.toolbar = toolbar;
        this.status = status;
        minimumToolbarHeight = Math.max(toolbar.getMinimumHeight(), toolbar.getLayoutParams().height);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (round && toolbar != null && status != null) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                height = getResources().getDisplayMetrics().heightPixels;
            }
            int diameter = Math.min(width, height);
            LayoutParams params = (LayoutParams) toolbar.getLayoutParams();
            params.width = Math.max(1, (int) (diameter * 0.8f));
            params.gravity = Gravity.CENTER_HORIZONTAL;
            FrameLayout.LayoutParams statusParams = (FrameLayout.LayoutParams) status.getLayoutParams();
            int statusWidth = Math.max(1, params.width - toolbar.getPaddingLeft()
                    - toolbar.getPaddingRight() - statusParams.leftMargin - statusParams.rightMargin);
            // Unconstrained height retains fallback-font line metrics and custom line spacing.
            status.measure(MeasureSpec.makeMeasureSpec(statusWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int textHeight = status.getMeasuredHeight();
            if (status.getLayout() != null) {
                textHeight = Math.max(textHeight, status.getLayout().getHeight()
                        + status.getCompoundPaddingTop() + status.getCompoundPaddingBottom());
            }
            params.height = Math.max(minimumToolbarHeight, textHeight + toolbar.getPaddingTop()
                    + toolbar.getPaddingBottom() + statusParams.topMargin + statusParams.bottomMargin);
            float radius = Math.max(0, diameter / 2f
                    - 2 * getResources().getDisplayMetrics().density);
            float halfWidth = params.width / 2f;
            float halfChord = (float) Math.sqrt(Math.max(0,
                    radius * radius - halfWidth * halfWidth));
            params.topMargin = Math.max(0, (int) Math.ceil(height / 2f - halfChord));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
