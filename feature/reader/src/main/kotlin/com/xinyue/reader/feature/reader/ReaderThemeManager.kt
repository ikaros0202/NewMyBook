package com.xinyue.reader.feature.reader

import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.repository.ReaderThemeRepository
import com.xinyue.reader.core.domain.time.EpochClock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ReaderThemeManager @Inject constructor(
    private val repository: ReaderThemeRepository,
    private val clock: EpochClock,
) {
    private var idFactory: () -> String = { UUID.randomUUID().toString() }

    internal constructor(
        repository: ReaderThemeRepository,
        clock: EpochClock,
        idFactory: () -> String,
    ) : this(repository, clock) {
        this.idFactory = idFactory
    }

    fun observeThemes(): Flow<List<ReaderThemePreset>> = repository.observeAll().map(::mergeAndSort)

    suspend fun create(name: String, settings: ReaderSettings): ReaderThemePreset {
        val normalizedName = validateAvailableName(name)
        val id = idFactory().also { candidate ->
            require(candidate.isNotBlank()) { "主题 ID 不能为空" }
            require(currentThemes().none { it.id == candidate }) { "主题 ID 已存在" }
        }
        val preset = ReaderThemePreset(
            id = id,
            name = normalizedName,
            settings = settings.normalized(),
            builtIn = false,
            updatedAtEpochMillis = clock.nowEpochMillis(),
        )
        repository.save(preset)
        return preset
    }

    suspend fun copy(sourceId: String, name: String): ReaderThemePreset {
        val source = find(sourceId)
        return create(name, source.settings)
    }

    suspend fun rename(themeId: String, name: String): ReaderThemePreset {
        val existing = requireCustom(themeId)
        val normalizedName = validateAvailableName(name, excludingId = themeId)
        return existing.copy(
            name = normalizedName,
            updatedAtEpochMillis = clock.nowEpochMillis(),
        ).also { repository.save(it) }
    }

    suspend fun update(themeId: String, settings: ReaderSettings): ReaderThemePreset {
        val existing = requireCustom(themeId)
        return existing.copy(
            settings = settings.normalized(),
            updatedAtEpochMillis = clock.nowEpochMillis(),
        ).also { repository.save(it) }
    }

    suspend fun delete(themeId: String) {
        requireCustom(themeId)
        repository.delete(themeId)
    }

    suspend fun find(themeId: String): ReaderThemePreset = currentThemes()
        .firstOrNull { it.id == themeId }
        ?: throw IllegalArgumentException("主题不存在")

    fun activeThemeId(settings: ReaderSettings, themes: List<ReaderThemePreset>): String? {
        val appearance = ReaderSettings().applyAppearanceFrom(settings)
        return themes.firstOrNull { ReaderSettings().applyAppearanceFrom(it.settings) == appearance }?.id
    }

    private suspend fun requireCustom(themeId: String): ReaderThemePreset {
        val theme = find(themeId)
        require(!theme.builtIn) { "内置主题不可修改或删除" }
        return theme
    }

    private suspend fun validateAvailableName(name: String, excludingId: String? = null): String {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "主题名称不能为空" }
        require(normalized.length <= MAX_THEME_NAME_LENGTH) { "主题名称不能超过 100 个字符" }
        require(
            currentThemes().none {
                it.id != excludingId && it.name.equals(normalized, ignoreCase = true)
            },
        ) { "主题名称已存在" }
        return normalized
    }

    private suspend fun currentThemes(): List<ReaderThemePreset> =
        mergeAndSort(repository.observeAll().first())

    private fun mergeAndSort(stored: List<ReaderThemePreset>): List<ReaderThemePreset> {
        val reservedIds = BUILT_IN_THEMES.mapTo(mutableSetOf(), ReaderThemePreset::id)
        val custom = stored.filter { !it.builtIn && it.id !in reservedIds }
            .sortedWith(
                compareByDescending<ReaderThemePreset> { it.updatedAtEpochMillis }
                    .thenBy { it.name }
                    .thenBy { it.id },
            )
        return BUILT_IN_THEMES + custom
    }

    companion object {
        const val MAX_THEME_NAME_LENGTH = 100

        val BUILT_IN_THEMES: List<ReaderThemePreset> = READER_BUILT_IN_PALETTES.map { palette ->
            ReaderThemePreset(
                id = palette.id,
                name = palette.name,
                settings = ReaderSettings(
                    colorTheme = palette.theme,
                    foregroundArgb = palette.foregroundArgb,
                    backgroundArgb = palette.backgroundArgb,
                ),
                builtIn = true,
                updatedAtEpochMillis = 0,
            )
        }
    }
}

/** Applies only visual appearance and preserves interaction, brightness and information-bar choices. */
fun ReaderSettings.applyAppearanceFrom(theme: ReaderSettings): ReaderSettings = copy(
    font = theme.font,
    fontWeight = theme.fontWeight,
    fontSizeSp = theme.fontSizeSp,
    letterSpacingEm = theme.letterSpacingEm,
    lineHeightMultiplier = theme.lineHeightMultiplier,
    paragraphSpacingEm = theme.paragraphSpacingEm,
    firstLineIndentEm = theme.firstLineIndentEm,
    alignment = theme.alignment,
    horizontalPaddingDp = theme.horizontalPaddingDp,
    verticalPaddingDp = theme.verticalPaddingDp,
    foregroundArgb = theme.foregroundArgb,
    backgroundArgb = theme.backgroundArgb,
    warmOverlayArgb = theme.warmOverlayArgb,
    warmOverlayOpacity = theme.warmOverlayOpacity,
    focusBand = theme.focusBand,
    colorTheme = theme.colorTheme,
).normalized()

internal object EmptyReaderThemeRepository : ReaderThemeRepository {
    override fun observeAll(): Flow<List<ReaderThemePreset>> = flowOf(emptyList())
    override suspend fun save(preset: ReaderThemePreset) = Unit
    override suspend fun delete(presetId: String) = Unit
}
