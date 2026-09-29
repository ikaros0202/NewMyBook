package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BUILT_IN_PAPER_ID
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreConflictResolution
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestoreRequest
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultRestorePlanResolverTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `merge applies explicit conflict choices and remaps dependent settings`() {
        val current = BackupCatalogSnapshot(
            books = listOf(book("local-book", "local-hash", groupId = "local-group")),
            groups = listOf(BackupGroupRecord("local-group", "同名分组", 0, 1, 1)),
            themes = listOf(BackupThemeRecord("local-theme", "同名主题", settings(), false, 1)),
            fonts = listOf(BackupFontRecord("local-font", "本地字体", sha("local-font"), 1, 1)),
            globalSettings = BackupGlobalSettingsRecord(settings(), schedule(), 1),
        )
        val backup = BackupCatalogSnapshot(
            books = listOf(book("incoming-book", "incoming-hash", groupId = "incoming-group")),
            groups = listOf(BackupGroupRecord("incoming-group", "同名分组", 2, 2, 2)),
            themes = listOf(BackupThemeRecord("incoming-theme", "同名主题", settings("incoming-font"), false, 2)),
            fonts = listOf(BackupFontRecord("incoming-font", "备份字体", sha("incoming-font"), 1, 2, "assets/fonts/incoming-font/font.bin")),
            globalSettings = BackupGlobalSettingsRecord(
                settings("incoming-font"), schedule("incoming-theme"), 2,
            ),
            bookSettings = listOf(
                BackupBookSettingsRecord(
                    "incoming-book",
                    readerDataJson.encodeToString(ReaderSettingsOverrides(font = ReaderFontRef.Imported("incoming-font"))),
                    2,
                ),
            ),
        )
        val prepared = prepared(backup, current, BackupOptions(includeBookText = true, includeFonts = true))
        val request = RestoreRequest(
            prepared.plan.stagedPlanToken,
            RestoreMode.MERGE,
            prepared.plan.conflicts.map { conflict ->
                when (conflict.id) {
                    "group:incoming-group" -> RestoreConflictResolution(conflict.id, RestoreConflictChoice.RENAME_BACKUP, "备份分组")
                    "theme:incoming-theme" -> RestoreConflictResolution(conflict.id, RestoreConflictChoice.RENAME_BACKUP, "备份主题")
                    "settings:global" -> RestoreConflictResolution(conflict.id, RestoreConflictChoice.USE_BACKUP)
                    else -> RestoreConflictResolution(conflict.id, conflict.suggestedChoice)
                }
            },
        )

        val resolved = DefaultRestorePlanResolver().resolve(prepared, request)

        val restoredBook = resolved.desiredCatalog.books.single { it.id == "incoming-book" }
        val restoredMembership = resolved.desiredCatalog.memberships.single { it.bookId == restoredBook.id }
        val restoredGroup = resolved.desiredCatalog.groups.single { it.id == restoredMembership.groupId }
        val restoredTheme = resolved.desiredCatalog.themes.single { it.name == "备份主题" }
        val restoredFont = resolved.desiredCatalog.fonts.single { it.contentSha256 == sha("incoming-font") }
        val global = requireNotNull(resolved.desiredCatalog.globalSettings)
        val decodedSettings = readerDataJson.decodeFromString<ReaderSettings>(global.settingsJson)
        val decodedSchedule = readerDataJson.decodeFromString<StoredReaderThemeSchedule>(global.scheduleJson)
        val decodedOverrides = readerDataJson.decodeFromString<ReaderSettingsOverrides>(
            resolved.desiredCatalog.bookSettings.single { it.bookId == restoredBook.id }.overridesJson,
        )
        assertThat(restoredGroup.name).isEqualTo("备份分组")
        assertThat((decodedSettings.font as ReaderFontRef.Imported).fontId).isEqualTo(restoredFont.id)
        assertThat(decodedSchedule.schedule.lightThemeId).isEqualTo(restoredTheme.id)
        assertThat((decodedOverrides.font as ReaderFontRef.Imported).fontId).isEqualTo(restoredFont.id)
    }

    @Test
    fun `overwrite restores exact backup identities and deletes unreferenced live assets`() {
        val current = BackupCatalogSnapshot(
            books = listOf(book("old-book", "old")),
            fonts = listOf(BackupFontRecord("old-font", "旧字体", sha("old-font"), 1, 1)),
            bookSources = listOf(BackupBookSource("old-book", "books/old-book/original.txt", "books/old-book/content.txt", "books/old-book/offsets.xidx", null)),
            fontSources = listOf(BackupFontSource("old-font", "fonts/old-font/font.bin")),
        )
        val backup = BackupCatalogSnapshot(books = listOf(book("new-book", "new")))
        val prepared = prepared(backup, current, BackupOptions())

        val resolved = DefaultRestorePlanResolver().resolve(
            prepared,
            RestoreRequest(prepared.plan.stagedPlanToken, RestoreMode.OVERWRITE, emptyList()),
        )

        assertThat(resolved.desiredCatalog.books.map { it.id }).containsExactly("new-book")
        assertThat(resolved.desiredCatalog.bookSources.single().originalRelativePath)
            .isEqualTo("books/new-book/original.txt")
        assertThat(resolved.deleteTargetRelativePaths).containsAtLeast(
            "books/old-book/original.txt",
            "books/old-book/content.txt",
            "books/old-book/offsets.xidx",
            "fonts/old-font/font.bin",
        )
    }

    @Test
    fun `merge requires one valid resolution per conflict and overwrite requires book text`() {
        val current = BackupCatalogSnapshot(globalSettings = BackupGlobalSettingsRecord(settings(), schedule(), 1))
        val backup = BackupCatalogSnapshot(globalSettings = BackupGlobalSettingsRecord(settings(), schedule(), 2))
        val prepared = prepared(backup, current, BackupOptions(includeBookText = false, includeFonts = false))

        assertThat(
            runCatching {
                DefaultRestorePlanResolver().resolve(
                    prepared,
                    RestoreRequest(prepared.plan.stagedPlanToken, RestoreMode.MERGE, emptyList()),
                )
            }.exceptionOrNull(),
        ).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(
            runCatching {
                DefaultRestorePlanResolver().resolve(
                    prepared,
                    RestoreRequest(prepared.plan.stagedPlanToken, RestoreMode.OVERWRITE, emptyList()),
                )
            }.exceptionOrNull(),
        ).isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun prepared(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        options: BackupOptions,
    ): PreparedRestorePlan {
        val root = temporary.newFolder()
        val stagedRoot = File(root, "import-token").apply { mkdirs() }
        val extracted = File(stagedRoot, "extracted").apply { mkdirs() }
        val entries = backup.books.flatMap { book ->
            if (!options.includeBookText) emptyList() else listOf(
                com.xinyue.reader.core.domain.model.BackupManifestEntry(requireNotNull(book.originalAssetPath), com.xinyue.reader.core.domain.model.BackupEntryKind.ORIGINAL_TEXT, 1, "a".repeat(64)),
                com.xinyue.reader.core.domain.model.BackupManifestEntry(requireNotNull(book.normalizedAssetPath), com.xinyue.reader.core.domain.model.BackupEntryKind.NORMALIZED_TEXT, 1, "b".repeat(64)),
                com.xinyue.reader.core.domain.model.BackupManifestEntry(requireNotNull(book.offsetIndexAssetPath), com.xinyue.reader.core.domain.model.BackupEntryKind.OFFSET_INDEX, 1, "c".repeat(64)),
            )
        } + if (options.includeFonts) backup.fonts.map { font ->
            com.xinyue.reader.core.domain.model.BackupManifestEntry(
                requireNotNull(font.assetPath), com.xinyue.reader.core.domain.model.BackupEntryKind.FONT, 1, "d".repeat(64),
            )
        } else emptyList()
        val manifest = BackupManifest(appVersion = "test", createdAtEpochMillis = 1, options = options, entries = entries)
        val files = entries.associate { entry ->
            val file = BackupPathPolicy.resolve(extracted, entry.path).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1))
            }
            entry.path to file
        }
        val plan = RestorePlanner { "generated-${System.nanoTime()}" }.plan(
            "token", backup, current, manifest, files.keys, 0, 0,
        )
        return PreparedRestorePlan(
            plan,
            StagedBackup(stagedRoot, File(stagedRoot, "archive"), extracted, manifest, files, 0, entries.size.toLong()),
            backup,
            current,
        )
    }

    private fun book(
        id: String,
        hashSeed: String,
        groupId: String? = null,
        includeAssets: Boolean = true,
    ) = BackupBookRecord(
        id = id,
        title = "公开书名-$id",
        originalFileName = "public.txt",
        charsetName = "UTF-8",
        contentSha256 = sha(hashSeed),
        contentLength = 1,
        createdAtEpochMillis = 1,
        groupId = groupId,
        originalAssetPath = if (includeAssets) "assets/books/$id/original.txt" else null,
        normalizedAssetPath = if (includeAssets) "assets/books/$id/content.txt" else null,
        offsetIndexAssetPath = if (includeAssets) "assets/books/$id/offsets.xidx" else null,
    )

    private fun settings(fontId: String? = null) = readerDataJson.encodeToString(
        ReaderSettings(font = fontId?.let { ReaderFontRef.Imported(it) } ?: ReaderFontRef.System),
    )

    private fun schedule(themeId: String = BUILT_IN_PAPER_ID) = readerDataJson.encodeToString(
        StoredReaderThemeSchedule(ReaderThemeSchedule(lightThemeId = themeId)),
    )

    private fun sha(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}
