package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 展示格式化的回归网：时长、缓存/离线体积、点赞缩写、小数与公告预览。 */
public class FormatTest {

    @Test
    public void compactDecimal_trimsTrailingZero() {
        assertEquals("3", Format.compactDecimal(3.0));
        assertEquals("3.5", Format.compactDecimal(3.5));
        assertEquals("3.1", Format.compactDecimal(3.14));
        assertEquals("0", Format.compactDecimal(0.0));
    }

    @Test
    public void readingDuration_buckets() {
        assertEquals("0 分钟", Format.readingDuration(0));
        assertEquals("0 分钟", Format.readingDuration(-5));
        assertEquals("<1 分钟", Format.readingDuration(30_000L));
        assertEquals("1 分钟", Format.readingDuration(60_000L));
        assertEquals("1 小时 30 分钟", Format.readingDuration(90L * 60_000L));
        assertEquals("2 小时", Format.readingDuration(120L * 60_000L));
    }

    @Test
    public void cacheMb_clampsNegative() {
        assertEquals("1.0 MB", Format.cacheMb(1_048_576L));
        assertEquals("0.0 MB", Format.cacheMb(0L));
        assertEquals("0.0 MB", Format.cacheMb(-100L));
    }

    @Test
    public void offlineSize_kbThenMb() {
        assertEquals("1 KB", Format.offlineSize(512L));      // 不足 1KB 也保底 1
        assertEquals("2 KB", Format.offlineSize(2048L));
        assertEquals("2.0 MB", Format.offlineSize(2L * 1_048_576L));
    }

    @Test
    public void commentLikeCount_thresholds() {
        assertEquals("999", Format.commentLikeCount(999));
        assertEquals("1K", Format.commentLikeCount(1_000));
        assertEquals("1.5K", Format.commentLikeCount(1_500));
        assertEquals("1万", Format.commentLikeCount(10_000));
        assertEquals("1.5万", Format.commentLikeCount(15_000));
        assertEquals("1亿", Format.commentLikeCount(100_000_000));
    }

    @Test
    public void announcementPreview_truncates() {
        assertEquals("", Format.announcementPreview(null));
        assertEquals("短公告", Format.announcementPreview("短公告"));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append('a');
        String preview = Format.announcementPreview(sb.toString());
        assertEquals(95, preview.length());        // 92 + "..."
        assertTrue(preview.endsWith("..."));
    }
}
