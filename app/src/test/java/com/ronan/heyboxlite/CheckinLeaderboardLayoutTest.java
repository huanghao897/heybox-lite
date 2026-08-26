package com.ronan.heyboxlite;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CheckinLeaderboardLayoutTest {
    @Test
    public void roundWatchUsesCompactMeasurements() {
        CheckinLeaderboardLayout layout = CheckinLeaderboardLayout.forMetrics(
                320, 320, 1.0f, 0.78f, true, true);

        assertTrue(layout.isCompact());
        assertTrue(layout.selectorHeight() >= 36);
        assertTrue(layout.avatarSize() >= 24);
        assertTrue(layout.dividerInset() > layout.rankWidth());
    }

    @Test
    public void rectangularWatchUsesCompactMeasurementsWithoutRoundAssumptions() {
        CheckinLeaderboardLayout layout = CheckinLeaderboardLayout.forMetrics(
                360, 480, 1.0f, 0.82f, false, true);

        assertTrue(layout.isCompact());
        assertTrue(layout.overviewSubtitle().contains("无需登录"));
        assertTrue(layout.valueColumnWidth(layout.pageHorizontalPadding(8), 1) > 0);
    }

    @Test
    public void narrowDisplayShortensIntroCopy() {
        CheckinLeaderboardLayout layout = CheckinLeaderboardLayout.forMetrics(
                240, 240, 1.0f, 1.0f, true, true);

        assertTrue(layout.overviewSubtitle().length() < 12);
    }

    @Test
    public void phoneKeepsMoreGenerousSpacing() {
        CheckinLeaderboardLayout layout = CheckinLeaderboardLayout.forMetrics(
                1080, 2400, 3.0f, 1.0f, false, false);

        assertTrue(!layout.isCompact());
        assertTrue(layout.sectionGap() > layout.compactGap());
    }

    @Test
    public void narrowLayoutKeepsRoomForNamesAndValues() {
        CheckinLeaderboardLayout layout = CheckinLeaderboardLayout.forMetrics(
                240, 240, 1.0f, 0.78f, true, true);

        int pagePadding = layout.pageHorizontalPadding(25);
        assertTrue(pagePadding >= 0);
        assertTrue(layout.valueColumnWidth(pagePadding, 100) >= 1);
    }

    @Test
    public void rectangularWatchDoesNotReceiveRoundScreenInsets() {
        CheckinLeaderboardLayout layout = CheckinLeaderboardLayout.forMetrics(
                360, 480, 1.0f, 0.82f, false, true);

        assertTrue(!layout.isRoundScreen());
        assertTrue(layout.isWatch());
        assertTrue(layout.pageHorizontalPadding(8) <= 8);
    }
}
