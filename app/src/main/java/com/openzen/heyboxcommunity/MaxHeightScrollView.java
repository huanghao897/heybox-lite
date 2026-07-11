package com.openzen.heyboxcommunity;

import android.content.Context;
import android.view.View;
import android.widget.ScrollView;

final class MaxHeightScrollView extends ScrollView {
    private int maxHeight;

    MaxHeightScrollView(Context context) {
        super(context);
    }

    void setMaxHeight(int value) {
        maxHeight = Math.max(0, value);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight > 0) {
            heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                    maxHeight, View.MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
