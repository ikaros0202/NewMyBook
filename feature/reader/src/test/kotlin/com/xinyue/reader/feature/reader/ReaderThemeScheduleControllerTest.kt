package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.ThemeScheduleMode
import com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository
import com.xinyue.reader.core.domain.time.EpochClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderThemeScheduleControllerTest {
    @Test
    fun `fixed schedule switches exactly at the next boundary`() = runTest {
        val base = Instant.parse("2026-07-15T06:59:00Z").toEpochMilli()
        val repository = FakeScheduleRepository(fixedSchedule())
        val controller = controller(repository, base)
        val applied = mutableListOf<String?>()

        controller.start(backgroundScope) { applied += it }
        runCurrent()
        assertThat(applied).containsExactly("night")

        advanceTimeBy(60_000)
        runCurrent()
        assertThat(applied).containsExactly("night", "morning").inOrder()
    }

    @Test
    fun `follow system reacts to dark changes without clock polling`() = runTest {
        val repository = FakeScheduleRepository(
            ReaderThemeSchedule(
                mode = ThemeScheduleMode.FOLLOW_SYSTEM,
                lightThemeId = "morning",
                darkThemeId = "night",
            ),
        )
        val systemDark = MutableStateFlow(false)
        val delays = mutableListOf<Long>()
        val controller = controller(
            repository = repository,
            baseEpochMillis = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli(),
            systemDark = systemDark,
            delayUntil = { millis -> delays += millis; awaitCancellation() },
        )
        val applied = mutableListOf<String?>()

        controller.start(backgroundScope) { applied += it }
        runCurrent()
        systemDark.value = true
        runCurrent()

        assertThat(applied).containsExactly("morning", "night").inOrder()
        assertThat(delays).isEmpty()
    }

    @Test
    fun `fixed schedule requests one boundary delay rather than minute polling`() = runTest {
        val base = Instant.parse("2026-07-15T06:59:10Z").toEpochMilli()
        val delays = mutableListOf<Long>()
        val controller = controller(
            repository = FakeScheduleRepository(fixedSchedule()),
            baseEpochMillis = base,
            delayUntil = { millis -> delays += millis; awaitCancellation() },
        )

        controller.start(backgroundScope) {}
        runCurrent()

        assertThat(delays).containsExactly(50_000L)
    }

    @Test
    fun `time or timezone signal cancels the old delay and recomputes`() = runTest {
        val base = Instant.parse("2026-07-15T06:00:00Z").toEpochMilli()
        val repository = FakeScheduleRepository(fixedSchedule())
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var zone = ZoneId.of("UTC")
        val delays = mutableListOf<Long>()
        val controller = controller(
            repository = repository,
            baseEpochMillis = base,
            timeChanges = changes,
            zoneIdProvider = { zone },
            delayUntil = { millis -> delays += millis; awaitCancellation() },
        )

        controller.start(backgroundScope) {}
        runCurrent()
        zone = ZoneId.of("Asia/Shanghai")
        changes.tryEmit(Unit)
        runCurrent()

        assertThat(delays).containsExactly(60 * 60 * 1000L, 8 * 60 * 60 * 1000L).inOrder()
    }

    @Test
    fun `missing scheduled theme falls back and manual theme expires at boundary`() = runTest {
        val base = Instant.parse("2026-07-15T21:00:00Z").toEpochMilli()
        val repository = FakeScheduleRepository(
            fixedSchedule().copy(lightThemeId = "missing", darkThemeId = "night"),
        )
        val controller = controller(repository, base)
        val applied = mutableListOf<String?>()

        controller.start(backgroundScope) { applied += it }
        runCurrent()
        assertThat(applied.last()).isEqualTo("built-in-paper")

        controller.recordManualTheme("manual")
        runCurrent()
        assertThat(repository.manual.value?.themeId).isEqualTo("manual")
        assertThat(repository.manual.value?.expiresAtEpochMillis)
            .isEqualTo(Instant.parse("2026-07-15T22:00:00Z").toEpochMilli())
        assertThat(applied.last()).isEqualTo("manual")

        advanceTimeBy(60 * 60 * 1000L)
        runCurrent()
        assertThat(applied.last()).isEqualTo("night")
        assertThat(repository.manual.value).isNull()
    }

    @Test
    fun `manual override is not stored when option is disabled`() = runTest {
        val repository = FakeScheduleRepository(
            fixedSchedule().copy(manualOverrideUntilNextSwitch = false),
        )
        val controller = controller(
            repository,
            Instant.parse("2026-07-15T12:00:00Z").toEpochMilli(),
        )
        controller.start(backgroundScope) {}
        runCurrent()

        controller.recordManualTheme("manual")
        runCurrent()

        assertThat(repository.manual.value).isNull()
    }

    @Test
    fun `disabling automatic or manual retention clears a persisted override`() = runTest {
        val initial = fixedSchedule()
        val repository = FakeScheduleRepository(initial).apply {
            manual.value = ReaderThemeManualOverride("manual", "morning", 123L)
        }
        val controller = controller(
            repository,
            Instant.parse("2026-07-15T12:00:00Z").toEpochMilli(),
        )

        controller.updateSchedule(initial.copy(manualOverrideUntilNextSwitch = false))
        assertThat(repository.manual.value).isNull()

        repository.manual.value = ReaderThemeManualOverride("manual", "morning", 456L)
        controller.updateSchedule(initial.copy(mode = ThemeScheduleMode.OFF))
        assertThat(repository.manual.value).isNull()
    }

    @Test
    fun `stopping controller cancels reader subscriptions and further changes`() = runTest {
        val repository = FakeScheduleRepository(
            ReaderThemeSchedule(
                mode = ThemeScheduleMode.FOLLOW_SYSTEM,
                lightThemeId = "morning",
                darkThemeId = "night",
            ),
        )
        val systemDark = MutableStateFlow(false)
        val controller = controller(repository, 0, systemDark = systemDark)
        val applied = mutableListOf<String?>()
        controller.start(backgroundScope) { applied += it }
        runCurrent()

        controller.stop()
        systemDark.value = true
        runCurrent()

        assertThat(applied).containsExactly("morning")
        assertThat(controller.isRunning).isFalse()
    }

    private fun TestScope.controller(
        repository: FakeScheduleRepository,
        baseEpochMillis: Long,
        systemDark: MutableStateFlow<Boolean> = MutableStateFlow(false),
        timeChanges: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1),
        zoneIdProvider: () -> ZoneId = { ZoneId.of("UTC") },
        delayUntil: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ) = ReaderThemeScheduleController(
        repository = repository,
        themes = MutableStateFlow(themes()),
        systemDark = systemDark,
        timeChanges = timeChanges,
        clock = EpochClock { baseEpochMillis + testScheduler.currentTime },
        zoneIdProvider = zoneIdProvider,
        delayUntil = delayUntil,
    )

    private fun fixedSchedule() = ReaderThemeSchedule(
        mode = ThemeScheduleMode.FIXED_TIME,
        lightThemeId = "morning",
        darkThemeId = "night",
        lightMinuteOfDay = 7 * 60,
        darkMinuteOfDay = 22 * 60,
    )

    private fun themes() = listOf(
        preset("morning"),
        preset("night"),
        preset("manual"),
    )

    private fun preset(id: String) = ReaderThemePreset(
        id = id,
        name = id,
        settings = ReaderSettings(),
        builtIn = false,
        updatedAtEpochMillis = 0,
    )
}

private class FakeScheduleRepository(
    initial: ReaderThemeSchedule,
) : ReaderThemeScheduleRepository {
    val schedule = MutableStateFlow(initial)
    val manual = MutableStateFlow<ReaderThemeManualOverride?>(null)

    override fun observeSchedule(): Flow<ReaderThemeSchedule> = schedule
    override fun observeManualOverride(): Flow<ReaderThemeManualOverride?> = manual

    override suspend fun updateSchedule(schedule: ReaderThemeSchedule) {
        this.schedule.value = schedule
    }

    override suspend fun updateManualOverride(override: ReaderThemeManualOverride?) {
        manual.value = override
    }
}
