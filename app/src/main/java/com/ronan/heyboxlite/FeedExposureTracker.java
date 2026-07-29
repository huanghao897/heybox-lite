package com.ronan.heyboxlite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FeedExposureTracker {
    static final long MAX_AGE_MS = 60L * 60L * 1000L;
    static final int MAX_CANDIDATES = 60;
    static final int MAX_REPORTED = 50;

    private final LinkedHashMap<String, Long> pending = new LinkedHashMap<>();
    private final Set<String> lastLoadedIds = new HashSet<>();

    void recordLoaded(List<FeedItem> items, long now) {
        lastLoadedIds.clear();
        if (items == null) return;
        for (FeedItem item : items) {
            if (item == null || item.id.isEmpty()) continue;
            lastLoadedIds.add(item.id);
            pending.remove(item.id);
            pending.put(item.id, now);
        }
    }

    void markVisible(List<FeedItem> items, int firstPosition, int visibleCount,
                     int headerCount) {
        if (items == null || items.isEmpty() || visibleCount <= 0) return;
        int firstItem = Math.max(0, firstPosition - Math.max(0, headerCount));
        int lastItem = Math.min(items.size(),
                firstPosition + visibleCount - Math.max(0, headerCount));
        for (int index = firstItem; index < lastItem; index++) {
            FeedItem item = items.get(index);
            if (item != null && !item.id.isEmpty()) pending.remove(item.id);
        }
    }

    String valueForRequest(boolean refresh, long now) {
        if (!refresh) return null;
        prune(now);
        List<String> eligible = new ArrayList<>();
        for (String id : pending.keySet()) {
            if (!lastLoadedIds.contains(id)) eligible.add(id);
        }
        int start = Math.max(0, eligible.size() - MAX_CANDIDATES);
        int end = Math.min(eligible.size(), start + MAX_REPORTED);
        if (start >= end) return null;
        StringBuilder value = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (value.length() > 0) value.append(',');
            value.append(eligible.get(index));
        }
        return value.toString();
    }

    int pendingCount() {
        return pending.size();
    }

    private void prune(long now) {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Long> entry : pending.entrySet()) {
            if (now - entry.getValue() > MAX_AGE_MS) expired.add(entry.getKey());
        }
        for (String id : expired) pending.remove(id);
    }
}
