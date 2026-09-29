package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.RestoreActionKind
import com.xinyue.reader.core.domain.model.RestoreConflictKind
import com.xinyue.reader.core.domain.model.RestoreEntityKind
import java.security.MessageDigest
import org.junit.Test

class RestorePlannerTest {
    @Test
    fun `books merge by deterministic hash mapping copy id collisions and remap all dependents`() {
        val ids = ArrayDeque(listOf("collision", "generated-book"))
        val planner = RestorePlanner { ids.removeFirst() }
        val current = BackupCatalogSnapshot(
            books = listOf(book("z-local", "hash-same"), book("a-local", "hash-same"), book("collision", "local-hash")),
        )
        val backupBooks = listOf(
            book("backup-same", "hash-same"),
            book("new-book", "new-hash"),
            book("collision", "different-hash"),
            book("duplicate-new", "new-hash"),
        )
        val backup = BackupCatalogSnapshot(
            books = backupBooks,
            progress = listOf(progress("backup-same", 10, 2)),
            annotations = listOf(annotation("note-1", "backup-same", note = "公开批注")),
            sessions = listOf(BackupSessionRecord("session-1", "backup-same", 1, 2, 3, 4)),
        )
        val assets = backupBooks.flatMap(::assetEntries)

        val plan = planner.plan(
            "token", backup, current, manifest(BackupOptions(), assets), assets.map { it.path }.toSet(), 200, 300,
        )

        assertThat(plan.bookIdRemap).containsExactly(
            "backup-same", "a-local",
            "new-book", "duplicate-new",
            "collision", "generated-book",
            "duplicate-new", "duplicate-new",
        )
        assertThat(plan.entityActions.single { it.kind == RestoreEntityKind.PROGRESS }.targetId).isEqualTo("a-local")
        assertThat(plan.entityActions.single { it.kind == RestoreEntityKind.ANNOTATION }.references["bookId"]).isEqualTo("a-local")
        assertThat(plan.entityActions.single { it.kind == RestoreEntityKind.SESSION }.references["bookId"]).isEqualTo("a-local")
        assertThat(plan.entityActions.single { it.sourceId == "new-book" }.action).isEqualTo(RestoreActionKind.SKIP_IDENTICAL)
        assertThat(plan.conflicts.map { it.kind }).contains(RestoreConflictKind.BOOK_ID)
        assertThat(plan.assetActions.map { it.targetRelativePath }).containsNoDuplicates()
    }

    @Test
    fun `metadata-only books are explicit while complete backup missing an asset is rejected`() {
        val planner = RestorePlanner { "unused" }
        val metadata = BackupCatalogSnapshot(books = listOf(book("metadata", "hash", includeAssets = false)))
        val metadataPlan = planner.plan(
            "metadata-token", metadata, BackupCatalogSnapshot(), manifest(BackupOptions(false, false)), emptySet(), 10, 0,
        )

        assertThat(metadataPlan.entityActions.single { it.kind == RestoreEntityKind.BOOK }.action)
            .isEqualTo(RestoreActionKind.SKIP_UNAVAILABLE)
        assertThat(metadataPlan.assetActions).isEmpty()

        val complete = BackupCatalogSnapshot(books = listOf(book("complete", "hash")))
        val required = assetEntries(complete.books.single())
        val error = runCatching {
            planner.plan("complete-token", complete, BackupCatalogSnapshot(), manifest(BackupOptions(), required), required.dropLast(1).map { it.path }.toSet(), 10, 0)
        }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `newer progress wins equal divergent progress conflicts and annotation uuid never discards content`() {
        val planner = RestorePlanner { "annotation-copy" }
        val books = listOf("newer", "older", "equal", "same").map { book(it, "hash-$it", includeAssets = false) }
        val current = BackupCatalogSnapshot(
            books = books,
            progress = listOf(progress("newer", 1, 10), progress("older", 2, 20), progress("equal", 3, 30), progress("same", 4, 40)),
            annotations = listOf(
                annotation("identical", "same", note = "same"),
                annotation("conflicting", "same", note = "local"),
            ),
        )
        val backup = BackupCatalogSnapshot(
            books = books,
            progress = listOf(progress("newer", 11, 11), progress("older", 12, 19), progress("equal", 13, 30), progress("same", 4, 40)),
            annotations = listOf(
                annotation("new-note", "same", note = "new"),
                annotation("identical", "same", note = "same"),
                annotation("conflicting", "same", note = "backup"),
            ),
        )

        val plan = planner.plan("token", backup, current, manifest(BackupOptions(false, false)), emptySet(), 0, 0)
        val progress = plan.entityActions.filter { it.kind == RestoreEntityKind.PROGRESS }.associateBy { it.sourceId }
        val annotations = plan.entityActions.filter { it.kind == RestoreEntityKind.ANNOTATION }.associateBy { it.sourceId }

        assertThat(progress.getValue("newer").action).isEqualTo(RestoreActionKind.REPLACE)
        assertThat(progress.getValue("older").action).isEqualTo(RestoreActionKind.KEEP_LOCAL)
        assertThat(progress.getValue("equal").action).isEqualTo(RestoreActionKind.KEEP_LOCAL)
        assertThat(progress.getValue("same").action).isEqualTo(RestoreActionKind.SKIP_IDENTICAL)
        assertThat(plan.conflicts.map { it.kind }).contains(RestoreConflictKind.PROGRESS)
        assertThat(annotations.getValue("new-note").action).isEqualTo(RestoreActionKind.INSERT)
        assertThat(annotations.getValue("identical").action).isEqualTo(RestoreActionKind.SKIP_IDENTICAL)
        assertThat(annotations.getValue("conflicting").action).isEqualTo(RestoreActionKind.COPY_AS_NEW)
        assertThat(annotations.getValue("conflicting").targetId).isEqualTo("annotation-copy")
        assertThat(annotations.getValue("conflicting").conflictCopyOf).isEqualTo("conflicting")
        assertThat(plan.conflicts.map { it.kind }).contains(RestoreConflictKind.ANNOTATION)
    }

    @Test
    fun `duplicate backup hashes collapse progress to one deterministic newest action`() {
        val planner = RestorePlanner { "unused" }
        val first = book("a-book", "same", includeAssets = false)
        val second = book("b-book", "same", includeAssets = false)
        val backup = BackupCatalogSnapshot(
            books = listOf(second, first),
            progress = listOf(progress("a-book", 1, 10), progress("b-book", 2, 20)),
        )

        val plan = planner.plan(
            "token", backup, BackupCatalogSnapshot(), manifest(BackupOptions(false, false)), emptySet(), 0, 0,
        )
        val progressActions = plan.entityActions.filter { it.kind == RestoreEntityKind.PROGRESS }

        assertThat(plan.bookIdRemap).containsExactly("a-book", "a-book", "b-book", "a-book")
        assertThat(progressActions).hasSize(1)
        assertThat(progressActions.single().sourceId).isEqualTo("b-book")
        assertThat(progressActions.single().targetId).isEqualTo("a-book")
    }

    @Test
    fun `named data settings fonts and statistics use explicit deterministic merge rules`() {
        val planner = RestorePlanner { "generated" }
        val current = BackupCatalogSnapshot(
            books = listOf(book("local-book", "same-hash", includeAssets = false)),
            groups = listOf(BackupGroupRecord("local-group", "科幻", 0, 1, 1)),
            themes = listOf(BackupThemeRecord("local-theme", "夜读", "local", false, 1)),
            fonts = listOf(BackupFontRecord("local-font", "字体", "f".repeat(64), 1, 1)),
            globalSettings = BackupGlobalSettingsRecord("local", "local-schedule", 1),
            sessions = listOf(BackupSessionRecord("same-session", "local-book", 1, 2, 3, 4), BackupSessionRecord("conflict-session", "local-book", 1, 2, 3, 4)),
            dailyStats = listOf(BackupDailyStatRecord("local-book", 10, 100, 1), BackupDailyStatRecord("local-book", 11, 100, 1)),
        )
        val backup = BackupCatalogSnapshot(
            books = listOf(book("backup-book", "same-hash", includeAssets = false, groupId = "backup-group")),
            groups = listOf(BackupGroupRecord("backup-group", "科幻", 1, 2, 2)),
            themes = listOf(BackupThemeRecord("backup-theme", "夜读", "backup", false, 2)),
            fonts = listOf(BackupFontRecord("backup-font", "另一字体", "f".repeat(64), 1, 2, "assets/fonts/backup-font/font.bin")),
            globalSettings = BackupGlobalSettingsRecord("backup", "backup-schedule", 2),
            bookSettings = listOf(BackupBookSettingsRecord("backup-book", "{}", 2)),
            sessions = listOf(BackupSessionRecord("same-session", "backup-book", 1, 2, 3, 4), BackupSessionRecord("new-session", "backup-book", 2, 3, 4, 5), BackupSessionRecord("conflict-session", "backup-book", 1, 2, 3, 9)),
            dailyStats = listOf(BackupDailyStatRecord("backup-book", 10, 100, 1), BackupDailyStatRecord("backup-book", 12, 200, 2), BackupDailyStatRecord("backup-book", 11, 900, 9)),
        )

        val fontEntry = BackupManifestEntry("assets/fonts/backup-font/font.bin", BackupEntryKind.FONT, 1, "d".repeat(64))
        val plan = planner.plan(
            "token", backup, current, manifest(BackupOptions(includeBookText = false, includeFonts = true), listOf(fontEntry)),
            setOf(fontEntry.path), 100, 200,
        )

        assertThat(plan.bookIdRemap["backup-book"]).isEqualTo("local-book")
        assertThat(plan.groupIdRemap["backup-group"]).isEqualTo("local-group")
        assertThat(plan.themeIdRemap["backup-theme"]).isEqualTo("local-theme")
        assertThat(plan.fontIdRemap["backup-font"]).isEqualTo("local-font")
        assertThat(plan.conflicts.map { it.kind }).containsAtLeast(
            RestoreConflictKind.GROUP,
            RestoreConflictKind.THEME,
            RestoreConflictKind.GLOBAL_SETTINGS,
            RestoreConflictKind.STATISTICS,
        )
        assertThat(plan.entityActions.single { it.kind == RestoreEntityKind.BOOK_SETTINGS }.targetId).isEqualTo("local-book")
        assertThat(plan.entityActions.single { it.sourceId == "same-session" }.action).isEqualTo(RestoreActionKind.SKIP_IDENTICAL)
        assertThat(plan.entityActions.single { it.sourceId == "new-session" }.action).isEqualTo(RestoreActionKind.INSERT)
        assertThat(plan.preview.requiredFreeBytes).isGreaterThan(plan.preview.stagingBytes + plan.preview.publishBytes + plan.preview.snapshotBytes)
    }

    private fun book(
        id: String,
        hash: String,
        includeAssets: Boolean = true,
        groupId: String? = null,
    ) = BackupBookRecord(
        id = id,
        title = "公共书名-$id",
        originalFileName = "public.txt",
        charsetName = "UTF-8",
        contentSha256 = MessageDigest.getInstance("SHA-256").digest(hash.encodeToByteArray())
            .joinToString("") { "%02x".format(it) },
        contentLength = 10,
        createdAtEpochMillis = 1,
        groupId = groupId,
        originalAssetPath = if (includeAssets) "assets/books/$id/original.txt" else null,
        normalizedAssetPath = if (includeAssets) "assets/books/$id/content.txt" else null,
        offsetIndexAssetPath = if (includeAssets) "assets/books/$id/offsets.xidx" else null,
    )

    private fun progress(bookId: String, offset: Long, updated: Long) =
        BackupProgressRecord(bookId, offset, "hash", "prefix", "suffix", 100, updated)

    private fun annotation(id: String, bookId: String, note: String) =
        BackupAnnotationRecord(id, bookId, "NOTE", 1, 2, "p", "s", "e".repeat(64), "yellow", note, 1, 2)

    private fun assetEntries(book: BackupBookRecord) = listOf(
        BackupManifestEntry(book.originalAssetPath!!, BackupEntryKind.ORIGINAL_TEXT, 10, "a".repeat(64)),
        BackupManifestEntry(book.normalizedAssetPath!!, BackupEntryKind.NORMALIZED_TEXT, 10, "b".repeat(64)),
        BackupManifestEntry(book.offsetIndexAssetPath!!, BackupEntryKind.OFFSET_INDEX, 10, "c".repeat(64)),
    )

    private fun manifest(
        options: BackupOptions,
        entries: List<BackupManifestEntry> = emptyList(),
    ) = BackupManifest(appVersion = "1.2-test", createdAtEpochMillis = 1, options = options, entries = entries)
}
