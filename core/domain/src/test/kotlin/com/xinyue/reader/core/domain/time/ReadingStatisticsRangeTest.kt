package com.xinyue.reader.core.domain.time

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

class ReadingStatisticsRangeTest {
    @Test
    fun `builds local day week and month half open ranges`() {
        val zone = ZoneOffset.UTC
        val now = LocalDate.of(2026, 7, 19).atTime(12, 0).toInstant(zone).toEpochMilli()

        assertThat(readingStatisticsRange(now, ReadingPeriod.DAY, zone).localDates)
            .containsExactly(LocalDate.of(2026, 7, 19))
        assertThat(readingStatisticsRange(now, ReadingPeriod.WEEK, zone).localDates)
            .containsExactlyElementsIn(
                (13..19).map { LocalDate.of(2026, 7, it) },
            ).inOrder()
        val month = readingStatisticsRange(now, ReadingPeriod.MONTH, zone)
        assertThat(month.localDates.first()).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(month.localDates.last()).isEqualTo(LocalDate.of(2026, 7, 31))
        assertThat(month.endEpochMillis).isEqualTo(
            LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}
