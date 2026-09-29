package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.ReaderSettingsDao
import com.xinyue.reader.core.database.entity.BookReaderOverridesEntity
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderFocusBandSettings
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderTextAlignment
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.ThemeScheduleMode
import com.xinyue.reader.core.domain.time.EpochClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomReaderSettingsRepositoryTest {
    @Test
    fun `round trips complete settings and resolves a one field book override`() = runTest {
        val dao = FakeReaderSettingsDao()
        val repository = RoomReaderSettingsRepository(dao, readerDataJson, EpochClock { 50 })
        val global = ReaderSettings(
            font = ReaderFontRef.Imported("font-1"),
            fontWeight = 700,
            fontSizeSp = 24f,
            letterSpacingEm = .04f,
            lineHeightMultiplier = 1.9f,
            paragraphSpacingEm = .8f,
            firstLineIndentEm = 3f,
            alignment = ReaderTextAlignment.JUSTIFY,
            horizontalPaddingDp = 32,
            verticalPaddingDp = 20,
            foregroundArgb = 0xFF123456,
            backgroundArgb = 0xFFFEDCBA,
            warmOverlayArgb = 0xFFAA5500,
            warmOverlayOpacity = .3f,
            focusBand = ReaderFocusBandSettings(true, 4, 0x22010203, .4f),
            keepScreenOn = false,
            showClock = true,
        )

        repository.updateGlobal(global)
        repository.updateBookOverrides("book-1", ReaderSettingsOverrides(fontSizeSp = 29f))

        assertThat(repository.observe(null).first()).isEqualTo(global)
        assertThat(repository.observe("book-1").first()).isEqualTo(global.copy(fontSizeSp = 29f))
        assertThat(repository.observeBookOverrides("book-1").first())
            .isEqualTo(ReaderSettingsOverrides(fontSizeSp = 29f))

        repository.clearBookOverrides("book-1")
        assertThat(repository.observe("book-1").first()).isEqualTo(global)
    }

    @Test
    fun `initializes defaults and corrupt json falls back without throwing`() = runTest {
        val dao = FakeReaderSettingsDao()
        val repository = RoomReaderSettingsRepository(dao, readerDataJson, EpochClock { 10 })

        assertThat(repository.observe(null).first()).isEqualTo(ReaderSettings())
        dao.global.value = dao.global.value!!.copy(settingsJson = "not-json")
        assertThat(repository.observe(null).first()).isEqualTo(ReaderSettings())
    }

    @Test
    fun `schedule update preserves settings and round trips`() = runTest {
        val dao = FakeReaderSettingsDao()
        val settingsRepository = RoomReaderSettingsRepository(dao, readerDataJson, EpochClock { 10 })
        val scheduleRepository = RoomReaderThemeScheduleRepository(dao, readerDataJson, EpochClock { 20 })
        val settings = ReaderSettings(fontSizeSp = 28f)
        val schedule = ReaderThemeSchedule(
            mode = ThemeScheduleMode.FIXED_TIME,
            lightThemeId = "light",
            darkThemeId = "dark",
            lightMinuteOfDay = 300,
            darkMinuteOfDay = 1200,
        )

        settingsRepository.updateGlobal(settings)
        scheduleRepository.updateSchedule(schedule)

        assertThat(scheduleRepository.observeSchedule().first()).isEqualTo(schedule)
        assertThat(settingsRepository.observe(null).first()).isEqualTo(settings)
    }

    @Test
    fun `concurrent settings and schedule writes preserve both payloads`() = runTest {
        val dao = FakeReaderSettingsDao()
        val coordinator = ReaderGlobalSettingsWriteCoordinator()
        val settingsRepository = RoomReaderSettingsRepository(
            dao,
            readerDataJson,
            EpochClock { 10 },
            coordinator,
        )
        val scheduleRepository = RoomReaderThemeScheduleRepository(
            dao,
            readerDataJson,
            EpochClock { 20 },
            coordinator,
        )
        settingsRepository.ensureInitialized()
        dao.upsertDelayMillis = 30
        val settings = ReaderSettings(fontSizeSp = 30f)
        val schedule = ReaderThemeSchedule(mode = ThemeScheduleMode.FOLLOW_SYSTEM)

        val settingsWrite = async { settingsRepository.updateGlobal(settings) }
        val scheduleWrite = async { scheduleRepository.updateSchedule(schedule) }
        settingsWrite.await()
        scheduleWrite.await()

        assertThat(settingsRepository.observe(null).first()).isEqualTo(settings)
        assertThat(scheduleRepository.observeSchedule().first()).isEqualTo(schedule)
    }

    @Test
    fun `update from latest merges inside one write lock without deadlocking`() = runTest {
        val dao = FakeReaderSettingsDao()
        val coordinator = ReaderGlobalSettingsWriteCoordinator()
        val repository = RoomReaderSettingsRepository(dao, readerDataJson, EpochClock { 30 }, coordinator)
        val scheduleRepository = RoomReaderThemeScheduleRepository(dao, readerDataJson, EpochClock { 20 }, coordinator)
        repository.updateGlobal(ReaderSettings(fontSizeSp = 24f, keepScreenOn = true))
        val schedule = ReaderThemeSchedule(mode = ThemeScheduleMode.FOLLOW_SYSTEM)
        scheduleRepository.updateSchedule(schedule)

        repository.updateGlobalFromLatest { latest -> latest.copy(keepScreenOn = false) }

        assertThat(repository.observe(null).first())
            .isEqualTo(ReaderSettings(fontSizeSp = 24f, keepScreenOn = false))
        assertThat(scheduleRepository.observeSchedule().first()).isEqualTo(schedule)
    }
}

internal open class FakeReaderSettingsDao : ReaderSettingsDao {
    val global = MutableStateFlow<ReaderGlobalSettingsEntity?>(null)
    var upsertDelayMillis: Long = 0
    private val overrides = mutableMapOf<String, MutableStateFlow<BookReaderOverridesEntity?>>()

    override fun observeGlobal(): Flow<ReaderGlobalSettingsEntity?> = global
    override suspend fun getGlobal(): ReaderGlobalSettingsEntity? = global.value
    override suspend fun upsertGlobal(entity: ReaderGlobalSettingsEntity) {
        if (upsertDelayMillis > 0) delay(upsertDelayMillis)
        global.value = entity
    }
    override suspend fun insertGlobalIfAbsent(entity: ReaderGlobalSettingsEntity): Long {
        if (global.value != null) return -1
        global.value = entity
        return entity.id.toLong()
    }
    override fun observeBookOverrides(bookId: String): Flow<BookReaderOverridesEntity?> =
        overrides.getOrPut(bookId) { MutableStateFlow(null) }
    override suspend fun getBookOverrides(bookId: String): BookReaderOverridesEntity? =
        overrides[bookId]?.value
    override suspend fun getAllBookOverrides(): List<BookReaderOverridesEntity> =
        overrides.values.mapNotNull { it.value }
    override suspend fun upsertBookOverrides(entity: BookReaderOverridesEntity) {
        overrides.getOrPut(entity.bookId) { MutableStateFlow(null) }.value = entity
    }
    override suspend fun deleteBookOverrides(bookId: String) {
        overrides.getOrPut(bookId) { MutableStateFlow(null) }.value = null
    }
}
