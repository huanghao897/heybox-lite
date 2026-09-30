package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class CheckinHistory {
    static final class Entry {
        final long id;
        final String time;
        final String state;
        final String summary;
        final boolean checkedIn;
        final int coinDelta;
        final int experienceDelta;
        final List<String> details;

        Entry(JSONObject value) {
            id = value.optLong("id");
            String finished = value.isNull("finished_at") ? "" : value.optString("finished_at");
            time = finished.isEmpty() ? value.optString("started_at") : finished;
            JSONObject result = value.optJSONObject("check_in");
            checkedIn = result != null && result.optBoolean("checked_in");
            state = checkedIn ? "已签到" : stateLabel(value.optString("status"));
            coinDelta = optionalReward(result, "coin_delta");
            experienceDelta = optionalReward(result, "experience_delta");
            summary = value.optString("summary");
            details = new ArrayList<>();
            JSONArray fields = value.optJSONArray("details");
            for (int i = 0; fields != null && i < Math.min(12, fields.length()); i++) {
                JSONObject item = fields.optJSONObject(i);
                if (item == null) continue;
                String label = item.optString("label");
                String text = item.optString("value");
                if (!label.isEmpty() && !text.isEmpty()) details.add(label + " · " + text);
            }
        }

        Entry(CheckinCenterClient.LastRun run) {
            id = run.id;
            time = run.finishedAt == null || run.finishedAt.isEmpty()
                    ? run.startedAt : run.finishedAt;
            CheckinCenterClient.CheckinResult result = run.checkIn;
            checkedIn = result != null && result.checkedIn;
            state = checkedIn ? "已签到" : stateLabel(run.status);
            coinDelta = result == null ? -1 : result.coinDelta;
            experienceDelta = result == null ? -1 : result.experienceDelta;
            summary = run.summary == null ? "" : run.summary;
            details = new ArrayList<>();
        }

        String displayTime() {
            String value = time == null ? "" : time.trim();
            if (value.length() >= 16) {
                if (value.charAt(10) == 'T') {
                    return value.substring(5, 10) + " " + value.substring(11, 16);
                }
                return value.substring(5, 16);
            }
            return value.isEmpty() ? "时间未知" : value;
        }

        String rewardLabel() {
            StringBuilder value = new StringBuilder();
            if (coinDelta >= 0) value.append("盒币 +").append(coinDelta);
            if (experienceDelta >= 0) {
                if (value.length() > 0) value.append(" · ");
                value.append("经验 +").append(experienceDelta);
            }
            return value.toString();
        }

        String summaryPreview() {
            String value = summary == null ? "" : summary
                    .replace('\r', ' ').replace('\n', ' ').trim();
            String reward = rewardLabel();
            if (!reward.isEmpty() && value.indexOf("盒币") < 0
                    && value.indexOf("经验") < 0) {
                value = value.isEmpty() ? reward : value + " · " + reward;
            }
            if (value.isEmpty()) value = checkedIn ? "签到任务已完成" : state;
            return value.length() <= 58 ? value : value.substring(0, 58) + "...";
        }
    }

    final List<Entry> entries;

    private CheckinHistory(List<Entry> entries) { this.entries = entries; }

    static CheckinHistory fromLastRun(CheckinCenterClient.LastRun run) {
        List<Entry> entries = new ArrayList<>();
        if (run != null) entries.add(new Entry(run));
        return new CheckinHistory(entries);
    }

    static CheckinHistory parse(JSONObject body) throws JSONException {
        JSONArray items = body.getJSONArray("items");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < Math.min(10, items.length()); i++) {
            entries.add(new Entry(items.getJSONObject(i)));
        }
        return new CheckinHistory(entries);
    }

    private static String stateLabel(String state) {
        if ("ok".equals(state)) return "已完成";
        if ("running".equals(state)) return "执行中";
        if ("failed".equals(state)) return "失败";
        if ("skipped".equals(state)) return "未执行";
        return "暂无结果";
    }

    private static int optionalReward(JSONObject value, String key) {
        if (value == null || value.isNull(key) || !value.has(key)) return -1;
        int result = value.optInt(key, -1);
        return result >= 0 && result <= 1_000_000 ? result : -1;
    }
}
