package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main-thread viewport owner. There is only one outstanding tile, including delivery. */
final class LongImageTileView extends View {
    private static final ImageTaskExecutor DECODER = new ImageTaskExecutor(1, 8);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final LongImageSource source;
    private final ImageTileBudget budget;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Map<Integer, Tile> tiles = new LinkedHashMap<>();
    private final RectF destination = new RectF();
    private final int targetWidth;
    private ImageLoadRequest pending;
    private Runnable ready;
    private Runnable failed;
    private int tileHeight;
    private int first;
    private int last = -1;
    private int visibleFirst;
    private int visibleLast;
    private int pendingIndex = -1;
    private int generation;
    private int sample;
    private int failures;
    private boolean closed;
    private boolean active = true;

    LongImageTileView(Context context, LongImageSource source, int targetWidth, Runnable ready,
                      Runnable failed) {
        this(context, source, targetWidth, ready, failed, ImageTileBudget.SHARED);
    }

    LongImageTileView(Context context, LongImageSource source, int targetWidth,
                      Runnable ready, Runnable failed, ImageTileBudget budget) {
        super(context);
        this.source = source;
        this.targetWidth = targetWidth;
        this.ready = ready;
        this.failed = failed;
        this.budget = budget;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        long height = Math.round((double) width * source.height / source.width);
        setMeasuredDimension(width, (int) Math.max(1, Math.min(0x00ffffff, height)));
    }

    void viewport(int top, int height) {
        if (closed || !active || getWidth() <= 0 || height <= 0) return;
        int rows = ImageDecodePolicy.sourceTileHeight(source.width, source.height, getWidth(), height);
        if (tileHeight != rows) {
            cancelPending();
            clearTiles();
            tileHeight = rows;
            sample = ImageDecodePolicy.sample(source.width, rows, targetWidth, budget.limit / 8);
        }
        double ratio = source.width / (double) getWidth();
        int sourceTop = (int) Math.min(source.height - 1L, Math.max(0L, (long) (top * ratio)));
        int sourceBottom = (int) Math.min(source.height, Math.max(sourceTop + 1L,
                (long) Math.ceil(((long) top + height) * ratio)));
        visibleFirst = sourceTop / tileHeight;
        visibleLast = (sourceBottom - 1) / tileHeight;
        first = Math.max(0, visibleFirst - 1);
        last = Math.min((source.height - 1) / tileHeight, visibleLast + 1);
        if (pendingIndex >= 0 && (pendingIndex < first || pendingIndex > last)) cancelPending();
        Iterator<Map.Entry<Integer, Tile>> iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Tile> entry = iterator.next();
            if (entry.getKey() < first || entry.getKey() > last) {
                entry.getValue().close();
                iterator.remove();
            }
        }
        invalidate();
        pump();
    }

    private void pump() {
        if (closed || !active || pending != null || last < first) return;
        int index = missing(visibleFirst, visibleLast);
        if (index < 0) index = missing(first, last);
        if (index < 0) return;
        Rect region = region(index);
        int decodeSample = sample;
        ImageTileBudget.Lease lease = null;
        while (decodeSample <= (1 << 29)) {
            lease = budget.reserve(ImageDecodePolicy.bytes(source.width, region.height(), decodeSample));
            if (lease != null) break;
            decodeSample *= 2;
        }
        if (lease == null) {
            MAIN.removeCallbacks(retry);
            MAIN.postDelayed(retry, 32L);
            return;
        }
        final int tileIndex = index;
        final int serial = generation;
        final int selectedSample = decodeSample;
        final ImageTileBudget.Lease reservation = lease;
        final LongImageSource input = source;
        WeakReference<LongImageTileView> owner = new WeakReference<>(this);
        pendingIndex = index;
        pending = ImageLoadRequest.work(DECODER, MAIN,
                () -> decode(input, region, selectedSample, reservation), tile -> {
                    LongImageTileView view = owner.get();
                    if (view == null) { if (tile != null) tile.close(); return; }
                    view.delivered(serial, tileIndex, tile);
                }, tile -> { if (tile != null) tile.close(); }, reservation::close);
    }

    private final Runnable retry = this::pump;

    private static Tile decode(LongImageSource source, Rect region, int sample,
                               ImageTileBudget.Lease lease) {
        try {
            for (int attempt = 0; attempt < 3 && !ImageWorkToken.stopped(); attempt++) {
                try {
                    Bitmap bitmap = source.decode(region, sample);
                    if (bitmap == null) break;
                    if (bitmap.getAllocationByteCount() > ImageDecodePolicy.bytes(
                            source.width, region.height(), sample)) {
                        bitmap.recycle();
                        break;
                    }
                    return new Tile(bitmap, lease);
                } catch (OutOfMemoryError error) {
                    sample = sample >= (1 << 29) ? 1 << 30 : sample * 2;
                }
            }
        } catch (RuntimeException error) {
            // Invalid/cancelled region sources must leave the preview usable.
        }
        lease.close();
        return null;
    }

    private void delivered(int serial, int index, Tile tile) {
        if (closed || serial != generation || index < first || index > last || !active) {
            if (tile != null) tile.close();
            return;
        }
        pending = null;
        pendingIndex = -1;
        if (tile == null) {
            if (++failures >= 3) {
                Runnable callback = failed;
                close();
                if (callback != null) callback.run();
                return;
            }
            sample = Math.min(1 << 29, sample * 2);
            MAIN.removeCallbacks(retry);
            MAIN.postDelayed(retry, 80L);
            return;
        }
        tiles.put(index, tile);
        failures = 0;
        invalidate();
        if (missing(visibleFirst, visibleLast) < 0 && ready != null) {
            Runnable callback = ready;
            ready = null;
            callback.run();
        }
        pump();
    }

    private int missing(int start, int end) {
        for (int index = start; index <= end; index++) if (!tiles.containsKey(index)) return index;
        return -1;
    }

    private Rect region(int index) {
        int top = (int) ((long) index * tileHeight);
        return new Rect(0, top, source.width, (int) Math.min(source.height, (long) top + tileHeight));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = getWidth() / (float) source.width;
        for (Map.Entry<Integer, Tile> entry : tiles.entrySet()) {
            Rect rect = region(entry.getKey());
            destination.set(0, rect.top * scale, getWidth(), rect.bottom * scale);
            canvas.drawBitmap(entry.getValue().bitmap, null, destination, paint);
        }
    }

    void setActive(boolean active) {
        this.active = active;
        if (!active) { cancelPending(); clearTiles(); }
    }

    void freeze() {
        active = false;
        cancelPending();
        source.close();
    }

    void close() {
        if (closed) return;
        closed = true;
        ready = null;
        failed = null;
        cancelPending();
        clearTiles();
        source.close();
    }

    private void cancelPending() {
        generation++;
        MAIN.removeCallbacks(retry);
        if (pending != null) pending.cancel();
        pending = null;
        pendingIndex = -1;
    }

    private void clearTiles() {
        for (Tile tile : new ArrayList<>(tiles.values())) tile.close();
        tiles.clear();
        invalidate();
    }

    int residentCount() { return tiles.size(); }
    boolean pendingTile() { return pending != null; }

    private static final class Tile {
        final Bitmap bitmap;
        final ImageTileBudget.Lease lease;
        Tile(Bitmap bitmap, ImageTileBudget.Lease lease) { this.bitmap = bitmap; this.lease = lease; }
        void close() { if (!bitmap.isRecycled()) bitmap.recycle(); lease.close(); }
    }
}
