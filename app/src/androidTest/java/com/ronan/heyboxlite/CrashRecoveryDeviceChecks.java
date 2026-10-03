package com.ronan.heyboxlite;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/** Local-only checks: no intentional fatal error or production upload. */
final class CrashRecoveryDeviceChecks {
    static void run(Instrumentation instrumentation) {
        Throwable[] failure = new Throwable[1];
        instrumentation.runOnMainSync(() -> {
            try {
                Context context = instrumentation.getTargetContext();
                checkFixedRows(context);
            } catch (Throwable error) {
                failure[0] = error;
            }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private static void checkFixedRows(Context context) {
        PullRefreshListView list = new PullRefreshListView(context, 0xff000000,
                0xffcccccc, 0xffffffff, 1f, 1f);
        int[] count = {10};
        BaseAdapter content = new BaseAdapter() {
            @Override public int getCount() { return count[0]; }
            @Override public Object getItem(int index) { return index; }
            @Override public long getItemId(int index) { return index; }
            @Override public View getView(int index, View recycled, ViewGroup parent) {
                TextView view = new TextView(context);
                view.setText("item " + index);
                view.setHeight(48);
                return view;
            }
        };
        list.setContentAdapter(content, new TextView(context), new TextView(context));
        require(list.getHeaderViewsCount() == 0);
        require(list.getAdapter() instanceof FixedRowsAdapter);
        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.RGB_565);
        try {
            for (int size : new int[]{10, 0, 1, 40, 0}) {
                count[0] = size;
                content.notifyDataSetChanged();
                require(!list.getAdapter().isEnabled(-1));
                require(!list.getAdapter().isEnabled(list.getAdapter().getCount()));
                layout(list, 320, 320);
                list.draw(new Canvas(bitmap));
                require(list.getAdapter().getCount() == size + 3);
            }
        } finally {
            bitmap.recycle();
        }
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Crash recovery device check failed");
    }
}
