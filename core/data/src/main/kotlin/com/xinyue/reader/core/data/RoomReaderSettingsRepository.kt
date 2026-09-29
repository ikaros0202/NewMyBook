package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.ReaderSettingsDao
import com.xinyue.reader.core.database.entity.BookReaderOverridesEntity
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.repository.ReaderSettingsRepository
import com.xinyue.reader.core.domain.time.EpochClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class RoomReaderSettingsRepository @Inject constructor(
    private val dao: ReaderSettingsDao,
    private val json: Json,
    private val clock: EpochClock,
    private val startupGate: ReaderSettingsStartupGate,
    private val writeCoordinator: ReaderGlobalSettingsWriteCoordinator,
) : ReaderSettingsRepository {
    internal constructor(
        dao: ReaderSettingsDao,
        json: Json,
        clock: EpochClock,
    ) : this(
        dao,
        json,
        clock,
        ReaderSettingsStartupGate(),
        ReaderGlobalSettingsWriteCoordinator(),
    )

    internal constructor(
        dao: ReaderSettingsDao,
        json: Json,
        clock: EpochClock,
        writeCoordinator: ReaderGlobalSettingsWriteCoordinator,
    ) : this(dao, json, clock, ReaderSettingsStartupGate(), writeCoordinator)

    override fun observe(bookId: String?): Flow<ReaderSettings> = flow {
        prepare()
        val global = dao.observeGlobal().map { it.decodeSettings() }
        if (bookId == null) {
            emitAll(global)
        } else {
            emitAll(
                combine(global, observeBookOverrides(bookId)) { settings, overrides ->
                    settings.resolve(overrides)
                },
            )
        }
    }

    override fun observeBookOverrides(bookId: String): Flow<ReaderSettingsOverrides> =
        dao.observeBookOverrides(bookId).map { entity ->
            entity?.let {
                json.decodeOrDefault(it.overridesJson, ReaderSettingsOverrides())
            } ?: ReaderSettingsOverrides()
        }

    override suspend fun updateGlobal(settings: ReaderSettings) {
        prepare()
        writeCoordinator.write {
            val existing = dao.getGlobal()
            dao.upsertGlobal(
                ReaderGlobalSettingsEntity(
                    settingsJson = json.encodeToString(settings.normalized()),
                    scheduleJson = existing?.scheduleJson
                        ?: json.encodeToString(StoredReaderThemeSchedule()),
                    updatedAtEpochMillis = clock.nowEpochMillis(),
                ),
            )
        }
    }

    override suspend fun updateGlobalFromLatest(transform: (ReaderSettings) -> ReaderSettings) {
        prepare()
        writeCoordinator.write {
            val existing = dao.getGlobal()
            val latest = existing.decodeSettings()
            dao.upsertGlobal(
                ReaderGlobalSettingsEntity(
                    settingsJson = json.encodeToString(transform(latest).normalized()),
                    scheduleJson = existing?.scheduleJson
                        ?: json.encodeToString(StoredReaderThemeSchedule()),
                    updatedAtEpochMillis = clock.nowEpochMillis(),
                ),
            )
        }
    }

    override suspend fun updateBookOverrides(bookId: String, overrides: ReaderSettingsOverrides) {
        require(bookId.isNotBlank()) { "Book ID cannot be blank" }
        prepare()
        dao.upsertBookOverrides(
            BookReaderOverridesEntity(
                bookId = bookId,
                overridesJson = json.encodeToString(overrides),
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }

    override suspend fun clearBookOverrides(bookId: String) {
        dao.deleteBookOverrides(bookId)
    }

    suspend fun ensureInitialized() {
        prepare()
    }

    private suspend fun prepare() {
        startupGate.awaitMigration()
        dao.insertGlobalIfAbsent(defaultEntity())
    }

    private fun defaultEntity() = ReaderGlobalSettingsEntity(
        settingsJson = json.encodeToString(ReaderSettings()),
        scheduleJson = json.encodeToString(StoredReaderThemeSchedule()),
        updatedAtEpochMillis = clock.nowEpochMillis(),
    )

    private fun ReaderGlobalSettingsEntity?.decodeSettings(): ReaderSettings =
        this?.let { json.decodeOrDefault(it.settingsJson, ReaderSettings()).normalized() }
            ?: ReaderSettings()
}

class ReaderSettingsStartupGate private constructor(
    private val migration: suspend () -> Unit,
) {
    @Inject
    constructor(migrator: LegacyReaderSettingsMigrator) : this(migrator::migrateIfNeeded)

    internal constructor() : this({})

    suspend fun awaitMigration() = migration()
}
