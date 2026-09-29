package com.xinyue.reader.core.database

import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room3.testing.MigrationTestHelper
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XinYueDatabaseMigrationTest {
    @Test
    fun version11MigratesSingleGroupsToCollectionsAndAddsSeriesMetadata() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseFile = File(context.cacheDir, "xinyue-migration-11-to-12.db")
        databaseFile.delete()
        val helper = MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = databaseFile,
            driver = BundledSQLiteDriver(),
            databaseClass = XinYueDatabase::class,
        )
        helper.createDatabase(11).use { connection ->
            connection.execSQL(
                "INSERT INTO book_groups VALUES ('collection-1', '旧分组', 0, 10, 10)",
            )
            connection.execSQL(
                """
                INSERT INTO books (
                    id, title, author, originalFileName, originalPath, normalizedPath,
                    charsetName, contentSha256, contentLength, createdAtEpochMillis,
                    lastOpenedAtEpochMillis, groupId, customCoverPath, finished
                ) VALUES (
                    'book-11', '迁移书籍', '作者', 'old.txt',
                    'books/book-11/original.txt', 'books/book-11/content.txt',
                    'UTF-8', 'hash-11', 200, 10, 20, 'collection-1', NULL, 0
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(12, listOf(MIGRATION_11_12)).use { connection ->
            assertSingleValue(connection, "SELECT title FROM books WHERE id = 'book-11'", "迁移书籍")
            assertSingleLong(connection, "SELECT seriesName IS NULL FROM books WHERE id = 'book-11'", 1)
            assertSingleLong(connection, "SELECT seriesOrder IS NULL FROM books WHERE id = 'book-11'", 1)
            assertSingleValue(
                connection,
                "SELECT groupId FROM book_group_memberships WHERE bookId = 'book-11'",
                "collection-1",
            )
        }
        databaseFile.delete()
    }

    @Test
    fun version10AddsLibraryAndStatisticsDataWithoutLosingExistingValues() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseFile = File(context.cacheDir, "xinyue-migration-10-to-11.db")
        databaseFile.delete()
        val helper = MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = databaseFile,
            driver = BundledSQLiteDriver(),
            databaseClass = XinYueDatabase::class,
        )
        helper.createDatabase(10).use { connection ->
            connection.execSQL(
                """
                INSERT INTO books (
                    id, title, author, originalFileName, originalPath, normalizedPath,
                    charsetName, contentSha256, contentLength, createdAtEpochMillis,
                    lastOpenedAtEpochMillis
                ) VALUES (
                    'book-10', '迁移保留书', '测试作者', 'fixture.txt',
                    'books/book-10/original.txt', 'books/book-10/content.txt',
                    'GB18030', 'hash-10', 321, 10, 20
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO reading_progress (
                    bookId, offset, contextHash, prefix, suffix, contentLength, updatedAtEpochMillis
                ) VALUES ('book-10', 88, 'context', 'pre', 'suf', 321, 30)
                """.trimIndent(),
            )
            connection.execSQL(
                "INSERT INTO annotations VALUES " +
                    "('note-10', 'book-10', 'NOTE', 40, 45, 'p', 's', 'selected-hash', '#FFF59D', " +
                    "'测试批注', 31, 32)",
            )
            connection.execSQL(
                "INSERT INTO reader_themes VALUES ('theme-10', '测试主题', '{\"fontSizeSp\":24}', 0, 33)",
            )
            connection.execSQL(
                "INSERT INTO imported_fonts VALUES " +
                    "('font-10', '测试字体', 'fonts/font-10/font.ttf', 'font-hash-10', 2048, 34)",
            )
        }

        helper.runMigrationsAndValidate(11, listOf(MIGRATION_10_11)).use { connection ->
            assertSingleValue(connection, "SELECT title FROM books WHERE id = 'book-10'", "迁移保留书")
            assertSingleValue(connection, "SELECT author FROM books WHERE id = 'book-10'", "测试作者")
            assertSingleValue(connection, "SELECT charsetName FROM books WHERE id = 'book-10'", "GB18030")
            assertSingleLong(connection, "SELECT offset FROM reading_progress WHERE bookId = 'book-10'", 88)
            assertSingleValue(connection, "SELECT prefix FROM reading_progress WHERE bookId = 'book-10'", "pre")
            assertSingleValue(connection, "SELECT note FROM annotations WHERE id = 'note-10'", "测试批注")
            assertSingleValue(connection, "SELECT name FROM reader_themes WHERE id = 'theme-10'", "测试主题")
            assertSingleValue(connection, "SELECT displayName FROM imported_fonts WHERE id = 'font-10'", "测试字体")
            assertSingleLong(connection, "SELECT groupId IS NULL FROM books WHERE id = 'book-10'", 1)
            assertSingleLong(connection, "SELECT customCoverPath IS NULL FROM books WHERE id = 'book-10'", 1)
            assertSingleLong(connection, "SELECT finished FROM books WHERE id = 'book-10'", 0)
            for (table in listOf("book_groups", "reading_sessions", "reading_daily_stats")) {
                assertSingleLong(connection, "SELECT COUNT(*) FROM `$table`", 0)
            }
        }
        databaseFile.delete()
    }

    @Test
    fun version9AddsReaderPersonalizationTablesWithoutLosingReadingData() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseFile = File(context.cacheDir, "xinyue-migration-9-to-10.db")
        databaseFile.delete()
        val helper = MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = databaseFile,
            driver = BundledSQLiteDriver(),
            databaseClass = XinYueDatabase::class,
        )
        helper.createDatabase(9).use { connection ->
            connection.execSQL(
                """
                INSERT INTO books (
                    id, title, author, originalFileName, originalPath, normalizedPath,
                    charsetName, contentSha256, contentLength, createdAtEpochMillis,
                    lastOpenedAtEpochMillis
                ) VALUES (
                    'book-9', '迁移保留小说', NULL, 'old.txt',
                    'books/book-9/original.txt', 'books/book-9/content.txt',
                    'UTF-8', 'hash-9', 200, 10, 20
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "INSERT INTO reading_progress VALUES ('book-9', 88, 'legacy', 200, 30, 'pre', 'suf')",
            )
            connection.execSQL(
                "INSERT INTO annotations VALUES " +
                    "('note-9', 'book-9', 'NOTE', 40, 45, 'p', 's', NULL, NULL, '保留批注', 31, 32)",
            )
        }

        helper.runMigrationsAndValidate(10, listOf(MIGRATION_9_10)).use { connection ->
            assertSingleValue(connection, "SELECT title FROM books WHERE id = 'book-9'", "迁移保留小说")
            assertSingleLong(connection, "SELECT offset FROM reading_progress WHERE bookId = 'book-9'", 88)
            assertSingleValue(connection, "SELECT note FROM annotations WHERE id = 'note-9'", "保留批注")
            for (table in listOf(
                "reader_global_settings",
                "book_reader_overrides",
                "reader_themes",
                "imported_fonts",
            )) {
                assertSingleLong(connection, "SELECT COUNT(*) FROM `$table`", 0)
            }
        }
        databaseFile.delete()
    }

    @Test
    fun version8MigratesProgressToRepairableFingerprint() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseFile = File(context.cacheDir, "xinyue-migration-8-to-9.db")
        databaseFile.delete()
        val helper = MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = databaseFile,
            driver = BundledSQLiteDriver(),
            databaseClass = XinYueDatabase::class,
        )
        helper.createDatabase(8).use { connection ->
            connection.execSQL(
                """
                INSERT INTO books (
                    id, title, author, originalFileName, originalPath, normalizedPath,
                    charsetName, contentSha256, contentLength, createdAtEpochMillis,
                    lastOpenedAtEpochMillis
                ) VALUES (
                    'book-8', '迁移小说', NULL, 'old.txt',
                    'books/book-8/original.txt', 'books/book-8/content.txt',
                    'UTF-8', 'hash-8', 200, 10, 20
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "INSERT INTO reading_progress VALUES ('book-8', 88, 'legacy', 200, 30)",
            )
        }

        helper.runMigrationsAndValidate(9, listOf(MIGRATION_8_9)).use { connection ->
            assertSingleLong(connection, "SELECT offset FROM reading_progress WHERE bookId = 'book-8'", 88)
            assertSingleValue(connection, "SELECT prefix FROM reading_progress WHERE bookId = 'book-8'", "")
            assertSingleValue(connection, "SELECT suffix FROM reading_progress WHERE bookId = 'book-8'", "")
        }
        databaseFile.delete()
    }

    @Test
    fun version7MigratesBookmarksToUnifiedAnnotations() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseFile = File(context.cacheDir, "xinyue-migration-7-to-8.db")
        databaseFile.delete()
        val helper = MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = databaseFile,
            driver = BundledSQLiteDriver(),
            databaseClass = XinYueDatabase::class,
        )
        helper.createDatabase(7).use { connection ->
            connection.execSQL(
                """
                INSERT INTO books (
                    id, title, author, originalFileName, originalPath, normalizedPath,
                    charsetName, contentSha256, contentLength, createdAtEpochMillis,
                    lastOpenedAtEpochMillis
                ) VALUES (
                    'book-7', '迁移小说', NULL, 'old.txt',
                    'books/book-7/original.txt', 'books/book-7/content.txt',
                    'UTF-8', 'hash-7', 200, 10, 20
                )
                """.trimIndent(),
            )
            connection.execSQL("INSERT INTO bookmarks VALUES ('mark-1', 'book-7', 66, '旧批注', 40)")
            connection.execSQL("INSERT INTO bookmarks VALUES ('mark-2', 'book-7', 99, NULL, 50)")
            connection.execSQL("INSERT INTO reading_progress VALUES ('book-7', 88, 'context', 200, 30)")
        }

        helper.runMigrationsAndValidate(8, listOf(MIGRATION_7_8)).use { connection ->
            assertSingleLong(connection, "SELECT COUNT(*) FROM annotations", 2)
            assertSingleValue(connection, "SELECT kind FROM annotations WHERE id = 'mark-1'", "BOOKMARK")
            assertSingleLong(connection, "SELECT startOffset FROM annotations WHERE id = 'mark-1'", 66)
            assertSingleLong(connection, "SELECT endOffset FROM annotations WHERE id = 'mark-1'", 66)
            assertSingleValue(connection, "SELECT note FROM annotations WHERE id = 'mark-1'", "旧批注")
            assertSingleLong(connection, "SELECT updatedAtEpochMillis FROM annotations WHERE id = 'mark-2'", 50)
            assertSingleLong(connection, "SELECT offset FROM reading_progress WHERE bookId = 'book-7'", 88)
        }
        databaseFile.delete()
    }

    @Test
    fun version6MigratesToVersion7WithEmptySearchIndex() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseFile = File(context.cacheDir, "xinyue-migration-6-to-7.db")
        databaseFile.delete()
        val helper = MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = databaseFile,
            driver = BundledSQLiteDriver(),
            databaseClass = XinYueDatabase::class,
        )
        helper.createDatabase(6).use { connection ->
            connection.execSQL(
                """
                INSERT INTO books (
                    id, title, author, originalFileName, originalPath, normalizedPath,
                    charsetName, contentSha256, contentLength, createdAtEpochMillis,
                    lastOpenedAtEpochMillis
                ) VALUES (
                    'book-6', '保留小说', NULL, 'old.txt',
                    'books/book-6/original.txt', 'books/book-6/content.txt',
                    'UTF-8', 'hash-6', 200, 10, 20
                )
                """.trimIndent(),
            )
            connection.execSQL("INSERT INTO chapters VALUES ('book-6', 0, '第一章')")
            connection.execSQL("INSERT INTO chapter_configs VALUES ('book-6', 'BROAD', 1, 30)")
        }

        helper.runMigrationsAndValidate(7).use { connection ->
            assertSingleValue(connection, "SELECT title FROM books WHERE id = 'book-6'", "保留小说")
            assertSingleValue(connection, "SELECT ruleSet FROM chapter_configs WHERE bookId = 'book-6'", "BROAD")
            assertSingleLong(connection, "SELECT COUNT(*) FROM search_chunks", 0)
            assertSingleLong(connection, "SELECT COUNT(*) FROM search_index_states", 0)
        }
        databaseFile.delete()
    }

    @Test
    fun version5MigratesToVersion6WithoutLosingReadingData() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseFile = File(context.cacheDir, "xinyue-migration-5-to-6.db")
        databaseFile.delete()
        val helper = MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = databaseFile,
            driver = BundledSQLiteDriver(),
            databaseClass = XinYueDatabase::class,
        )

        helper.createDatabase(5).use { connection ->
            connection.execSQL(
                """
                INSERT INTO books (
                    id, title, author, originalFileName, originalPath, normalizedPath,
                    charsetName, contentSha256, contentLength, createdAtEpochMillis,
                    lastOpenedAtEpochMillis
                ) VALUES (
                    'book-5', '旧版小说', NULL, 'old.txt',
                    'books/book-5/original.txt', 'books/book-5/content.txt',
                    'UTF-8', 'hash-5', 200, 10, 20
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "INSERT INTO reading_progress VALUES ('book-5', 88, 'context', 200, 30)",
            )
            connection.execSQL(
                "INSERT INTO bookmarks VALUES ('mark-5', 'book-5', 66, '重要段落', 40)",
            )
            connection.execSQL(
                "INSERT INTO chapters VALUES ('book-5', 50, '第二章')",
            )
        }

        helper.runMigrationsAndValidate(6).use { connection ->
            assertSingleValue(connection, "SELECT title FROM books WHERE id = 'book-5'", "旧版小说")
            assertSingleLong(connection, "SELECT offset FROM reading_progress WHERE bookId = 'book-5'", 88)
            assertSingleValue(connection, "SELECT note FROM bookmarks WHERE id = 'mark-5'", "重要段落")
            assertSingleValue(connection, "SELECT title FROM chapters WHERE bookId = 'book-5'", "第二章")
            assertSingleLong(connection, "SELECT COUNT(*) FROM chapter_configs", 0)
        }
        databaseFile.delete()
    }

    @Test
    fun everyExportedSchemaMigratesToVersion12WithoutLosingBooks() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (startVersion in 1..11) {
            val databaseFile = File(context.cacheDir, "xinyue-migration-$startVersion.db")
            databaseFile.delete()
            val helper = MigrationTestHelper(
                instrumentation = InstrumentationRegistry.getInstrumentation(),
                file = databaseFile,
                driver = BundledSQLiteDriver(),
                databaseClass = XinYueDatabase::class,
            )
            val bookId = "book-$startVersion"
            helper.createDatabase(startVersion).use { connection ->
                val versionSpecificColumns = if (startVersion >= 11) ", finished" else ""
                val versionSpecificValues = if (startVersion >= 11) ", 0" else ""
                connection.execSQL(
                    """
                    INSERT INTO books (
                        id, title, author, originalFileName, originalPath, normalizedPath,
                        charsetName, contentSha256, contentLength, createdAtEpochMillis,
                        lastOpenedAtEpochMillis$versionSpecificColumns
                    ) VALUES (
                        '$bookId', '旧版小说$startVersion', NULL, 'old.txt',
                        'books/$bookId/original.txt', 'books/$bookId/content.txt',
                        'UTF-8', 'hash-$startVersion', 200, 10, NULL$versionSpecificValues
                    )
                    """.trimIndent(),
                )
            }

            helper.runMigrationsAndValidate(
                12,
                listOf(
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                ),
            ).use { connection ->
                connection.prepare("SELECT title FROM books WHERE id = '$bookId'").use { statement ->
                    assertThat(statement.step()).isTrue()
                    assertThat(statement.getText(0)).isEqualTo("旧版小说$startVersion")
                }
                connection.prepare("SELECT COUNT(*) FROM pending_file_cleanup").use { statement ->
                    assertThat(statement.step()).isTrue()
                    assertThat(statement.getLong(0)).isEqualTo(0)
                }
                connection.prepare("SELECT COUNT(*) FROM chapters").use { statement ->
                    assertThat(statement.step()).isTrue()
                    assertThat(statement.getLong(0)).isEqualTo(0)
                }
                connection.prepare("SELECT COUNT(*) FROM chapter_configs").use { statement ->
                    assertThat(statement.step()).isTrue()
                    assertThat(statement.getLong(0)).isEqualTo(0)
                }
                connection.prepare("SELECT COUNT(*) FROM search_chunks").use { statement ->
                    assertThat(statement.step()).isTrue()
                    assertThat(statement.getLong(0)).isEqualTo(0)
                }
                connection.prepare("SELECT COUNT(*) FROM search_index_states").use { statement ->
                    assertThat(statement.step()).isTrue()
                    assertThat(statement.getLong(0)).isEqualTo(0)
                }
                connection.prepare("SELECT COUNT(*) FROM annotations").use { statement ->
                    assertThat(statement.step()).isTrue()
                    assertThat(statement.getLong(0)).isEqualTo(0)
                }
                connection.prepare("SELECT prefix, suffix FROM reading_progress LIMIT 1").use { statement ->
                    if (statement.step()) {
                        assertThat(statement.getText(0)).isEmpty()
                        assertThat(statement.getText(1)).isEmpty()
                    }
                }
                for (table in listOf(
                    "reader_global_settings",
                    "book_reader_overrides",
                    "reader_themes",
                    "imported_fonts",
                    "book_groups",
                    "book_group_memberships",
                    "reading_sessions",
                    "reading_daily_stats",
                )) {
                    assertSingleLong(connection, "SELECT COUNT(*) FROM `$table`", 0)
                }
                assertSingleLong(connection, "SELECT seriesName IS NULL FROM books WHERE id = '$bookId'", 1)
                assertSingleLong(connection, "SELECT seriesOrder IS NULL FROM books WHERE id = '$bookId'", 1)
                assertSingleLong(connection, "SELECT customCoverPath IS NULL FROM books WHERE id = '$bookId'", 1)
                assertSingleLong(connection, "SELECT finished FROM books WHERE id = '$bookId'", 0)
            }
            databaseFile.delete()
        }
    }

    private fun assertSingleValue(
        connection: androidx.sqlite.SQLiteConnection,
        query: String,
        expected: String,
    ) {
        connection.prepare(query).use { statement ->
            assertThat(statement.step()).isTrue()
            assertThat(statement.getText(0)).isEqualTo(expected)
        }
    }

    private fun assertSingleLong(
        connection: androidx.sqlite.SQLiteConnection,
        query: String,
        expected: Long,
    ) {
        connection.prepare(query).use { statement ->
            assertThat(statement.step()).isTrue()
            assertThat(statement.getLong(0)).isEqualTo(expected)
        }
    }
}
