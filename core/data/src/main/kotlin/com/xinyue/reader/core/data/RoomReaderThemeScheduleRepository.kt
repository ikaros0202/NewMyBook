package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.ReaderSettingsDao
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository
import com.xinyue.reader.core.domain.time.EpochClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class RoomReaderThemeScheduleRepository @Inject constructor(
    private val dao: ReaderSettingsDao,
    private val json: Json,
    private val clock: EpochClock,
    private val writeCoordinator: ReaderGlobalSettingsWriteCoordinator,
) : ReaderThemeScheduleRepository {
    internal constructor(
        dao: ReaderSettingsDao,
        json: Json,
        clock: EpochClock,
    ) : this(dao, json, clock, ReaderGlobalSettingsWriteCoordinator())

    override fun observeSchedule(): Flow<ReaderThemeSchedule> = flow {
        ensureInitialized()
        emitAll(dao.observeGlobal().map { it.decodeSchedule().schedule })
    }

    override fun observeManualOverride(): Flow<ReaderThemeManualOverride?> = flow {
        ensureInitialized()
        emitAll(dao.observeGlobal().map { it.decodeSchedule().manualOverride })
    }

    override suspend fun updateSchedule(schedule: ReaderThemeSchedule) {
        updateStored { it.copy(schedule = schedule) }
    }

    override suspend fun updateManualOverride(override: ReaderThemeManualOverride?) {
        updateStored { it.copy(manualOverride = override) }
    }

    private suspend fun updateStored(transform: (StoredReaderThemeSchedule) -> StoredReaderThemeSchedule) {
        ensureInitialized()
        writeCoordinator.write {
            val existing = requireNotNull(dao.getGlobal())
            dao.upsertGlobal(
                existing.copy(
                    scheduleJson = json.encodeToString(transform(existing.decodeSchedule())),
                    updatedAtEpochMillis = clock.nowEpochMillis(),
                ),
            )
        }
    }

    private suspend fun ensureInitialized() {
        dao.insertGlobalIfAbsent(
            ReaderGlobalSettingsEntity(
                settingsJson = json.encodeToString(ReaderSettings()),
                scheduleJson = json.encodeToString(StoredReaderThemeSchedule()),
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }

    private fun ReaderGlobalSettingsEntity?.decodeSchedule(): StoredReaderThemeSchedule =
        this?.let { json.decodeOrDefault(it.scheduleJson, StoredReaderThemeSchedule()) }
            ?: StoredReaderThemeSchedule()
}
