package com.ronan.heyboxlite;

/** Releases native UI state and pending work owned by the previous login session. */
final class NativeSessionContentReset {
    private NativeSessionContentReset() { }

    static void clear(MainActivity activity) {
        if (activity.writeTokenProvider != null) activity.writeTokenProvider.close();
        if (activity.feedPage != null) activity.feedPage.invalidateSession();
        if (activity.searchPage != null) activity.searchPage.invalidateSession();
        if (activity.postActions != null) activity.postActions.invalidateSession();
        if (activity.commentController != null) activity.commentController.reset();
        if (activity.savedContentController != null) activity.savedContentController.cancelRequests();
        if (activity.detailLoader != null) activity.detailLoader.cancel();
        if (activity.readingTimeTracker != null) activity.readingTimeTracker.pause();
        activity.discardDetailHistory();
        if (activity.detailPager != null) activity.detailPager.cancelMotion();
        activity.detailPager = null;
        activity.detailScroll = null;
        activity.detailCommentScroll = null;
        activity.currentDetailItem = null;
        activity.currentDetailBody = null;
        activity.currentLinkId = "";
        activity.currentLinkHsrc = "";
        activity.currentAuthCode = "";
        activity.retainedPages.clear();
        activity.screenSnapshots.clear();
        activity.fullScreenSnapshots.clear();
    }
}
