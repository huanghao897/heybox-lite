package com.ronan.heyboxlite;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.compose.ui.platform.ComposeView;

/** Owns the already painted detail preview until the original Compose surface can draw. */
final class NativeDetailReturnHandoff extends FrameLayout {
    interface Destination {
        boolean isCurrent();
    }

    private static final ThreadLocal<DetailPager> returnOwner = new ThreadLocal<>();

    // Progressive rendering can replace MainActivity.detailPager before the old visible pager
    // finishes settling. The existing no-argument native callback must still name its sender.
    static void dispatchReturn(DetailPager pager, Runnable callback) {
        DetailPager previous = returnOwner.get();
        returnOwner.set(pager);
        try {
            callback.run();
        } finally {
            if (previous == null) returnOwner.remove();
            else returnOwner.set(previous);
        }
    }

    static DetailPager returningPager(DetailPager current) {
        DetailPager owner = returnOwner.get();
        return owner == null ? current : owner;
    }

    static View livePreview(DetailPager pager) {
        View preview = pager == null ? null : pager.returnPreviewForHandoff();
        return preview instanceof ComposeView ? preview : null;
    }

    private final FrameLayout destinationLayer;
    private final View destinationView;
    private final Runnable released;
    private Destination destination;
    private ViewTreeObserver observer;
    private boolean disposed;
    private final ViewTreeObserver.OnPreDrawListener preDraw = this::onDestinationPreDraw;
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) {
            ViewGroup host = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
            dispose(false);
            // A parent may be walking its child array during window teardown. Clear ownership
            // now, but remove the sibling only after that synchronous detach dispatch finishes.
            if (host != null) host.post(() -> {
                if (getParent() == host) host.removeView(NativeDetailReturnHandoff.this);
            });
        }
    };

    private NativeDetailReturnHandoff(Context context, FrameLayout layer,
                                      View view, Runnable released) {
        super(context);
        this.destinationLayer = layer;
        this.destinationView = view;
        this.released = released;
        setClickable(true);
    }

    static NativeDetailReturnHandoff adopt(DetailPager pager, View preview,
                                           FrameLayout destinationLayer, Runnable released) {
        if (pager == null || !pager.canHandoffReturnPreview(preview)
                || destinationLayer == null || destinationLayer.getChildCount() == 0
                || !(destinationLayer.getParent() instanceof FrameLayout)
                || !preview.isAttachedToWindow() || !destinationLayer.isAttachedToWindow()
                || preview.getWindowToken() != destinationLayer.getWindowToken()) return null;
        FrameLayout host = (FrameLayout) destinationLayer.getParent();
        View destinationView = destinationLayer.getChildAt(0);
        NativeDetailReturnHandoff handoff = new NativeDetailReturnHandoff(
                host.getContext(), destinationLayer, destinationView, released);
        host.addView(handoff, new FrameLayout.LayoutParams(
                (FrameLayout.LayoutParams) destinationLayer.getLayoutParams()));
        // Both parents are attached to the same window. Transfer without remove/add dispatching
        // a window detach, which would destroy ComposeView's existing composition.
        handoff.attachViewToParent(pager.detachReturnPreviewForHandoff(), -1,
                new FrameLayout.LayoutParams(-1, -1));
        handoff.measure(MeasureSpec.makeMeasureSpec(destinationLayer.getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(destinationLayer.getHeight(), MeasureSpec.EXACTLY));
        handoff.layout(destinationLayer.getLeft(), destinationLayer.getTop(),
                destinationLayer.getRight(), destinationLayer.getBottom());
        handoff.requestLayout();
        destinationView.addOnAttachStateChangeListener(handoff.attachment);
        return handoff;
    }

    void awaitDestination(Destination destination) {
        if (this.disposed) return;
        this.destination = destination;
        if (!destination.isCurrent()) {
            dispose();
            return;
        }
        this.observer = this.destinationView.getViewTreeObserver();
        this.observer.addOnPreDrawListener(this.preDraw);
        this.destinationView.invalidate();
    }

    private boolean onDestinationPreDraw() {
        if (this.disposed) return true;
        if (!this.destination.isCurrent()) {
            dispose();
            return true;
        }
        // Tree observers are shared even by invisible siblings. Only the actual destination's
        // visible, laid-out frame may retire the preview, not the preview's own first pre-draw.
        if (this.destinationView.getParent() == this.destinationLayer
                && this.destinationView.isShown() && this.destinationView.getWidth() > 0
                && this.destinationView.getHeight() > 0
                && this.destinationView.getAlpha() == 1f
                && this.destinationLayer.getAlpha() == 1f
                && !this.destinationView.isLayoutRequested()
                && !this.destinationLayer.isLayoutRequested()) dispose();
        return true;
    }

    void dispose() {
        dispose(true);
    }

    private void dispose(boolean removeFromHost) {
        if (this.disposed) return;
        this.disposed = true;
        this.destinationView.removeOnAttachStateChangeListener(this.attachment);
        if (this.observer != null) {
            ViewTreeObserver current = this.observer.isAlive()
                    ? this.observer : this.destinationView.getViewTreeObserver();
            if (current.isAlive()) current.removeOnPreDrawListener(this.preDraw);
            this.observer = null;
        }
        this.destination = null;
        if (removeFromHost && getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
        this.released.run();
    }

    @Override protected void onDetachedFromWindow() {
        dispose(false);
        super.onDetachedFromWindow();
    }
}
