package com.ronan.heyboxlite;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** Viewer page owns one opening request, scroll container and viewport tile owner. */
final class ImageViewerLongPage {
    interface Listener {
        void opened(DismissibleImageScrollView scroll);
        void ready(DismissibleImageScrollView scroll);
        void failed();
    }

    private final Context context;
    private final int inset;
    private final int targetWidth;
    private final ImagePullListener pull;
    private Listener listener;
    private ImageLoadRequest opening;
    private DismissibleImageScrollView scroll;
    private LongImageTileView tiles;
    private boolean closed;
    private boolean active = true;
    private String url;

    ImageViewerLongPage(Context context, int inset, int targetWidth,
                         ImagePullListener pull, Listener listener) {
        this.context = context;
        this.inset = inset;
        this.targetWidth = targetWidth;
        this.pull = pull;
        this.listener = listener;
    }

    void open(String url) {
        this.url = url;
        if (closed || !active) return;
        WeakReference<ImageViewerLongPage> owner = new WeakReference<>(this);
        opening = ImageLoader.openLongImage(url, source -> {
            ImageViewerLongPage page = owner.get();
            if (page == null || page.closed) { if (source != null) source.close(); return; }
            page.opened(source);
        });
    }

    private void opened(LongImageSource source) {
        opening = null;
        if (source == null) { listener.failed(); return; }
        scroll = new DismissibleImageScrollView(context);
        scroll.setFillViewport(false);
        scroll.setPullListener(pull);
        scroll.setPadding(inset, inset, inset, inset);
        scroll.setClipToPadding(false);
        tiles = new LongImageTileView(context, source, targetWidth, () -> {
            if (!closed) listener.ready(scroll);
        }, () -> {
            if (!closed) listener.failed();
        });
        scroll.addView(tiles, new DismissibleImageScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setVisibility(View.INVISIBLE);
        scroll.getViewTreeObserver().addOnScrollChangedListener(this::viewport);
        scroll.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> viewport());
        listener.opened(scroll);
        scroll.post(this::viewport);
    }

    private void viewport() {
        if (closed || scroll == null || tiles == null) return;
        tiles.viewport(Math.max(0, scroll.getScrollY()),
                scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom());
    }

    void setActive(boolean active) {
        this.active = active;
        if (!active && opening != null) { opening.cancel(); opening = null; }
        if (active && !closed && tiles == null && opening == null && url != null) open(url);
        if (tiles != null) { tiles.setActive(active); if (active) viewport(); }
    }

    void freeze() {
        active = false;
        if (opening != null) opening.cancel();
        opening = null;
        if (tiles != null) tiles.freeze();
    }

    void close() {
        if (closed) return;
        closed = true;
        listener = null;
        if (opening != null) opening.cancel();
        opening = null;
        if (tiles != null) tiles.close();
        if (scroll != null) {
            scroll.animate().cancel();
            if (scroll.getParent() instanceof ViewGroup) {
                ((ViewGroup) scroll.getParent()).removeView(scroll);
            }
        }
        tiles = null;
        scroll = null;
    }
}
