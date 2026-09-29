package com.xinyue.reader.core.domain.model

import java.time.Instant
import java.time.ZoneId

const val READING_IDLE_TIMEOUT_MILLIS = 5L * 60L * 1_000L

data class DailyReadingPortion(
    val localEpochDay: Long,
    val activeMillis: Long,
    val zoneId: String,
)

fun splitEffectiveReadingInterval(
    lastInteractionAtEpochMillis: Long,
    nowEpochMillis: Long,
    zoneId: ZoneId,
): List<DailyReadingPortion> {
    if (nowEpochMillis <= lastInteractionAtEpochMillis) return emptyList()
    val endExclusive = minOf(
        nowEpochMillis,
        lastInteractionAtEpochMillis + READING_IDLE_TIMEOUT_MILLIS,
    )
    val portions = mutableListOf<DailyReadingPortion>()
    var cursor = lastInteractionAtEpochMillis
    while (cursor < endExclusive) {
        val localDate = Instant.ofEpochMilli(cursor).atZone(zoneId).toLocalDate()
        val nextLocalDay = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val portionEnd = minOf(endExclusive, nextLocalDay)
        check(portionEnd > cursor) { "无法解析本地日期边界" }
        portions += DailyReadingPortion(
            localEpochDay = localDate.toEpochDay(),
            activeMillis = portionEnd - cursor,
            zoneId = zoneId.id,
        )
        cursor = portionEnd
    }
    return portions
}
