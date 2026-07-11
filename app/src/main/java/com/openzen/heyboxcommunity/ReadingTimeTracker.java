package com.openzen.heyboxcommunity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 阅读时长统计：只在详情页计时。
 * 维度：今日/累计的文章与帖子构成、近 14 天逐日时长、看帖篇数、按社区（话题）累计。
 * 同一帖子重复进入不重复计篇数；话题在详情数据返回后通过 {@link #tagTopic} 补记。
 */
final class ReadingTimeTracker {
    private static final String PREFS = "heybox_reading_time";
    private static final String DAY = "day";
    private static final String TODAY_ARTICLE = "today_article_ms";
    private static final String TODAY_POST = "today_post_ms";
    private static final String TOTAL_ARTICLE = "total_article_ms";
    private static final String TOTAL_POST = "total_post_ms";
    private static final String TODAY_COUNT = "today_count";
    private static final String TOTAL_COUNT = "total_count";
    private static final String DAY_MS_PREFIX = "day_ms_";
    private static final String TOPIC_MS_PREFIX = "topic_ms_";
    private static final int DAY_KEEP = 14;
    private static final int TOPIC_KEEP = 30;

    private final SharedPreferences prefs;
    private boolean active;
    private boolean article;
    private long startedAt;
    private String currentLink = "";
    private String currentTopic = "";
    private String lastCountedLink = "";

    ReadingTimeTracker(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        resetTodayIfNeeded();
    }

    void start(boolean isArticle, String linkId) {
        String id = linkId == null ? "" : linkId;
        if (active && article == isArticle && id.equals(currentLink)) return;
        pause();
        resetTodayIfNeeded();
        if (!id.isEmpty() && !id.equals(lastCountedLink)) {
            lastCountedLink = id;
            prefs.edit()
                    .putInt(TODAY_COUNT, prefs.getInt(TODAY_COUNT, 0) + 1)
                    .putInt(TOTAL_COUNT, prefs.getInt(TOTAL_COUNT, 0) + 1)
                    .apply();
        }
        active = true;
        article = isArticle;
        currentLink = id;
        currentTopic = "";
        startedAt = SystemClock.elapsedRealtime();
    }

    /** 详情接口返回话题后补记；当前这段阅读时长会计入该社区。 */
    void tagTopic(String topicName) {
        if (topicName == null || topicName.isEmpty()) return;
        currentTopic = topicName;
    }

    void pause() {
        if (!active) return;
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
        active = false;
        if (elapsed == 0L) return;
        resetTodayIfNeeded();
        String todayKey = article ? TODAY_ARTICLE : TODAY_POST;
        String totalKey = article ? TOTAL_ARTICLE : TOTAL_POST;
        String dayKey = DAY_MS_PREFIX + dayStamp(0);
        SharedPreferences.Editor edit = prefs.edit()
                .putLong(todayKey, prefs.getLong(todayKey, 0L) + elapsed)
                .putLong(totalKey, prefs.getLong(totalKey, 0L) + elapsed)
                .putLong(dayKey, prefs.getLong(dayKey, 0L) + elapsed);
        if (!currentTopic.isEmpty()) {
            String topicKey = TOPIC_MS_PREFIX + currentTopic;
            edit.putLong(topicKey, prefs.getLong(topicKey, 0L) + elapsed);
        }
        edit.apply();
    }

    Stats stats() {
        resetTodayIfNeeded();
        long live = active ? Math.max(0L, SystemClock.elapsedRealtime() - startedAt) : 0L;
        long liveArticle = active && article ? live : 0L;
        long livePost = active && !article ? live : 0L;
        long[] weekMs = new long[7];
        for (int i = 0; i < 7; i++) {
            weekMs[i] = prefs.getLong(DAY_MS_PREFIX + dayStamp(i - 6), 0L);
        }
        weekMs[6] += live;
        List<TopicStat> topics = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(TOPIC_MS_PREFIX)) continue;
            Object raw = entry.getValue();
            long value = raw instanceof Long ? (Long) raw : 0L;
            String name = entry.getKey().substring(TOPIC_MS_PREFIX.length());
            if (active && name.equals(currentTopic)) value += live;
            if (value > 0L && !name.isEmpty()) topics.add(new TopicStat(name, value));
        }
        Collections.sort(topics, (a, b) -> Long.compare(b.ms, a.ms));
        return new Stats(
                prefs.getLong(TODAY_ARTICLE, 0L) + liveArticle,
                prefs.getLong(TODAY_POST, 0L) + livePost,
                prefs.getLong(TOTAL_ARTICLE, 0L) + liveArticle,
                prefs.getLong(TOTAL_POST, 0L) + livePost,
                prefs.getInt(TODAY_COUNT, 0),
                prefs.getInt(TOTAL_COUNT, 0),
                weekMs, topics);
    }

    private String dayStamp(int offsetDays) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, offsetDays);
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.getTime());
    }

    private void resetTodayIfNeeded() {
        String today = dayStamp(0);
        if (today.equals(prefs.getString(DAY, ""))) return;
        SharedPreferences.Editor edit = prefs.edit()
                .putString(DAY, today)
                .putLong(TODAY_ARTICLE, 0L)
                .putLong(TODAY_POST, 0L)
                .putInt(TODAY_COUNT, 0);
        String oldest = dayStamp(-(DAY_KEEP - 1));
        List<TopicStat> topicEntries = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(DAY_MS_PREFIX)) {
                if (key.substring(DAY_MS_PREFIX.length()).compareTo(oldest) < 0) edit.remove(key);
            } else if (key.startsWith(TOPIC_MS_PREFIX)) {
                Object raw = entry.getValue();
                topicEntries.add(new TopicStat(key, raw instanceof Long ? (Long) raw : 0L));
            }
        }
        if (topicEntries.size() > TOPIC_KEEP) {
            Collections.sort(topicEntries, (a, b) -> Long.compare(b.ms, a.ms));
            for (int i = TOPIC_KEEP; i < topicEntries.size(); i++) {
                edit.remove(topicEntries.get(i).name);
            }
        }
        edit.apply();
    }

    static final class TopicStat {
        final String name;
        final long ms;

        TopicStat(String name, long ms) {
            this.name = name;
            this.ms = ms;
        }
    }

    static final class Stats {
        final long todayArticleMs;
        final long todayPostMs;
        final long totalArticleMs;
        final long totalPostMs;
        final int todayCount;
        final int totalCount;
        /** 近 7 天逐日时长，最后一项是今天。 */
        final long[] weekMs;
        /** 按社区累计，时长降序。 */
        final List<TopicStat> topics;

        Stats(long todayArticleMs, long todayPostMs, long totalArticleMs, long totalPostMs,
              int todayCount, int totalCount, long[] weekMs, List<TopicStat> topics) {
            this.todayArticleMs = todayArticleMs;
            this.todayPostMs = todayPostMs;
            this.totalArticleMs = totalArticleMs;
            this.totalPostMs = totalPostMs;
            this.todayCount = todayCount;
            this.totalCount = totalCount;
            this.weekMs = weekMs;
            this.topics = topics;
        }

        long todayMs() {
            return todayArticleMs + todayPostMs;
        }

        long totalMs() {
            return totalArticleMs + totalPostMs;
        }

        long weekTotalMs() {
            long sum = 0L;
            for (long value : weekMs) sum += value;
            return sum;
        }
    }
}
