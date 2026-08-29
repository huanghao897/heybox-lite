package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CommentReplyPagingTest {
    @Test
    public void expandsFiveWhenAtLeastFiveRemain() {
        assertEquals("展开 5 条回复",
                CommentReplyPaging.expansionLabel(18, 2, true));
    }

    @Test
    public void usesKnownRemainingCountBelowFive() {
        assertEquals("展开 2 条回复",
                CommentReplyPaging.expansionLabel(4, 2, true));
    }

    @Test
    public void keepsPagingWhenServerHasMoreButTotalIsUnknown() {
        assertEquals("展开 5 条回复",
                CommentReplyPaging.expansionLabel(5, 5, true));
        assertEquals("", CommentReplyPaging.expansionLabel(5, 5, false));
    }
}
