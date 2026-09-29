package com.xinyue.reader.feature.library

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingStatistics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class StatisticsFormattingTest {
    @Test
    fun `root statistics omits back while single book statistics keeps it`() {
        assertThat(statisticsShowsBackButton(null)).isFalse()
        assertThat(statisticsShowsBackButton("book-1")).isTrue()
    }

    @Test
    fun `period ranges use local day Monday week calendar month and DST boundaries`() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-05T12:00:00Z").toEpochMilli()

        val day = statisticsRange(now, StatisticsPeriod.DAY, zone)
        val week = statisticsRange(now, StatisticsPeriod.WEEK, zone)
        val month = statisticsRange(now, StatisticsPeriod.MONTH, zone)

        assertThat(day.localDates.map { it.toString() }).containsExactly("2026-03-05")
        assertThat(week.localDates.first().toString()).isEqualTo("2026-03-02")
        assertThat(week.localDates.last().toString()).isEqualTo("2026-03-08")
        assertThat(month.localDates.first().toString()).isEqualTo("2026-03-01")
        assertThat(month.localDates.last().toString()).isEqualTo("2026-03-31")
        assertThat(week.endEpochMillis - week.startEpochMillis).isEqualTo(167L * 60 * 60 * 1_000)
    }

    @Test
    fun `duration formatting handles zero sub minute minutes and hours`() {
        assertThat(formatReadingDuration(0)).isEqualTo("0 分钟")
        assertThat(formatReadingDuration(59_999)).isEqualTo("<1 分钟")
        assertThat(formatReadingDuration(60_000)).isEqualTo("1 分钟")
        assertThat(formatReadingDuration(3_720_000)).isEqualTo("1 小时 2 分钟")
    }

    @Test
    fun `chart duration labels stay compact while preserving readable units`() {
        assertThat(formatChartDuration(0)).isEqualTo("0m")
        assertThat(formatChartDuration(59_999)).isEqualTo("<1m")
        assertThat(formatChartDuration(45 * 60_000L)).isEqualTo("45m")
        assertThat(formatChartDuration(65 * 60_000L)).isEqualTo("1h5m")
    }

    @Test
    fun `chart buckets keep every day when the range fits the viewport`() {
        val start = LocalDate.of(2026, 8, 21)
        val daily = listOf(
            DailyReadingStat(start, 30 * 60_000L, 1),
            DailyReadingStat(start.plusDays(1), 0, 0),
            DailyReadingStat(start.plusDays(2), 90 * 60_000L, 2),
        )

        val buckets = statisticsChartBuckets(daily)

        assertThat(buckets).hasSize(3)
        assertThat(buckets.map { it.startDate }).containsExactly(
            start,
            start.plusDays(1),
            start.plusDays(2),
        ).inOrder()
        assertThat(buckets.map { it.activeMillis }).containsExactly(
            30 * 60_000L,
            0L,
            90 * 60_000L,
        ).inOrder()
    }

    @Test
    fun `chart buckets compress a month into seven additive windows`() {
        val start = LocalDate.of(2026, 8, 1)
        val daily = (0 until 31).map { index ->
            DailyReadingStat(
                date = start.plusDays(index.toLong()),
                activeMillis = (index + 1) * 60_000L,
                sessionCount = (index % 3).toLong(),
            )
        }

        val buckets = statisticsChartBuckets(daily)

        assertThat(buckets).hasSize(7)
        assertThat(buckets.first().startDate).isEqualTo(start)
        assertThat(buckets.last().endDate).isEqualTo(start.plusDays(30))
        assertThat(buckets.sumOf { it.activeMillis }).isEqualTo(daily.sumOf { it.activeMillis })
        assertThat(buckets.sumOf { it.sessionCount }).isEqualTo(daily.sumOf { it.sessionCount })
        assertThat(buckets.zipWithNext().all { (left, right) -> left.endDate.plusDays(1) == right.startDate })
            .isTrue()
    }

    @Test
    fun `book ranking is deterministic by time then title and id`() {
        val books = listOf(book("b", "同名"), book("a", "同名"), book("c", "更少"))
        val stats = mapOf(
            "a" to ReadingStatistics("a", 100, 1, 0, 1),
            "b" to ReadingStatistics("b", 100, 1, 0, 1),
            "c" to ReadingStatistics("c", 20, 1, 0, 1),
        )

        assertThat(rankBooks(books, stats).map { it.book.id }).containsExactly("a", "b", "c").inOrder()
    }

    private fun book(id: String, title: String) = Book(
        id = id,
        title = title,
        author = null,
        originalFileName = "$id.txt",
        originalPath = "books/$id/original.txt",
        normalizedPath = "books/$id/content.txt",
        charsetName = "UTF-8",
        contentSha256 = id,
        contentLength = 1,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )
}
