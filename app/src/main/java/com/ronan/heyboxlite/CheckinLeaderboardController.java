package com.ronan.heyboxlite;

import android.app.Activity;
import android.view.View;

/** Owns leaderboard page lifetime and leaves shell navigation to the activity. */
final class CheckinLeaderboardController implements CheckinLeaderboardPage.Host {
    interface Host {
        boolean leaderboardUsesRoundLayout();

        boolean leaderboardIsActive();

        int pageHorizontalPadding();

        int subpageTopPadding();

        int roundHeaderInset();

        void prepareLeaderboardChrome(Runnable backAction);

        void retainLeaderboardPage(View page);

        void transitionToLeaderboard(View page);

        void returnFromLeaderboard();
    }

    private final Activity activity;
    private final SessionStore session;
    private final CheckinCenterCoordinator coordinator;
    private final ThemeTokens tokens;
    private final Host host;
    private CheckinLeaderboardPage page;

    CheckinLeaderboardController(Activity activity, SessionStore session,
                                 CheckinCenterCoordinator coordinator, ThemeTokens tokens,
                                 Host host) {
        this.activity = activity;
        this.session = session;
        this.coordinator = coordinator;
        this.tokens = tokens;
        this.host = host;
    }

    void show() {
        host.prepareLeaderboardChrome(this::returnToHost);
        boolean roundLayout = host.leaderboardUsesRoundLayout();
        if (page != null && page.usesRoundLayout() != roundLayout) {
            page.close();
            page = null;
        }
        if (page == null) {
            page = new CheckinLeaderboardPage(activity, session, coordinator, tokens,
                    roundLayout, this);
        }
        page.onResume();
        View pageView = page.view();
        host.retainLeaderboardPage(pageView);
        host.transitionToLeaderboard(pageView);
    }

    void onResume() {
        if (page != null) page.onResume();
    }

    void onPause() {
        if (page != null) page.onPause();
    }

    void close() {
        if (page == null) return;
        page.close();
        page = null;
    }

    private void returnToHost() {
        host.returnFromLeaderboard();
    }

    @Override
    public void closePage() {
        returnToHost();
    }

    @Override
    public boolean isActive() {
        return host.leaderboardIsActive();
    }

    @Override
    public int pageHorizontalPadding() {
        return host.pageHorizontalPadding();
    }

    @Override
    public int subpageTopPadding() {
        return host.subpageTopPadding();
    }

    @Override
    public int roundHeaderInset() {
        return host.roundHeaderInset();
    }
}
