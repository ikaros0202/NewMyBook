package com.xinyue.reader.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class LegacyReaderSettingsMigratorTest {
    @Test
    fun `migrates legacy values exactly once including fade compatibility`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reader = LegacyReaderSettingsReader(context).also { it.clearForTest() }
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE).edit()
            .putFloat("font_size_sp", 26f)
            .putString("font_family", "SERIF")
            .putString("page_animation", "FADE")
            .putBoolean("show_clock", true)
            .apply()
        val dao = FakeReaderSettingsDao()
        val migrator = LegacyReaderSettingsMigrator(reader, dao, readerDataJson, EpochClockForTest)

        migrator.migrateIfNeeded()
        val migrated = readerDataJson.decodeFromString<ReaderSettings>(dao.global.value!!.settingsJson)
        assertThat(migrated.fontSizeSp).isEqualTo(26f)
        assertThat(migrated.font).isEqualTo(ReaderFontRef.Serif)
        assertThat(migrated.pageAnimation).isEqualTo(ReaderPageAnimation.COVER)
        assertThat(migrated.showClock).isTrue()

        dao.global.value = dao.global.value!!.copy(
            settingsJson = readerDataJson.encodeToString(ReaderSettings(fontSizeSp = 31f)),
        )
        migrator.migrateIfNeeded()
        assertThat(readerDataJson.decodeFromString<ReaderSettings>(dao.global.value!!.settingsJson).fontSizeSp)
            .isEqualTo(31f)
    }

    @Test
    fun `corrupt legacy enum and tap zones use safe defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reader = LegacyReaderSettingsReader(context).also { it.clearForTest() }
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE).edit()
            .putString("font_family", "CORRUPT")
            .putString("page_animation", "CORRUPT")
            .putString("tap_zone_actions", "NONE,BROKEN")
            .apply()

        assertThat(reader.readSettings()).isEqualTo(ReaderSettings())
    }

    @Test
    fun `completion marker is written only after room insert succeeds`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reader = LegacyReaderSettingsReader(context).also { it.clearForTest() }
        val dao = object : FakeReaderSettingsDao() {
            override suspend fun insertGlobalIfAbsent(entity: com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity): Long {
                error("simulated transaction failure")
            }
        }
        val migrator = LegacyReaderSettingsMigrator(reader, dao, readerDataJson, EpochClockForTest)

        assertFailsWith<IllegalStateException> { migrator.migrateIfNeeded() }
        assertThat(reader.isMigrationComplete()).isFalse()
    }

    private object EpochClockForTest : com.xinyue.reader.core.domain.time.EpochClock {
        override fun nowEpochMillis(): Long = 100
    }
}
