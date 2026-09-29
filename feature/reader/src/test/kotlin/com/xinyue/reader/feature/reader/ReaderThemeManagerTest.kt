package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.graphics.toArgb
import com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_GREEN_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_OLED_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_PAPER_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_SEPIA_ID
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderTapAction
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.repository.ReaderThemeRepository
import com.xinyue.reader.core.domain.time.EpochClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class ReaderThemeManagerTest {
    @Test
    fun `built in palettes are one canonical set and paper matches the settings default`() {
        val expected = listOf(
            ReaderColorTheme.PAPER to (0xFF2B2926L to 0xFFF6F1E7L),
            ReaderColorTheme.SEPIA to (0xFF40362BL to 0xFFF4E8CEL),
            ReaderColorTheme.GREEN to (0xFF25352BL to 0xFFDDEBD9L),
            ReaderColorTheme.DARK to (0xFFE5E1DCL to 0xFF252423L),
            ReaderColorTheme.OLED_BLACK to (0xFFE6E6E6L to 0xFF000000L),
        )

        assertThat(ReaderThemeManager.BUILT_IN_THEMES.map { theme ->
            theme.settings.colorTheme to (theme.settings.foregroundArgb to theme.settings.backgroundArgb)
        }).containsExactlyElementsIn(expected).inOrder()
        assertThat(ReaderSettings().matchesBuiltInPalette(ReaderColorTheme.PAPER)).isTrue()
        assertThat(manager().activeThemeId(ReaderSettings(), ReaderThemeManager.BUILT_IN_THEMES))
            .isEqualTo(BUILT_IN_PAPER_ID)
    }

    @Test
    fun `theme preview converts persisted long as 32 bit ARGB`() {
        assertThat(readerThemePreviewColor(0xFF010203).toArgb()).isEqualTo(0xFF010203.toInt())
    }

    @Test
    fun `exposes five immutable built ins in stable order`() = runTest {
        val manager = manager()

        assertThat(manager.observeThemes().first().map(ReaderThemePreset::id)).containsExactly(
            BUILT_IN_PAPER_ID,
            BUILT_IN_SEPIA_ID,
            BUILT_IN_GREEN_ID,
            BUILT_IN_DARK_ID,
            BUILT_IN_OLED_ID,
        ).inOrder()
        assertFailsWith<IllegalArgumentException> {
            manager.rename(BUILT_IN_PAPER_ID, "新名字")
        }
        assertFailsWith<IllegalArgumentException> {
            manager.update(BUILT_IN_DARK_ID, ReaderSettings(fontSizeSp = 30f))
        }
        assertFailsWith<IllegalArgumentException> {
            manager.delete(BUILT_IN_OLED_ID)
        }
    }

    @Test
    fun `normalizes names rejects duplicates and accepts exactly one hundred characters`() = runTest {
        val manager = manager(ids = ArrayDeque(listOf("one", "two", "three")))

        assertFailsWith<IllegalArgumentException> { manager.create("  ", ReaderSettings()) }
        val first = manager.create("  夜读  ", ReaderSettings(fontSizeSp = 99f))
        assertThat(first.name).isEqualTo("夜读")
        assertThat(first.settings.fontSizeSp).isEqualTo(36f)
        assertFailsWith<IllegalArgumentException> { manager.create("夜读", ReaderSettings()) }
        assertFailsWith<IllegalArgumentException> { manager.create("夜读".uppercase(), ReaderSettings()) }

        val oneHundred = "甲".repeat(100)
        assertThat(manager.create(oneHundred, ReaderSettings()).name).hasLength(100)
        assertFailsWith<IllegalArgumentException> {
            manager.create("乙".repeat(101), ReaderSettings())
        }
    }

    @Test
    fun `supports create copy rename update delete and deterministic custom sorting`() = runTest {
        var now = 10L
        val manager = manager(
            ids = ArrayDeque(listOf("z-id", "a-id", "copy-id")),
            clock = EpochClock { now++ },
        )
        val older = manager.create("同名排序乙", ReaderSettings(fontSizeSp = 18f))
        val newer = manager.create("同名排序甲", ReaderSettings(fontSizeSp = 22f))
        val copied = manager.copy(older.id, "副本")
        manager.rename(copied.id, "改名副本")
        manager.update(copied.id, ReaderSettings(fontSizeSp = 30f))

        val themes = manager.observeThemes().first()
        val customs = themes.filterNot(ReaderThemePreset::builtIn)
        assertThat(customs.map(ReaderThemePreset::id))
            .containsExactly(copied.id, newer.id, older.id)
            .inOrder()
        assertThat(customs.first().name).isEqualTo("改名副本")
        assertThat(customs.first().settings.fontSizeSp).isEqualTo(30f)

        manager.delete(newer.id)
        assertThat(manager.observeThemes().first().map(ReaderThemePreset::id))
            .doesNotContain(newer.id)
    }

    @Test
    fun `applying then deleting a custom theme keeps materialized appearance and interaction settings`() = runTest {
        val manager = manager(ids = ArrayDeque(listOf("custom")))
        val theme = manager.create(
            "沉浸夜读",
            ReaderSettings(fontSizeSp = 28f, backgroundArgb = 0xFF010203),
        )
        val current = ReaderSettings(
            pageAnimation = ReaderPageAnimation.NONE,
            tapZoneActions = List(ReaderSettings.TAP_ZONE_COUNT) { ReaderTapAction.MENU },
        )

        val applied = current.applyAppearanceFrom(theme.settings)
        manager.delete(theme.id)

        assertThat(applied.fontSizeSp).isEqualTo(28f)
        assertThat(applied.backgroundArgb).isEqualTo(0xFF010203)
        assertThat(applied.pageAnimation).isEqualTo(ReaderPageAnimation.NONE)
        assertThat(applied.tapZoneActions).containsExactlyElementsIn(current.tapZoneActions).inOrder()
        assertThat(manager.observeThemes().first().map(ReaderThemePreset::id)).doesNotContain(theme.id)
    }

    private fun manager(
        ids: ArrayDeque<String> = ArrayDeque(),
        clock: EpochClock = EpochClock { 1L },
    ): ReaderThemeManager = ReaderThemeManager(
        repository = FakeThemeRepository(),
        clock = clock,
        idFactory = { ids.removeFirstOrNull() ?: "generated-id" },
    )
}

private class FakeThemeRepository : ReaderThemeRepository {
    private val themes = MutableStateFlow<List<ReaderThemePreset>>(emptyList())

    override fun observeAll(): Flow<List<ReaderThemePreset>> = themes

    override suspend fun save(preset: ReaderThemePreset) {
        themes.value = themes.value.filterNot { it.id == preset.id } + preset
    }

    override suspend fun delete(presetId: String) {
        themes.value = themes.value.filterNot { it.id == presetId }
    }
}
