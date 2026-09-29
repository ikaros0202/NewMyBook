package com.xinyue.reader.core.domain.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ReadingPeriod {
    DAY,
    WEEK,
    MONTH,
}

data class ReadingStatisticsRange(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val localDates: List<LocalDate>,
)

fun readingStatisticsRange(
    nowEpochMillis: Long,
    period: ReadingPeriod,
    zoneId: ZoneId,
): ReadingStatisticsRange {
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
    val start = when (period) {
        ReadingPeriod.DAY -> today
        ReadingPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ReadingPeriod.MONTH -> today.withDayOfMonth(1)
    }
    val end = when (period) {
        ReadingPeriod.DAY -> start.plusDays(1)
        ReadingPeriod.WEEK -> start.plusWeeks(1)
        ReadingPeriod.MONTH -> start.plusMonths(1)
    }
    return readingStatisticsRange(start, end, zoneId)
}

fun readingStatisticsRange(
    start: LocalDate,
    end: LocalDate,
    zoneId: ZoneId,
): ReadingStatisticsRange = ReadingStatisticsRange(
    startEpochMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
    endEpochMillis = end.atStartOfDay(zoneId).toInstant().toEpochMilli(),
    localDates = generateSequence(start) { date -> date.plusDays(1).takeIf { it < end } }.toList(),
)
