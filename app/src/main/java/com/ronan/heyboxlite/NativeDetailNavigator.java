package com.ronan.heyboxlite;

import android.view.View;
import android.view.ViewGroup;

/** Owns native detail return targets and suspended parents independently of Compose. */
final class NativeDetailNavigator {
    private final MainActivity activity;
    private final ContentNavigationHistory<DetailNavigationState> history =
            new ContentNavigationHistory<>();
    private String returnScreen = "feed";
    private String returnTitle = "";
    private View returnView;
    private String userSpaceReturnScreen = "feed";

    NativeDetailNavigator(MainActivity activity) {
        this.activity = activity;
    }

    void open(FeedItem item) {
        activity.pendingBackTransition = false;
        activity.pageTransitions.finishNow();
        activity.saveCurrentDetailProgress();
        activity.stopQrPolling();
        activity.ensureEmojiCatalog(() -> { });
        if ("feed".equals(activity.screen)) activity.feedPage.saveScroll();
        if ("search".equals(activity.screen)) activity.searchPage.saveListPosition();
        View source = currentRoot();
        activity.captureShellSnapshot(activity.screen, source);
        activity.captureFullScreenSnapshot(activity.screen);
        if (!"detail".equals(activity.screen)) {
            returnScreen = activity.screen;
            returnTitle = activity.title == null ? "" : activity.title.getText().toString();
            returnView = retainsReturnView(returnScreen) ? source : null;
        }
        activity.screen = "detail";
        activity.nativeDetail.load(item);
    }

    String backTarget() {
        if ("detail".equals(activity.screen)) return returnScreen;
        if ("user_space".equals(activity.screen)) {
            return history.isEmpty() ? userSpaceReturnScreen : "detail";
        }
        if ("saved".equals(activity.screen)) {
            return activity.savedContentController.returnScreen();
        }
        return ScreenRoutes.staticParentOrDefault(activity.screen, "feed");
    }

    View returnView() { return returnView; }
    boolean hasHistory() { return !history.isEmpty(); }
    void topLevelActivated(String key) {
        if ("profile".equals(key)) userSpaceReturnScreen = "feed";
    }
    View previousRoot() {
        DetailNavigationState previous = history.peek();
        return previous == null ? null : previous.root;
    }

    boolean prepareUserSpace() {
        if (!"detail".equals(activity.screen)) {
            userSpaceReturnScreen = activity.screen;
            return true;
        }
        activity.pageTransitions.finishNow();
        activity.saveCurrentDetailProgress();
        DetailNavigationState previous = activity.nativeDetail.suspend(
                currentRoot(), returnScreen, returnView, returnTitle);
        if (previous == null) return false;
        history.push(previous);
        returnView = null;
        pauseReading();
        return true;
    }

    void returnFromUserSpace() {
        if (restorePrevious(false)) return;
        String target = userSpaceReturnScreen;
        userSpaceReturnScreen = "feed";
        if ("profile".equals(target)) activity.showProfile();
        else if ("reading_center".equals(target)) activity.showReadingCenter();
        else activity.showFeed();
    }

    void returnFromDetail(boolean gestureOwned) {
        pauseReading();
        activity.saveCurrentDetailProgress();
        returnView = activity.nativeDetail.releaseForReturn(returnView);
        if (restoreReturnView(!gestureOwned)) return;
        activity.pendingBackTransition = !gestureOwned;
        if (gestureOwned) {
            activity.pageTransitions.finishNow();
            activity.content.removeAllViews();
        }
        if ("saved".equals(returnScreen)) {
            String savedReturn = activity.savedContentController.returnScreen();
            if ("reading_center".equals(savedReturn)) activity.showReadingCenter();
            else if ("favorites".equals(savedReturn)) activity.showFavorites();
            else activity.showProfile();
        } else if ("reading_center".equals(returnScreen)) {
            activity.showReadingCenter();
        } else if ("search".equals(returnScreen)) {
            activity.showSearch(true);
        } else {
            activity.showFeed();
        }
    }

    boolean restorePrevious(boolean alreadyAttached) {
        DetailNavigationState previous = history.pop();
        if (previous == null) return false;
        activity.nativeDetail.restore(previous);
        returnScreen = previous.returnScreen;
        returnView = previous.returnView;
        returnTitle = previous.returnTitle;
        activity.screen = "detail";
        activity.shellBar.setVisibility(View.GONE);
        activity.setBottomNavVisible(false, false);
        activity.leading.setVisibility(View.VISIBLE);
        activity.leading.setOnClickListener(view -> activity.returnFromDetail());
        activity.action.setVisibility(View.INVISIBLE);
        activity.nativeDetail.updateTitle();
        Motions.resetTree(previous.root);
        if (!alreadyAttached) {
            detach(previous.root);
            activity.pendingBackTransition = true;
            activity.transitionTo(previous.root);
        }
        activity.nativeDetail.resumeReading();
        return true;
    }

    void discardHistory() {
        for (DetailNavigationState previous : history.drain()) {
            previous.pager.cancelMotion();
            previous.pager.takeReturnView();
        }
    }

    void reset() {
        discardHistory();
        returnScreen = "feed";
        returnTitle = "";
        returnView = null;
        userSpaceReturnScreen = "feed";
    }

    private boolean restoreReturnView(boolean animate) {
        activity.pendingBackTransition = false;
        activity.pageTransitions.finishNow();
        if (returnView == null || !retainsReturnView(returnScreen)) {
            returnView = null;
            return false;
        }
        View page = returnView;
        returnView = null;
        detach(page);
        activity.screen = returnScreen;
        configureReturnChrome();
        activity.ensurePageBackdrop(page);
        if (animate) activity.pageTransitions.run(activity.content, page, false);
        else {
            activity.content.removeAllViews();
            activity.content.addView(page, activity.match());
        }
        // Reattaching a list after an off-screen update can reset its position.
        if ("search".equals(activity.screen)) activity.searchPage.restoreListPosition();
        if ("feed".equals(activity.screen)) activity.feedPage.restoreScroll();
        return true;
    }

    private void configureReturnChrome() {
        if ("feed".equals(activity.screen)) {
            activity.activate("feed");
            activity.title.setText(R.string.title_feed);
            activity.action.setVisibility(View.INVISIBLE);
            return;
        }
        activity.setBottomNavVisible(false);
        activity.title.setText(returnTitle);
        activity.leading.setVisibility(View.VISIBLE);
        activity.leading.setOnClickListener(view -> activity.onBackPressed());
        activity.action.setVisibility(View.INVISIBLE);
        if ("user_space".equals(activity.screen)) {
            activity.title.setText(R.string.title_user_space);
        }
    }

    private View currentRoot() {
        return activity.content == null || activity.content.getChildCount() == 0 ? null
                : activity.content.getChildAt(activity.content.getChildCount() - 1);
    }

    private void pauseReading() {
        if (activity.readingTimeTracker != null) {
            activity.readingTimeTracker.pause();
            activity.updateReadingTimeEntry();
        }
    }

    private static void detach(View page) {
        if (page.getParent() instanceof ViewGroup) {
            ((ViewGroup) page.getParent()).removeView(page);
        }
    }

    private static boolean retainsReturnView(String screen) {
        return "feed".equals(screen) || "search".equals(screen) || "saved".equals(screen)
                || "reading_center".equals(screen) || "user_space".equals(screen);
    }
}
