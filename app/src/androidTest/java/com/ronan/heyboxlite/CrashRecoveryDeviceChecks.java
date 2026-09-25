package com.ronan.heyboxlite;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
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
                checkRecoveryBounds(context);
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

    private static void checkRecoveryBounds(Context context) {
        SessionStore session = new SessionStore(context);
        boolean wasRound = session.roundScreen();
        try {
            for (boolean round : new boolean[]{true, false}) {
                session.setRoundScreen(round);
                CrashRecoveryView root = new CrashRecoveryView(context, session,
                        "error: java.lang.IllegalStateException\nat some.Frame()",
                        () -> {}, () -> {}, () -> {});
                for (int[] size : new int[][]{{240, 240}, {320, 320}, {400, 400}, {400, 300}}) {
                    layout(root, size[0], size[1]);
                    for (String tag : new String[]{"crash-restart", "crash-exit", "crash-save"}) {
                        View button = root.findViewWithTag(tag);
                        Rect rect = new Rect(0, 0, button.getWidth(), button.getHeight());
                        root.offsetDescendantRectToMyCoords(button, rect);
                        require(rect.width() > 0 && rect.height() > 0);
                        require(rect.top >= 0 && rect.bottom <= size[1]);
                        require(rect.left >= 0 && rect.right <= size[0]);
                        if (session.usesRoundLayout()) {
                            double radius = Math.min(size[0], size[1]) / 2.0;
                            for (int x : new int[]{rect.left, rect.right}) {
                                for (int y : new int[]{rect.top, rect.bottom}) {
                                    double dx = x - size[0] / 2.0;
                                    double dy = y - size[1] / 2.0;
                                    require(dx * dx + dy * dy <= radius * radius);
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            session.setRoundScreen(wasRound);
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
