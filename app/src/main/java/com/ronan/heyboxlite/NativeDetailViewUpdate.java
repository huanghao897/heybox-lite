package com.ronan.heyboxlite;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/** Replaces document contents without replacing the retained navigation/scroll owners. */
final class NativeDetailViewUpdate {
    private NativeDetailViewUpdate() { }

    static DetailPageAssembler.Result reuse(DetailPageAssembler.Result next,
                                            DetailPager pager, ScrollView article,
                                            ScrollView comments) {
        replace(article, next.articleScroll);
        replace(comments, next.commentScroll);
        return new DetailPageAssembler.Result(next.body, next.hsrc, next.authCode,
                next.diagnostics, pager, article, comments, (FrameLayout) pager.getParent(),
                next.articleCommentHost, next.commentPageHost, next.comments, next.pagePadding,
                next.roundHeaderTop, next.contentBlocks, next.videos);
    }

    private static void replace(ScrollView target, ScrollView source) {
        View content = source.getChildAt(0);
        source.removeView(content);
        target.removeAllViews();
        target.addView(content);
    }
}
