package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CheckinLeaderboardTest {
    @Test
    public void parsesCheckinAndSponsorshipRows() throws Exception {
        JSONObject payload = new JSONObject()
                .put("schema", 1)
                .put("checkin", new JSONArray()
                        .put(new JSONObject()
                                .put("rank", 1)
                                .put("display_name", "小**甲")
                                 .put("streak_days", 12)
                                 .put("value_label", "12 天")
                                 .put("initial", "小")
                                 .put("avatar_url", "https://heyboxlite.xyz/checkin/api/lite/leaderboard/avatar/checkin/1")))
                .put("sponsorship", new JSONArray()
                        .put(new JSONObject()
                                .put("rank", 1)
                                .put("display_name", "use***r")
                                .put("total_amount_cents", 2500)
                                .put("value_label", "¥25.00")
                                .put("initial", "u")));

        CheckinLeaderboard.Data result = CheckinLeaderboard.parse(payload);

        assertEquals(1, result.checkin.size());
        assertEquals("小**甲", result.checkin.get(0).displayName);
        assertEquals(12, result.checkin.get(0).value);
        assertEquals("https://heyboxlite.xyz/checkin/api/lite/leaderboard/avatar/checkin/1",
                result.checkin.get(0).avatarUrl);
        assertEquals(1, result.sponsorship.size());
        assertEquals(2500, result.sponsorship.get(0).value);
    }

    @Test
    public void acceptsEmptyRankings() throws Exception {
        CheckinLeaderboard.Data result = CheckinLeaderboard.parse(new JSONObject()
                .put("schema", 1)
                .put("checkin", new JSONArray())
                .put("sponsorship", new JSONArray()));

        assertTrue(result.checkin.isEmpty());
        assertTrue(result.sponsorship.isEmpty());
    }

    @Test(expected = CheckinCenterClient.ApiError.class)
    public void rejectsMalformedRankingRows() throws Exception {
        CheckinLeaderboard.parse(new JSONObject()
                .put("schema", 1)
                .put("checkin", new JSONArray().put(new JSONObject()
                        .put("rank", 0)
                        .put("display_name", "坏数据")
                        .put("streak_days", 1)
                        .put("value_label", "1 天")))
                .put("sponsorship", new JSONArray()));
    }

    @Test(expected = CheckinCenterClient.ApiError.class)
    public void rejectsOversizedRankingArrays() throws Exception {
        JSONArray rows = new JSONArray();
        for (int index = 1; index <= 101; index++) {
            rows.put(new JSONObject()
                    .put("rank", index)
                    .put("display_name", "用户")
                    .put("streak_days", 1)
                    .put("value_label", "1 天"));
        }
        CheckinLeaderboard.parse(new JSONObject()
                .put("schema", 1)
                .put("checkin", rows)
                .put("sponsorship", new JSONArray()));
    }

    @Test(expected = CheckinCenterClient.ApiError.class)
    public void rejectsUntrustedAvatarUrl() throws Exception {
        CheckinLeaderboard.parse(new JSONObject()
                .put("schema", 1)
                .put("checkin", new JSONArray().put(new JSONObject()
                        .put("rank", 1)
                        .put("display_name", "用户")
                        .put("streak_days", 1)
                        .put("value_label", "1 天")
                        .put("avatar_url", "https://example.com/avatar.jpg")))
                .put("sponsorship", new JSONArray()));
    }
}
