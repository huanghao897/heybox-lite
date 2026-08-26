package com.ronan.heyboxlite;

import android.app.Activity;
import android.util.DisplayMetrics;

/** Responsive measurements for the public leaderboard on phones and watches. */
final class CheckinLeaderboardLayout {
    private static final float COMPACT_WIDTH_DP = 320.0f;
    private static final float NARROW_WIDTH_DP = 250.0f;
    private static final int COMPACT_MIN_CONTENT_DP = 176;
    private static final int PHONE_MIN_CONTENT_DP = 240;

    private final boolean roundScreen;
    private final boolean watch;
    private final boolean compact;
    private final boolean narrow;
    private final float density;
    private final float uiScale;
    private final int widthPixels;

    private CheckinLeaderboardLayout(boolean roundScreen, boolean watch,
                                     float density, float uiScale, int widthPixels,
                                     float shortEdgeDp) {
        this.roundScreen = roundScreen;
        this.watch = watch;
        this.compact = roundScreen || watch || shortEdgeDp <= COMPACT_WIDTH_DP;
        this.narrow = shortEdgeDp <= NARROW_WIDTH_DP;
        this.density = Math.max(0.1f, density);
        this.uiScale = Math.max(0.1f, uiScale);
        this.widthPixels = Math.max(1, widthPixels);
    }

    static CheckinLeaderboardLayout create(Activity activity, SessionStore session,
                                           boolean roundScreen) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        float density = Math.max(0.1f, metrics.density);
        return forMetrics(metrics.widthPixels, metrics.heightPixels, density,
                session.uiScale() / 100.0f, roundScreen,
                RoundLayoutMetrics.isWatchDisplay(activity));
    }

    static CheckinLeaderboardLayout forMetrics(int widthPixels, int heightPixels,
                                               float density, float uiScale,
                                               boolean roundScreen, boolean watch) {
        float safeDensity = Math.max(0.1f, density);
        int shortEdge = Math.max(1, Math.min(widthPixels, heightPixels));
        float shortEdgeDp = shortEdge / safeDensity;
        return new CheckinLeaderboardLayout(roundScreen, watch, safeDensity, uiScale,
                Math.max(1, widthPixels), shortEdgeDp);
    }

    boolean isCompact() {
        return compact;
    }

    boolean isRoundScreen() {
        return roundScreen;
    }

    boolean isWatch() {
        return watch;
    }

    int pageHorizontalPadding(int requested) {
        int minimumContent = dp(compact ? (narrow ? 156 : COMPACT_MIN_CONTENT_DP)
                : PHONE_MIN_CONTENT_DP);
        int maximum = Math.max(0, (widthPixels - minimumContent) / 2);
        return Math.min(Math.max(0, requested), maximum);
    }

    String overviewSubtitle() {
        return narrow ? "无需登录即可查看" : "无需登录或连接签到服务即可查看";
    }

    int pageBottomPadding() {
        return dp(compact ? 16 : 20);
    }

    int sectionGap() {
        return dp(compact ? 8 : 10);
    }

    int compactGap() {
        return dp(compact ? 7 : 9);
    }

    int cardHorizontalPadding() {
        return dp(compact ? 11 : 14);
    }

    int cardVerticalPadding() {
        return dp(compact ? 10 : 13);
    }

    int selectorPadding() {
        return dp(compact ? 3 : 4);
    }

    int selectorHeight() {
        return Math.max(36, dp(narrow ? 34 : compact ? 36 : 40));
    }

    int selectorItemHeight() {
        return Math.max(32, dp(narrow ? 32 : compact ? 34 : 38));
    }

    float cardTitleSp() {
        return compact ? 14.5f : 15.5f;
    }

    float bodySp() {
        return compact ? 11.5f : 12.0f;
    }

    int rowVerticalPadding() {
        return dp(compact ? 5 : 7);
    }

    int rowMinimumHeight() {
        return Math.max(40, dp(compact ? 42 : 46));
    }

    int rankWidth() {
        return rankWidth(1);
    }

    int rankWidth(int rank) {
        int digits = Math.max(1, String.valueOf(Math.max(1, rank)).length());
        int base = compact ? 21 : 25;
        return Math.max(dp(base + Math.max(0, digits - 2) * 4), compact ? 20 : 24);
    }

    int avatarSize() {
        return Math.max(24, dp(compact ? 27 : 32));
    }

    int avatarRadius() {
        return Math.max(12, dp(compact ? 14 : 16));
    }

    int avatarLeftMargin() {
        return dp(compact ? 3 : 5);
    }

    int avatarRightMargin() {
        return dp(compact ? 6 : 8);
    }

    int valueLeftMargin() {
        return dp(compact ? 4 : 7);
    }

    int valueColumnWidth(int pagePadding, int rank) {
        int contentWidth = Math.max(1, widthPixels - pagePadding * 2);
        int fixedWidth = rankWidth(rank) + avatarLeftMargin() + avatarSize()
                + avatarRightMargin() + valueLeftMargin();
        int nameMinimum = dp(narrow ? 42 : compact ? 54 : 76);
        int available = Math.max(dp(compact ? 48 : 58),
                contentWidth - fixedWidth - nameMinimum);
        int preferred = dp(compact ? (narrow ? 64 : 78) : 98);
        return Math.min(preferred, available);
    }

    int dividerInset() {
        return dividerInset(1);
    }

    int dividerInset(int rank) {
        return rankWidth(rank) + avatarLeftMargin() + avatarSize() + avatarRightMargin();
    }

    int emptyVerticalPadding() {
        return dp(compact ? 22 : 28);
    }

    int dp(float value) {
        return Math.round(value * density * uiScale);
    }
}
