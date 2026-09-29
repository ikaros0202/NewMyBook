package com.xinyue.reader.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `annotations` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `startOffset` INTEGER NOT NULL,
                `endOffset` INTEGER NOT NULL,
                `prefix` TEXT NOT NULL,
                `suffix` TEXT NOT NULL,
                `selectedSha256` TEXT,
                `color` TEXT,
                `note` TEXT,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `annotations` (
                `id`, `bookId`, `kind`, `startOffset`, `endOffset`, `prefix`, `suffix`,
                `selectedSha256`, `color`, `note`, `createdAtEpochMillis`, `updatedAtEpochMillis`
            )
            SELECT
                `id`, `bookId`, 'BOOKMARK', `offset`, `offset`, '', '',
                NULL, NULL, `note`, `createdAtEpochMillis`, `createdAtEpochMillis`
            FROM `bookmarks`
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE `bookmarks`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_bookId` ON `annotations` (`bookId`)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_annotations_bookId_kind` ON `annotations` (`bookId`, `kind`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_annotations_bookId_startOffset` " +
                "ON `annotations` (`bookId`, `startOffset`)",
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `reading_progress` ADD COLUMN `prefix` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `reading_progress` ADD COLUMN `suffix` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reader_global_settings` (
                `id` INTEGER NOT NULL,
                `settingsJson` TEXT NOT NULL,
                `scheduleJson` TEXT NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `book_reader_overrides` (
                `bookId` TEXT NOT NULL,
                `overridesJson` TEXT NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`bookId`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_book_reader_overrides_bookId` " +
                "ON `book_reader_overrides` (`bookId`)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reader_themes` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `settingsJson` TEXT NOT NULL,
                `builtIn` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_reader_themes_name` ON `reader_themes` (`name`)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `imported_fonts` (
                `id` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `privateRelativePath` TEXT NOT NULL,
                `contentSha256` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_imported_fonts_contentSha256` " +
                "ON `imported_fonts` (`contentSha256`)",
        )
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `groupId` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `customCoverPath` TEXT")
        connection.execSQL("ALTER TABLE `books` ADD COLUMN `finished` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `book_groups` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_book_groups_name` ON `book_groups` (`name`)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_sessions` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `startedAtEpochMillis` INTEGER NOT NULL,
                `lastInteractionAtEpochMillis` INTEGER NOT NULL,
                `endedAtEpochMillis` INTEGER,
                `activeMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId` ON `reading_sessions` (`bookId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_endedAtEpochMillis` " +
                "ON `reading_sessions` (`endedAtEpochMillis`)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_daily_stats` (
                `bookId` TEXT NOT NULL,
                `localEpochDay` INTEGER NOT NULL,
                `activeMillis` INTEGER NOT NULL,
                `sessionCount` INTEGER NOT NULL,
                PRIMARY KEY(`bookId`, `localEpochDay`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_daily_stats_bookId` " +
                "ON `reading_daily_stats` (`bookId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_daily_stats_localEpochDay` " +
                "ON `reading_daily_stats` (`localEpochDay`)",
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TEMP TABLE `legacy_book_group_memberships` (
                `bookId` TEXT NOT NULL,
                `groupId` TEXT NOT NULL,
                PRIMARY KEY(`bookId`, `groupId`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `legacy_book_group_memberships` (`bookId`, `groupId`)
            SELECT `id`, `groupId` FROM `books` WHERE `groupId` IS NOT NULL
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE `books_new` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT,
                `originalFileName` TEXT NOT NULL,
                `originalPath` TEXT NOT NULL,
                `normalizedPath` TEXT NOT NULL,
                `charsetName` TEXT NOT NULL,
                `contentSha256` TEXT NOT NULL,
                `contentLength` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `lastOpenedAtEpochMillis` INTEGER,
                `seriesName` TEXT,
                `seriesOrder` INTEGER,
                `customCoverPath` TEXT,
                `finished` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `books_new` (
                `id`, `title`, `author`, `originalFileName`, `originalPath`, `normalizedPath`,
                `charsetName`, `contentSha256`, `contentLength`, `createdAtEpochMillis`,
                `lastOpenedAtEpochMillis`, `seriesName`, `seriesOrder`, `customCoverPath`, `finished`
            )
            SELECT
                `id`, `title`, `author`, `originalFileName`, `originalPath`, `normalizedPath`,
                `charsetName`, `contentSha256`, `contentLength`, `createdAtEpochMillis`,
                `lastOpenedAtEpochMillis`, NULL, NULL, `customCoverPath`, `finished`
            FROM `books`
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE `books`")
        connection.execSQL("ALTER TABLE `books_new` RENAME TO `books`")
        connection.execSQL(
            """
            CREATE TABLE `book_group_memberships` (
                `bookId` TEXT NOT NULL,
                `groupId` TEXT NOT NULL,
                PRIMARY KEY(`bookId`, `groupId`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`groupId`) REFERENCES `book_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX `index_book_group_memberships_groupId` " +
                "ON `book_group_memberships` (`groupId`)",
        )
        connection.execSQL(
            """
            INSERT INTO `book_group_memberships` (`bookId`, `groupId`)
            SELECT `bookId`, `groupId` FROM `legacy_book_group_memberships`
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE `legacy_book_group_memberships`")
    }
}
