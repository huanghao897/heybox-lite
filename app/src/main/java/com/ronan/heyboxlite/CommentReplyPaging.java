package com.ronan.heyboxlite;

final class CommentReplyPaging {
    static final int PAGE_SIZE = 5;

    private CommentReplyPaging() {}

    static int nextCount(int total, int shown, boolean hasMore) {
        int remaining = Math.max(0, total - shown);
        if (remaining > 0) return Math.min(PAGE_SIZE, remaining);
        return hasMore ? PAGE_SIZE : 0;
    }

    static String expansionLabel(int total, int shown, boolean hasMore) {
        int count = nextCount(total, shown, hasMore);
        return count <= 0 ? "" : "展开 " + count + " 条回复";
    }
}
