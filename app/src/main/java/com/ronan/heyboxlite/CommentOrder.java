package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CommentOrder {
    private CommentOrder() {}

    static List<JSONObject> sorted(JSONArray groups, boolean latest) {
        List<JSONObject> threads = new ArrayList<>();
        if (groups == null) return threads;
        for (int index = 0; index < groups.length(); index++) {
            JSONObject group = groups.optJSONObject(index);
            if (group != null) threads.add(group);
        }
        Collections.sort(threads, (left, right) -> {
            int pinned = Boolean.compare(CommentData.isPinnedThread(right),
                    CommentData.isPinnedThread(left));
            if (pinned != 0) return pinned;
            return latest
                    ? Long.compare(CommentData.threadTime(right),
                    CommentData.threadTime(left))
                    : Integer.compare(CommentData.threadLikes(right),
                    CommentData.threadLikes(left));
        });
        return threads;
    }
}
