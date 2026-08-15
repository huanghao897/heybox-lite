package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RoundLayoutMetricsTest {
    @Test
    public void pageInsetTargetsRoundSafeArea() {
        assertEquals(42, RoundLayoutMetrics.componentInset(
                400, RoundLayoutMetrics.PAGE_HORIZONTAL_RATIO, 8));
    }

    @Test
    public void pageTypesUseDifferentTopSafeAreas() {
        assertEquals(44, RoundLayoutMetrics.componentInset(
                400, RoundLayoutMetrics.PAGE_TOP_RATIO, 8));
        assertEquals(36, RoundLayoutMetrics.componentInset(
                400, RoundLayoutMetrics.SUBPAGE_TOP_RATIO, 8));
    }

    @Test
    public void headerAndSearchHaveIndependentWidths() {
        assertEquals(52, RoundLayoutMetrics.componentInset(
                400, RoundLayoutMetrics.HEADER_HORIZONTAL_RATIO, 0));
        assertEquals(30, RoundLayoutMetrics.componentInset(
                400, RoundLayoutMetrics.SEARCH_HORIZONTAL_RATIO, 0));
        assertEquals(10, RoundLayoutMetrics.headerInnerInset(400));
    }

    @Test
    public void invalidExtentsAreClampedAndMinimumIsPreserved() {
        assertEquals(8, RoundLayoutMetrics.componentInset(
                -1, RoundLayoutMetrics.PAGE_HORIZONTAL_RATIO, 8));
        assertEquals(0, RoundLayoutMetrics.componentInset(
                -1, RoundLayoutMetrics.PAGE_HORIZONTAL_RATIO, 0));
    }

}
