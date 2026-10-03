package com.ronan.heyboxlite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class ComposeReadingStatsStateTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun cumulativeDurationCountsAndSevenDayAverageComeFromTheSnapshot() {
        val state = state(
            todayMs = 60_000L,
            todayCount = 3,
            articleMs = 120_000L,
            postMs = 300_000L,
            totalCount = 17,
            weekMs = longArrayOf(0L, 0L, 0L, 0L, 0L, 0L, 420_000L),
        )

        assertEquals(60_000L, state.todayMs)
        assertEquals(3, state.todayCount)
        assertEquals(420_000L, state.totalMs)
        assertEquals(17, state.totalCount)
        assertEquals(420_000L, state.weekTotalMs)
        assertEquals(60_000L, state.weekAverageMs)
    }

    @Test
    fun weekdaysWrapFromSundayToSaturdayAcrossAMonthBoundary() {
        val state = state(nowMillis = timestamp(2026, Calendar.OCTOBER, 3))

        assertEquals(listOf("20260927", "20260928", "20260929", "20260930", "20261001", "20261002", "20261003"),
            state.weekDays.map { it.dateStamp })
        assertEquals(listOf("\u65e5", "\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u4eca\u5929"),
            state.weekDays.map { it.label })
        assertEquals("10 \u6708 3 \u65e5", state.todayDateLabel)
        assertTrue(state.weekDays.last().isToday)
        assertTrue(state.weekDays.dropLast(1).none { it.isToday })
    }

    @Test
    fun aSundayTodayKeepsThePreviousSaturdayLabel() {
        val state = state(nowMillis = timestamp(2026, Calendar.OCTOBER, 4))

        assertEquals(listOf("\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d", "\u4eca\u5929"),
            state.weekDays.map { it.label })
        assertEquals("20260928", state.weekDays.first().dateStamp)
        assertEquals("20261004", state.weekDays.last().dateStamp)
    }

    @Test
    fun sevenDayDatesCrossTheYearBoundary() {
        val state = state(nowMillis = timestamp(2027, Calendar.JANUARY, 2))

        assertEquals(listOf("20261227", "20261228", "20261229", "20261230", "20261231", "20270101", "20270102"),
            state.weekDays.map { it.dateStamp })
        assertEquals("\u65e5", state.weekDays.first().label)
        assertEquals("\u4e94", state.weekDays[5].label)
    }

    @Test
    fun leapDayRemainsARealDayInTheSevenDayWindow() {
        val state = state(nowMillis = timestamp(2024, Calendar.MARCH, 2))

        assertEquals(listOf("20240225", "20240226", "20240227", "20240228", "20240229", "20240301", "20240302"),
            state.weekDays.map { it.dateStamp })
        assertEquals("\u56db", state.weekDays[4].label)
    }

    @Test
    fun localDateUsesTheInjectedTimeZoneRatherThanTheJvmDefault() {
        val instant = timestamp(2026, Calendar.OCTOBER, 1, hour = 0, minute = 30)
        val eastern = state(nowMillis = instant, timeZone = TimeZone.getTimeZone("America/New_York"))
        val utcState = state(nowMillis = instant, timeZone = utc)

        assertEquals("20260930", eastern.weekDays.last().dateStamp)
        assertEquals("9 \u6708 30 \u65e5", eastern.todayDateLabel)
        assertEquals("20261001", utcState.weekDays.last().dateStamp)
        assertEquals("10 \u6708 1 \u65e5", utcState.todayDateLabel)
    }

    @Test
    fun daylightSavingTransitionStillProducesSevenConsecutiveLocalDates() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val state = state(nowMillis = timestamp(2026, Calendar.MARCH, 10, hour = 0, minute = 30, timeZone = zone),
            timeZone = zone)

        assertEquals(listOf("20260304", "20260305", "20260306", "20260307", "20260308", "20260309", "20260310"),
            state.weekDays.map { it.dateStamp })
        assertEquals("\u65e5", state.weekDays[4].label)
        assertEquals("\u4e00", state.weekDays[5].label)
    }

    @Test
    fun zeroDurationsKeepTheStripEmptyAndEveryFractionFinite() {
        val state = state(topics = listOf(ReadingStatsTopicInput("Empty", 0L)))

        assertEquals(0L, state.totalMs)
        assertEquals(0L, state.weekTotalMs)
        assertEquals(0L, state.weekAverageMs)
        assertEquals(0, state.article.percent)
        assertEquals(0, state.post.percent)
        assertEquals(0f, state.article.fraction, 0f)
        assertEquals(0f, state.post.fraction, 0f)
        state.weekDays.forEach { assertEquals(0f, it.fraction, 0f) }
        assertEquals(0f, state.topics.single().fraction, 0f)
        assertFalse(state.topics.single().fraction.isNaN())
        assertTrue(this.state().topics.isEmpty())
    }

    @Test
    fun weekBarsUseTheLargestDayAndPreserveZeroDaysAndInputOrder() {
        val durations = longArrayOf(0L, 60_000L, 120_000L, 240_000L, 0L, 30_000L, 180_000L)
        val state = state(weekMs = durations)

        assertEquals(durations.toList(), state.weekDays.map { it.milliseconds })
        val expected = listOf(0f, 0.25f, 0.5f, 1f, 0f, 0.125f, 0.75f)
        state.weekDays.forEachIndexed { index, day -> assertEquals(expected[index], day.fraction, 0.00001f) }
        assertEquals(630_000L, state.weekTotalMs)
        assertEquals(90_000L, state.weekAverageMs)
        durations[0] = 999L
        assertEquals(0L, state.weekDays.first().milliseconds)
    }

    @Test
    fun averageDividesByAllSevenDaysAndKeepsMillisecondsUntilFormatting() {
        val state = state(weekMs = longArrayOf(0L, 0L, 0L, 0L, 0L, 0L, 8L))

        assertEquals(8L, state.weekTotalMs)
        assertEquals(1L, state.weekAverageMs)
    }

    @Test
    fun articleAndPostFractionsUseDurationAndPercentagesRoundLikeTheJavaPage() {
        val state = state(articleMs = 2L, postMs = 1L)

        assertEquals(3L, state.totalMs)
        assertEquals(2f / 3f, state.article.fraction, 0.00001f)
        assertEquals(1f / 3f, state.post.fraction, 0.00001f)
        assertEquals(67, state.article.percent)
        assertEquals(33, state.post.percent)
        assertEquals(2L, state.article.milliseconds)
        assertEquals(1L, state.post.milliseconds)
    }

    @Test
    fun aSingleContentTypeUsesTheWholeStripWithoutATokenZeroSegment() {
        val articlesOnly = state(articleMs = 60_000L)
        val postsOnly = state(postMs = 60_000L)

        assertEquals(1f, articlesOnly.article.fraction, 0f)
        assertEquals(100, articlesOnly.article.percent)
        assertEquals(0f, articlesOnly.post.fraction, 0f)
        assertEquals(0, articlesOnly.post.percent)
        assertEquals(1f, postsOnly.post.fraction, 0f)
        assertEquals(100, postsOnly.post.percent)
        assertEquals(0f, postsOnly.article.fraction, 0f)
    }

    @Test
    fun communityTracksAreRelativeToTheLargestCommunityNotTotalReadingTime() {
        val state = state(articleMs = 1_000L, postMs = 1_000L,
            topics = listOf(ReadingStatsTopicInput("First", 100L),
                ReadingStatsTopicInput("Second", 25L), ReadingStatsTopicInput("Third", 50L)))

        assertEquals(listOf("First", "Second", "Third"), state.topics.map { it.name })
        assertEquals(1f, state.topics[0].fraction, 0f)
        assertEquals(0.25f, state.topics[1].fraction, 0f)
        assertEquals(0.5f, state.topics[2].fraction, 0f)
        assertEquals(25L, state.topics[1].milliseconds)
    }

    @Test
    fun onlyTheFirstFiveCommunitiesAreDisplayed() {
        val topics = (1..7).map { ReadingStatsTopicInput("Topic $it", 800L - it * 100L) }
        val state = state(topics = topics)

        assertEquals(5, state.topics.size)
        assertEquals(topics.take(5).map { it.name }, state.topics.map { it.name })
        assertEquals(1f, state.topics.first().fraction, 0f)
        assertEquals(3f / 7f, state.topics.last().fraction, 0.00001f)
    }

    @Test
    fun proportionsDoNotOverflowWhenDurationsAreMultipliedByOneHundred() {
        val state = state(articleMs = Long.MAX_VALUE / 2L, postMs = Long.MAX_VALUE / 2L)

        assertEquals(Long.MAX_VALUE - 1L, state.totalMs)
        assertEquals(0.5f, state.article.fraction, 0f)
        assertEquals(0.5f, state.post.fraction, 0f)
        assertEquals(50, state.article.percent)
        assertEquals(50, state.post.percent)
    }

    @Test
    fun overflowingTotalsNeverProduceNegativeDurationsOrInvalidFractions() {
        val state = state(articleMs = Long.MAX_VALUE, postMs = Long.MAX_VALUE,
            weekMs = LongArray(7) { Long.MAX_VALUE })

        assertEquals(Long.MAX_VALUE, state.totalMs)
        assertEquals(Long.MAX_VALUE, state.weekTotalMs)
        assertEquals(0.5f, state.article.fraction, 0f)
        assertEquals(50, state.article.percent)
        state.weekDays.forEach { assertEquals(1f, it.fraction, 0f) }
    }

    @Test
    fun theSameSnapshotClockAndZoneProduceTheSameUiState() {
        val first = state(nowMillis = timestamp(2026, Calendar.OCTOBER, 3))
        val second = state(nowMillis = timestamp(2026, Calendar.OCTOBER, 3))

        assertEquals(first, second)
    }

    private fun state(
        todayMs: Long = 0L,
        todayCount: Int = 0,
        articleMs: Long = 0L,
        postMs: Long = 0L,
        totalCount: Int = 0,
        weekMs: LongArray = LongArray(7),
        topics: List<ReadingStatsTopicInput> = emptyList(),
        nowMillis: Long = timestamp(2026, Calendar.OCTOBER, 3),
        timeZone: TimeZone = utc,
    ): ComposeReadingStatsState = ComposeReadingStatsMapper.map(
        todayMs, todayCount, articleMs, postMs, totalCount, weekMs, topics, nowMillis, timeZone,
    )

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
        timeZone: TimeZone = utc,
    ): Long = GregorianCalendar(timeZone, Locale.US).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis
}
