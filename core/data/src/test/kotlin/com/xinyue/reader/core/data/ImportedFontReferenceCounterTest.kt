package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.entity.BookReaderOverridesEntity
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.database.entity.ReaderThemeEntity
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImportedFontReferenceCounterTest {
    @Test
    fun `counts global book override and theme references while ignoring corrupt rows`() = runTest {
        val settingsDao = FakeReaderSettingsDao()
        val themeDao = FakeReaderThemeDao()
        val font = ReaderFontRef.Imported("font-1")
        settingsDao.global.value = ReaderGlobalSettingsEntity(
            settingsJson = readerDataJson.encodeToString(ReaderSettings(font = font)),
            scheduleJson = "{}",
            updatedAtEpochMillis = 1,
        )
        settingsDao.upsertBookOverrides(
            BookReaderOverridesEntity(
                "book-1",
                readerDataJson.encodeToString(ReaderSettingsOverrides(font = font)),
                2,
            ),
        )
        settingsDao.upsertBookOverrides(BookReaderOverridesEntity("book-corrupt", "bad-json", 3))
        themeDao.upsert(
            ReaderThemeEntity(
                "theme-1",
                "字体主题",
                readerDataJson.encodeToString(ReaderSettings(font = font)),
                false,
                4,
            ),
        )
        themeDao.upsert(ReaderThemeEntity("theme-corrupt", "损坏主题", "bad-json", false, 5))
        val counter = ImportedFontReferenceCounter(settingsDao, themeDao, readerDataJson)

        assertThat(counter.count("font-1")).isEqualTo(3)
        assertThat(counter.count("font-2")).isEqualTo(0)
    }
}
