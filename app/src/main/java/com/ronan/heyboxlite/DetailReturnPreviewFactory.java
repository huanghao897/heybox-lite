package com.ronan.heyboxlite;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

/** Builds the native detail return layer without adding more UI state to MainActivity. */
final class DetailReturnPreviewFactory {
    private DetailReturnPreviewFactory() {
    }

    static View create(Activity activity, ThemeTokens tokens, String targetKey,
                       TransitionSnapshotStore pageSnapshots,
                       TransitionSnapshotStore fullScreenSnapshots) {
        ImageView snapshot = new ImageView(activity);
        snapshot.setBackgroundColor(tokens == null ? 0xff202124 : tokens.panel);
        snapshot.setScaleType(ImageView.ScaleType.FIT_XY);
        if (!pageSnapshots.bindPreview(snapshot, targetKey)) {
            fullScreenSnapshots.bindPreview(snapshot, targetKey);
        }
        return snapshot;
    }
}
