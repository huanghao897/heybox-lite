package com.ronan.heyboxlite

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

internal data class ReadingStatsTopicInput(val name: String, val milliseconds: Long)

internal data class ReadingStatsDayState(
    val dateStamp: String,
    val label: String,
    val milliseconds: Long,
    val fraction: Float,
    val isToday: Boolean,
)

internal data class ReadingStatsPartState(
    val milliseconds: Long,
    val fraction: Float,
    val percent: Int,
)

internal data class ReadingStatsTopicState(
    val name: String,
    val milliseconds: Long,
    val fraction: Float,
)

internal data class ComposeReadingStatsState(
    val todayDateLabel: String,
    val todayMs: Long,
    val todayCount: Int,
    val totalMs: Long,
    val totalCount: Int,
    val weekTotalMs: Long,
    val weekAverageMs: Long,
    val weekDays: List<ReadingStatsDayState>,
    val article: ReadingStatsPartState,
    val post: ReadingStatsPartState,
    val topics: List<ReadingStatsTopicState>,
)

internal object ComposeReadingStatsMapper {
    fun map(
        todayMs: Long,
        todayCount: Int,
        totalArticleMs: Long,
        totalPostMs: Long,
        totalCount: Int,
        weekMs: LongArray,
        topics: List<ReadingStatsTopicInput>,
        nowMillis: Long,
        timeZone: TimeZone,
    ): ComposeReadingStatsState {
        val today = GregorianCalendar(timeZone, Locale.US).apply { timeInMillis = nowMillis }
        val day = today.clone() as Calendar
        day.add(Calendar.DAY_OF_YEAR, -6)
        val weekdayNames = listOf("\u65e5", "\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d")
        val durations = weekMs.map { it.coerceAtLeast(0L) }
        val weekMax = (durations.maxOrNull() ?: 0L).coerceAtLeast(1L).toDouble()
        val weekDays = durations.mapIndexed { index, milliseconds ->
            val isToday = index == 6
            ReadingStatsDayState(
                dateStamp = String.format(Locale.US, "%04d%02d%02d",
                    day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)),
                label = if (isToday) "\u4eca\u5929" else weekdayNames[day.get(Calendar.DAY_OF_WEEK) - 1],
                milliseconds = milliseconds,
                fraction = fraction(milliseconds, weekMax).toFloat(),
                isToday = isToday,
            ).also { day.add(Calendar.DAY_OF_YEAR, 1) }
        }
        val weekTotal = sumDurations(durations)
        val articleMs = totalArticleMs.coerceAtLeast(0L)
        val postMs = totalPostMs.coerceAtLeast(0L)
        val splitTotal = articleMs.toDouble() + postMs.toDouble()
        val topicMax = (topics.maxOfOrNull { it.milliseconds } ?: 0L).coerceAtLeast(1L).toDouble()
        return ComposeReadingStatsState(
            todayDateLabel = "${today.get(Calendar.MONTH) + 1} \u6708 ${today.get(Calendar.DAY_OF_MONTH)} \u65e5",
            todayMs = todayMs.coerceAtLeast(0L),
            todayCount = todayCount,
            totalMs = sumDurations(listOf(articleMs, postMs)),
            totalCount = totalCount,
            weekTotalMs = weekTotal,
            weekAverageMs = weekTotal / 7L,
            weekDays = weekDays,
            article = part(articleMs, splitTotal),
            post = part(postMs, splitTotal),
            topics = topics.take(5).map { topic ->
                val milliseconds = topic.milliseconds.coerceAtLeast(0L)
                ReadingStatsTopicState(topic.name, milliseconds, fraction(milliseconds, topicMax).toFloat())
            },
        )
    }

    private fun fraction(milliseconds: Long, total: Double): Double =
        if (total <= 0.0) 0.0 else (milliseconds.toDouble() / total).coerceIn(0.0, 1.0)

    private fun part(milliseconds: Long, total: Double): ReadingStatsPartState {
        val ratio = fraction(milliseconds, total)
        return ReadingStatsPartState(milliseconds, ratio.toFloat(), (ratio * 100.0).roundToInt())
    }

    // Extreme snapshots must not wrap displayed durations below zero.
    private fun sumDurations(values: List<Long>): Long =
        values.fold(0L) { total, value -> total + value.coerceAtMost(Long.MAX_VALUE - total) }
}
