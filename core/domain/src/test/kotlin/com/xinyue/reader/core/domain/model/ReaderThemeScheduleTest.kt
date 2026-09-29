package com.xinyue.reader.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderThemeScheduleTest {
    private val customPresets = listOf(
        preset("morning"),
        preset("night"),
        preset("manual"),
    )

    @Test
    fun `follow system resolves light and dark themes`() {
        val schedule = ReaderThemeSchedule(
            mode = ThemeScheduleMode.FOLLOW_SYSTEM,
            lightThemeId = "morning",
            darkThemeId = "night",
        )

        assertThat(schedule.resolve(600, false, customPresets)).isEqualTo("morning")
        assertThat(schedule.resolve(600, true, customPresets)).isEqualTo("night")
    }

    @Test
    fun `fixed daytime interval switches at exact boundaries`() {
        val schedule = ReaderThemeSchedule(
            mode = ThemeScheduleMode.FIXED_TIME,
            lightThemeId = "morning",
            darkThemeId = "night",
            lightMinuteOfDay = 7 * 60,
            darkMinuteOfDay = 22 * 60,
        )

        assertThat(schedule.resolve(6 * 60 + 59, false, customPresets)).isEqualTo("night")
        assertThat(schedule.resolve(7 * 60, false, customPresets)).isEqualTo("morning")
        assertThat(schedule.resolve(21 * 60 + 59, false, customPresets)).isEqualTo("morning")
        assertThat(schedule.resolve(22 * 60, false, customPresets)).isEqualTo("night")
    }

    @Test
    fun `fixed overnight light interval switches correctly`() {
        val schedule = ReaderThemeSchedule(
            mode = ThemeScheduleMode.FIXED_TIME,
            lightThemeId = "morning",
            darkThemeId = "night",
            lightMinuteOfDay = 22 * 60,
            darkMinuteOfDay = 7 * 60,
        )

        assertThat(schedule.resolve(21 * 60 + 59, false, customPresets)).isEqualTo("night")
        assertThat(schedule.resolve(22 * 60, false, customPresets)).isEqualTo("morning")
        assertThat(schedule.resolve(6 * 60 + 59, false, customPresets)).isEqualTo("morning")
        assertThat(schedule.resolve(7 * 60, false, customPresets)).isEqualTo("night")
    }

    @Test
    fun `deleted preset falls back to matching built in theme`() {
        val schedule = ReaderThemeSchedule(
            mode = ThemeScheduleMode.FOLLOW_SYSTEM,
            lightThemeId = "deleted-light",
            darkThemeId = "deleted-dark",
        )

        assertThat(schedule.resolve(600, false, emptyList())).isEqualTo(BUILT_IN_PAPER_ID)
        assertThat(schedule.resolve(600, true, emptyList())).isEqualTo(BUILT_IN_DARK_ID)
    }

    @Test
    fun `manual override remains until automatic theme crosses the next switch`() {
        val schedule = ReaderThemeSchedule(
            mode = ThemeScheduleMode.FIXED_TIME,
            lightThemeId = "morning",
            darkThemeId = "night",
            lightMinuteOfDay = 7 * 60,
            darkMinuteOfDay = 22 * 60,
        )
        val override = ReaderThemeManualOverride(
            themeId = "manual",
            automaticThemeIdAtActivation = "morning",
        )

        assertThat(schedule.resolve(21 * 60 + 59, false, customPresets, override)).isEqualTo("manual")
        assertThat(schedule.resolve(22 * 60, false, customPresets, override)).isEqualTo("night")
    }

    @Test
    fun `corrupt persisted times and names resolve without throwing`() {
        val schedule = ReaderThemeSchedule(
            mode = ThemeScheduleMode.FIXED_TIME,
            lightThemeId = " ",
            darkThemeId = "missing",
            lightMinuteOfDay = -100,
            darkMinuteOfDay = 99_999,
        )

        assertThat(schedule.resolve(-1, false, emptyList())).isAnyOf(BUILT_IN_PAPER_ID, BUILT_IN_DARK_ID)
        assertThat(schedule.resolve(99_999, true, emptyList())).isAnyOf(BUILT_IN_PAPER_ID, BUILT_IN_DARK_ID)
    }

    private fun preset(id: String) = ReaderThemePreset(
        id = id,
        name = id,
        settings = ReaderSettings(),
        builtIn = false,
        updatedAtEpochMillis = 0,
    )
}
