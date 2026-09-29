package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.ReaderThemeDao
import com.xinyue.reader.core.database.entity.ReaderThemeEntity
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_PAPER_ID
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.repository.ReaderThemeRepository
import com.xinyue.reader.core.domain.time.EpochClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class RoomReaderThemeRepository @Inject constructor(
    private val dao: ReaderThemeDao,
    private val json: Json,
    private val clock: EpochClock = EpochClock(System::currentTimeMillis),
) : ReaderThemeRepository {
    override fun observeAll(): Flow<List<ReaderThemePreset>> = dao.observeAll().map { entities ->
        entities.mapNotNull { it.toDomainOrNull() }
    }

    override suspend fun save(preset: ReaderThemePreset) {
        val normalizedName = preset.name.trim()
        require(preset.id.isNotBlank()) { "Theme ID cannot be blank" }
        require(normalizedName.isNotBlank()) { "Theme name cannot be blank" }
        require(normalizedName.length <= MAX_THEME_NAME_LENGTH) { "Theme name is too long" }
        require(dao.findDuplicateName(normalizedName, preset.id) == null) { "Theme name already exists" }
        val existing = dao.get(preset.id)
        require(existing == null || !existing.builtIn || preset.builtIn) { "Built-in theme cannot become custom" }
        dao.upsert(
            ReaderThemeEntity(
                id = preset.id,
                name = normalizedName,
                settingsJson = json.encodeToString(preset.settings.normalized()),
                builtIn = preset.builtIn,
                updatedAtEpochMillis = preset.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun delete(presetId: String) {
        val existing = dao.get(presetId) ?: return
        if (existing.builtIn) return
        dao.deleteCustomWithScheduleFallback(
            id = presetId,
            updatedAtEpochMillis = clock.nowEpochMillis(),
        ) { scheduleJson ->
            scheduleJson?.let {
                val stored = json.decodeOrDefault(it, StoredReaderThemeSchedule())
                val schedule = stored.schedule.copy(
                    lightThemeId = stored.schedule.lightThemeId
                        .takeUnless { id -> id == presetId } ?: BUILT_IN_PAPER_ID,
                    darkThemeId = stored.schedule.darkThemeId
                        .takeUnless { id -> id == presetId } ?: BUILT_IN_DARK_ID,
                )
                val manualOverride = stored.manualOverride?.takeUnless { override ->
                    override.themeId == presetId || override.automaticThemeIdAtActivation == presetId
                }
                json.encodeToString(
                    stored.copy(schedule = schedule, manualOverride = manualOverride),
                )
            }
        }
    }

    private fun ReaderThemeEntity.toDomainOrNull(): ReaderThemePreset? {
        val settings = runCatching { json.decodeFromString<ReaderSettings>(settingsJson).normalized() }
            .getOrNull() ?: return null
        return ReaderThemePreset(id, name, settings, builtIn, updatedAtEpochMillis)
    }

    private companion object {
        const val MAX_THEME_NAME_LENGTH = 100
    }
}
