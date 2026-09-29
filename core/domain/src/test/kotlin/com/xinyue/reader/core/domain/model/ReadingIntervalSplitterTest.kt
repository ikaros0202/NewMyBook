package com.xinyue.reader.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class ReadingIntervalSplitterTest {
    @Test
    fun `non-positive intervals are empty and long idle gaps cap at five minutes`() {
        assertThat(splitEffectiveReadingInterval(100, 100, ZoneId.of("UTC"))).isEmpty()
        assertThat(splitEffectiveReadingInterval(200, 100, ZoneId.of("UTC"))).isEmpty()
        assertThat(splitEffectiveReadingInterval(0, 60_000, ZoneId.of("UTC")).single().activeMillis)
            .isEqualTo(60_000)
        assertThat(splitEffectiveReadingInterval(0, 600_000, ZoneId.of("UTC")).single().activeMillis)
            .isEqualTo(300_000)
        assertThat(splitEffectiveReadingInterval(0, 300_000, ZoneId.of("UTC")).single().activeMillis)
            .isEqualTo(300_000)
    }

    @Test
    fun `interval splits at local midnight and captures the zone id`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = Instant.parse("2026-07-15T15:59:00Z").toEpochMilli()
        val end = Instant.parse("2026-07-15T16:01:00Z").toEpochMilli()

        val portions = splitEffectiveReadingInterval(start, end, zone)

        assertThat(portions.map(DailyReadingPortion::activeMillis)).containsExactly(60_000L, 60_000L).inOrder()
        assertThat(portions.map(DailyReadingPortion::localEpochDay)).containsExactly(20_649L, 20_650L).inOrder()
        assertThat(portions.map(DailyReadingPortion::zoneId)).containsExactly("Asia/Shanghai", "Asia/Shanghai")
    }

    @Test
    fun `spring and fall DST boundaries use true local dates`() {
        val zone = ZoneId.of("America/New_York")
        val springStart = Instant.parse("2026-03-08T06:58:00Z").toEpochMilli()
        val fallStart = Instant.parse("2026-11-01T05:58:00Z").toEpochMilli()

        assertThat(
            splitEffectiveReadingInterval(springStart, springStart + 240_000, zone).sumOf(DailyReadingPortion::activeMillis),
        ).isEqualTo(240_000)
        assertThat(
            splitEffectiveReadingInterval(fallStart, fallStart + 240_000, zone).sumOf(DailyReadingPortion::activeMillis),
        ).isEqualTo(240_000)
    }
}
