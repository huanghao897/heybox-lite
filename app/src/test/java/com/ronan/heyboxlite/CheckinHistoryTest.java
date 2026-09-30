package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CheckinHistoryTest {
    @Test
    public void checkedInSkippedRunIsShownAsSignedAndKeepsReward() throws Exception {
        JSONObject response = new JSONObject()
                .put("items", new JSONArray().put(new JSONObject()
                        .put("id", 17)
                        .put("status", "skipped")
                        .put("summary", "今日任务已执行")
                        .put("started_at", "2026-09-25 08:30")
                        .put("finished_at", JSONObject.NULL)
                        .put("check_in", new JSONObject()
                                .put("checked_in", true)
                                .put("coin_delta", 20)
                                .put("experience_delta", 20))));

        CheckinHistory.Entry entry = CheckinHistory.parse(response).entries.get(0);

        assertEquals("已签到", entry.state);
        assertEquals("09-25 08:30", entry.displayTime());
        assertEquals("今日任务已执行 · 盒币 +20 · 经验 +20",
                entry.summaryPreview());
    }

    @Test
    public void statusFallbackKeepsTheLatestRealRunVisible() {
        CheckinCenterClient.CheckinResult result = new CheckinCenterClient.CheckinResult(
                true, false, 20, 20, 4);
        CheckinCenterClient.LastRun run = new CheckinCenterClient.LastRun(
                18, "ok", "今日已签到", "2026-09-25 08:30",
                "2026-09-25 08:31", result);

        CheckinHistory.Entry entry = CheckinHistory.fromLastRun(run).entries.get(0);

        assertEquals("已签到", entry.state);
        assertEquals("09-25 08:31", entry.displayTime());
        assertEquals("今日已签到 · 盒币 +20 · 经验 +20",
                entry.summaryPreview());
    }
}
