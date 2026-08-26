package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed, display-safe data returned by the CheckinCenter leaderboard API. */
final class CheckinLeaderboard {
    static final class Data {
        final List<Entry> checkin;
        final List<Entry> sponsorship;

        Data(List<Entry> checkin, List<Entry> sponsorship) {
            this.checkin = checkin;
            this.sponsorship = sponsorship;
        }
    }

    static final class Entry {
        final int rank;
        final String displayName;
        final int value;
        final String valueLabel;
        final String initial;

        Entry(int rank, String displayName, int value, String valueLabel, String initial) {
            this.rank = rank;
            this.displayName = displayName;
            this.value = value;
            this.valueLabel = valueLabel;
            this.initial = initial;
        }
    }

    private CheckinLeaderboard() {}

    static Data parse(JSONObject value) throws CheckinCenterClient.ApiError {
        if (value == null || value.optInt("schema", -1) != 1) {
            throw protocolError();
        }
        return new Data(
                parseEntries(value.optJSONArray("checkin"), "streak_days"),
                parseEntries(value.optJSONArray("sponsorship"), "total_amount_cents"));
    }

    private static List<Entry> parseEntries(JSONArray values, String numericKey)
            throws CheckinCenterClient.ApiError {
        if (values == null || values.length() > 100) throw protocolError();
        List<Entry> entries = new ArrayList<>(values.length());
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) throw protocolError();
            int rank = item.optInt("rank", 0);
            int numericValue = item.optInt(numericKey, -1);
            String name = item.optString("display_name", "").trim();
            String label = item.optString("value_label", "").trim();
            String initial = item.optString("initial", "").trim();
            if (rank <= 0 || rank > 100 || numericValue < 0
                    || numericValue > 100_000_000 || name.isEmpty()
                    || name.length() > 128 || label.isEmpty() || label.length() > 64) {
                throw protocolError();
            }
            if (initial.isEmpty()) initial = firstCharacter(name);
            if (initial.isEmpty() || initial.length() > 4) throw protocolError();
            entries.add(new Entry(rank, name, numericValue, label, initial));
        }
        return Collections.unmodifiableList(entries);
    }

    private static String firstCharacter(String value) {
        if (value.isEmpty()) return "";
        int count = Character.charCount(value.codePointAt(0));
        return value.substring(0, Math.min(count, value.length()));
    }

    private static CheckinCenterClient.ApiError protocolError() {
        return new CheckinCenterClient.ApiError(
                CheckinCenterClient.Operation.LEADERBOARD,
                0,
                "签到服务响应异常",
                CheckinCenterClient.ErrorKind.PROTOCOL);
    }
}
