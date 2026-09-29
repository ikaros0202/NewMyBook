package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.ReaderSettingsDao
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.domain.time.EpochClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@Singleton
class LegacyReaderSettingsMigrator @Inject constructor(
    private val reader: LegacyReaderSettingsReader,
    private val dao: ReaderSettingsDao,
    private val json: Json,
    private val clock: EpochClock,
) {
    private val mutex = Mutex()

    suspend fun migrateIfNeeded() = mutex.withLock {
        if (reader.isMigrationComplete()) return@withLock
        dao.insertGlobalIfAbsent(
            ReaderGlobalSettingsEntity(
                settingsJson = json.encodeToString(reader.readSettings()),
                scheduleJson = json.encodeToString(StoredReaderThemeSchedule()),
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
        reader.markMigrationComplete()
    }
}
