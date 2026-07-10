package com.openzen.heyboxcommunity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ReadingTimeTracker {
    private static final String PREFS = "heybox_reading_time";
    private static final String DAY = "day";
    private static final String TODAY_ARTICLE = "today_article_ms";
    private static final String TODAY_POST = "today_post_ms";
    private static final String TOTAL_ARTICLE = "total_article_ms";
    private static final String TOTAL_POST = "total_post_ms";

    private final SharedPreferences prefs;
    private boolean active;
    private boolean article;
    private long startedAt;

    ReadingTimeTracker(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        resetTodayIfNeeded();
    }

    void start(boolean isArticle) {
        if (active && article == isArticle) return;
        pause();
        resetTodayIfNeeded();
        active = true;
        article = isArticle;
        startedAt = SystemClock.elapsedRealtime();
    }

    void pause() {
        if (!active) return;
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
        active = false;
        if (elapsed == 0L) return;
        resetTodayIfNeeded();
        String todayKey = article ? TODAY_ARTICLE : TODAY_POST;
        String totalKey = article ? TOTAL_ARTICLE : TOTAL_POST;
        prefs.edit()
                .putLong(todayKey, prefs.getLong(todayKey, 0L) + elapsed)
                .putLong(totalKey, prefs.getLong(totalKey, 0L) + elapsed)
                .apply();
    }

    Stats stats() {
        resetTodayIfNeeded();
        long live = active ? Math.max(0L, SystemClock.elapsedRealtime() - startedAt) : 0L;
        long todayArticle = prefs.getLong(TODAY_ARTICLE, 0L) + (active && article ? live : 0L);
        long todayPost = prefs.getLong(TODAY_POST, 0L) + (active && !article ? live : 0L);
        long totalArticle = prefs.getLong(TOTAL_ARTICLE, 0L) + (active && article ? live : 0L);
        long totalPost = prefs.getLong(TOTAL_POST, 0L) + (active && !article ? live : 0L);
        return new Stats(todayArticle, todayPost, totalArticle, totalPost);
    }

    private void resetTodayIfNeeded() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (today.equals(prefs.getString(DAY, ""))) return;
        prefs.edit()
                .putString(DAY, today)
                .putLong(TODAY_ARTICLE, 0L)
                .putLong(TODAY_POST, 0L)
                .apply();
    }

    static final class Stats {
        final long todayArticleMs;
        final long todayPostMs;
        final long totalArticleMs;
        final long totalPostMs;

        Stats(long todayArticleMs, long todayPostMs, long totalArticleMs, long totalPostMs) {
            this.todayArticleMs = todayArticleMs;
            this.todayPostMs = todayPostMs;
            this.totalArticleMs = totalArticleMs;
            this.totalPostMs = totalPostMs;
        }

        long todayMs() {
            return todayArticleMs + todayPostMs;
        }

        long totalMs() {
            return totalArticleMs + totalPostMs;
        }
    }
}
