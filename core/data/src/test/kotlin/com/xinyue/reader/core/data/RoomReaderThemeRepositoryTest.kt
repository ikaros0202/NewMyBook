package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.ReaderThemeDao
import com.xinyue.reader.core.database.entity.ReaderThemeEntity
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class RoomReaderThemeRepositoryTest {
    @Test
    fun `deleting a custom theme replaces every schedule reference before removal`() = runTest {
        val dao = FakeReaderThemeDao().apply {
            scheduleJson = readerDataJson.encodeToString(
                StoredReaderThemeSchedule(
                    schedule = ReaderThemeSchedule(
                        lightThemeId = "custom",
                        darkThemeId = "custom",
                    ),
                    manualOverride = ReaderThemeManualOverride("custom", "custom"),
                ),
            )
        }
        val repository = RoomReaderThemeRepository(dao, readerDataJson)
        repository.save(ReaderThemePreset("custom", "夜读", ReaderSettings(), false, 2))

        repository.delete("custom")

        val stored = readerDataJson.decodeFromString<StoredReaderThemeSchedule>(dao.scheduleJson)
        assertThat(stored.schedule.lightThemeId).isEqualTo("built-in-paper")
        assertThat(stored.schedule.darkThemeId).isEqualTo("built-in-dark")
        assertThat(stored.manualOverride).isNull()
        assertThat(repository.observeAll().first()).isEmpty()
        assertThat(dao.transactionalDeleteCount).isEqualTo(1)
    }

    @Test
    fun `round trips normalized custom theme and trims its name`() = runTest {
        val dao = FakeReaderThemeDao()
        val repository = RoomReaderThemeRepository(dao, readerDataJson)
        val preset = ReaderThemePreset(
            id = "theme-1",
            name = "  夜读  ",
            settings = ReaderSettings(fontSizeSp = 99f),
            builtIn = false,
            updatedAtEpochMillis = 10,
        )

        repository.save(preset)

        assertThat(repository.observeAll().first()).containsExactly(
            preset.copy(name = "夜读", settings = ReaderSettings(fontSizeSp = 36f)),
        )
    }

    @Test
    fun `rejects blank and duplicate names and does not delete built ins`() = runTest {
        val dao = FakeReaderThemeDao()
        val repository = RoomReaderThemeRepository(dao, readerDataJson)
        repository.save(ReaderThemePreset("builtin", "纸张", ReaderSettings(), true, 1))
        repository.save(ReaderThemePreset("custom", "夜读", ReaderSettings(), false, 2))

        assertFailsWith<IllegalArgumentException> {
            repository.save(ReaderThemePreset("blank", "  ", ReaderSettings(), false, 3))
        }
        assertFailsWith<IllegalArgumentException> {
            repository.save(ReaderThemePreset("duplicate", "夜读", ReaderSettings(), false, 3))
        }
        repository.delete("builtin")
        assertThat(repository.observeAll().first().map(ReaderThemePreset::id))
            .containsExactly("custom", "builtin")
    }
}

internal class FakeReaderThemeDao : ReaderThemeDao {
    private val values = MutableStateFlow<List<ReaderThemeEntity>>(emptyList())
    var scheduleJson: String = readerDataJson.encodeToString(StoredReaderThemeSchedule())
    var transactionalDeleteCount: Int = 0
    override fun observeAll(): Flow<List<ReaderThemeEntity>> = values
    override suspend fun getAll(): List<ReaderThemeEntity> = values.value
    override suspend fun get(id: String): ReaderThemeEntity? = values.value.firstOrNull { it.id == id }
    override suspend fun findDuplicateName(name: String, excludingId: String): ReaderThemeEntity? =
        values.value.firstOrNull { it.id != excludingId && it.name.equals(name, ignoreCase = true) }
    override suspend fun upsert(entity: ReaderThemeEntity) {
        values.value = (values.value.filterNot { it.id == entity.id } + entity)
            .sortedWith(
                compareByDescending<ReaderThemeEntity> { it.builtIn }
                    .thenByDescending { it.updatedAtEpochMillis }
                    .thenBy { it.name }
                    .thenBy { it.id },
            )
    }
    override suspend fun deleteCustom(id: String) {
        values.value = values.value.filterNot { it.id == id && !it.builtIn }
    }

    override suspend fun getScheduleJson(): String = scheduleJson

    override suspend fun updateScheduleJson(scheduleJson: String, updatedAtEpochMillis: Long) {
        this.scheduleJson = scheduleJson
    }

    override suspend fun deleteCustomWithScheduleFallback(
        id: String,
        updatedAtEpochMillis: Long,
        transformSchedule: (String?) -> String?,
    ) {
        transactionalDeleteCount += 1
        super.deleteCustomWithScheduleFallback(id, updatedAtEpochMillis, transformSchedule)
    }
}
