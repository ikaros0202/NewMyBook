package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupExportResult
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRestoreEndToEndTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `full export clear inspect overwrite restore is canonically equivalent`() = runTest {
        val root = temporary.newFolder("private")
        val initial = completeCatalogAndFiles(root)
        val expected = BackupEquivalenceVerifier(root).capture(initial)
        val database = FakeDatabase(initial)
        val documents = MemoryDocumentGateway()
        val registry = StagedRestorePlanRegistry()
        val resolver = DefaultRestorePlanResolver()
        val journals = RestoreJournalStore(root)
        val snapshots = RestoreSnapshotStore(root)
        val publisher = RestorePublisher(root, database, resolver, journals, snapshots, freeBytes = { Long.MAX_VALUE })
        val recovery = RestoreRecovery(root, database, resolver, journals, snapshots, registry)
        val service = LocalBackupService(
            catalogDataSource = BackupCatalogDataSource { database.state },
            assetInventory = BackupAssetInventory(root),
            archiveWriter = SecureBackupWriter(BackupAssetInventory(root)),
            documentGateway = documents,
            privateRoot = root,
            appVersion = "1.2-test",
            nowEpochMillis = { 1234 },
            idFactory = sequenceOf("export-operation", "restore-token").iterator()::next,
            stagedPlans = registry,
            restorePublisher = publisher,
            restoreRecovery = recovery,
        )

        assertThat(service.export("content://documents/public", BackupOptions()))
            .isInstanceOf(BackupExportResult.Success::class.java)
        assertThat(documents.bytes).isNotEmpty()
        database.state = BackupCatalogSnapshot()
        File(root, "books").deleteRecursively()
        File(root, "fonts").deleteRecursively()

        val inspected = service.inspect("content://documents/public") as BackupInspectResult.Success
        val restored = service.restore(
            RestoreRequest(inspected.preview.stagedPlanToken, RestoreMode.OVERWRITE, emptyList()),
        )

        assertThat(restored).isInstanceOf(RestoreResult.Success::class.java)
        val equivalence = BackupEquivalenceVerifier(root).compare(expected, database.state)
        assertThat(equivalence.equivalent).isTrue()
        assertThat(equivalence.mismatches).isEmpty()
        assertThat(File(root, "restore-journals").listFiles().orEmpty()).isEmpty()
        assertThat(File(root, "restore-snapshots").listFiles().orEmpty()).isEmpty()
        assertThat(File(root, "backup-staging").listFiles().orEmpty()).isEmpty()
    }

    private fun completeCatalogAndFiles(root: File): BackupCatalogSnapshot {
        val fontId = "font-public"
        val groupId = "group-public"
        val themeId = "theme-public"
        val fontFile = File(root, "fonts/$fontId/font.bin").also { it.parentFile!!.mkdirs(); it.writeText("public-font") }
        val books = listOf("book-a", "book-b").mapIndexed { index, id ->
            val dir = File(root, "books/$id").apply { mkdirs() }
            File(dir, "original.txt").writeText("public-original-$index")
            File(dir, "content.txt").writeText("same-public-normalized")
            File(dir, "offsets.xidx").writeBytes(byteArrayOf(1, 2, index.toByte()))
            if (index == 0) File(dir, "cover.webp").writeText("public-cover")
            BackupBookRecord(
                id = id,
                title = "公开书名-$index",
                author = "公开作者",
                originalFileName = "public-$index.txt",
                charsetName = "UTF-8",
                contentSha256 = sha("same-public-normalized".encodeToByteArray()),
                contentLength = File(dir, "content.txt").length(),
                createdAtEpochMillis = index + 1L,
                lastOpenedAtEpochMillis = index + 10L,
                groupId = groupId,
                finished = index == 1,
                originalAssetPath = "assets/books/$id/original.txt",
                normalizedAssetPath = "assets/books/$id/content.txt",
                offsetIndexAssetPath = "assets/books/$id/offsets.xidx",
                customCoverAssetPath = if (index == 0) "assets/books/$id/cover.webp" else null,
            )
        }
        val globalSettings = ReaderSettings(font = ReaderFontRef.Imported(fontId), fontSizeSp = 24f)
        val schedule = StoredReaderThemeSchedule(ReaderThemeSchedule(lightThemeId = themeId, darkThemeId = themeId))
        return BackupCatalogSnapshot(
            books = books,
            groups = listOf(BackupGroupRecord(groupId, "公开分组", 0, 1, 2)),
            progress = books.mapIndexed { index, book ->
                BackupProgressRecord(book.id, index + 1L, "anchor-$index", "prefix", "suffix", book.contentLength, 20 + index.toLong())
            },
            annotations = listOf(
                annotation("bookmark", books[0].id, "BOOKMARK", null),
                annotation("highlight", books[0].id, "HIGHLIGHT", null),
                annotation("note", books[1].id, "NOTE", "公开批注"),
            ),
            globalSettings = BackupGlobalSettingsRecord(
                readerDataJson.encodeToString(globalSettings), readerDataJson.encodeToString(schedule), 30,
            ),
            bookSettings = listOf(
                BackupBookSettingsRecord(
                    books[0].id,
                    readerDataJson.encodeToString(ReaderSettingsOverrides(font = ReaderFontRef.Imported(fontId), fontSizeSp = 26f)),
                    31,
                ),
            ),
            themes = listOf(
                BackupThemeRecord(themeId, "公开主题", readerDataJson.encodeToString(globalSettings), false, 30),
            ),
            fonts = listOf(
                BackupFontRecord(fontId, "公开字体", sha(fontFile.readBytes()), fontFile.length(), 1, "assets/fonts/$fontId/font.bin"),
            ),
            sessions = listOf(
                BackupSessionRecord("session-a", books[0].id, 1, 2, 3, 1),
                BackupSessionRecord("session-b", books[1].id, 4, 5, 6, 1),
            ),
            dailyStats = listOf(
                BackupDailyStatRecord(books[0].id, 20_000, 1, 1),
                BackupDailyStatRecord(books[1].id, 20_001, 1, 1),
            ),
            bookSources = books.map { book ->
                BackupBookSource(
                    book.id,
                    "books/${book.id}/original.txt",
                    "books/${book.id}/content.txt",
                    "books/${book.id}/offsets.xidx",
                    if (book.customCoverAssetPath != null) "books/${book.id}/cover.webp" else null,
                )
            },
            fontSources = listOf(BackupFontSource(fontId, "fonts/$fontId/font.bin")),
        )
    }

    private fun annotation(id: String, bookId: String, kind: String, note: String?) = BackupAnnotationRecord(
        id, bookId, kind, 1, if (kind == "BOOKMARK") 1 else 2, "prefix", "suffix",
        if (kind == "BOOKMARK") null else "c".repeat(64), "yellow", note, 1, 2,
    )

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private class FakeDatabase(initial: BackupCatalogSnapshot) : RestoreDatabaseGateway {
        var state = initial
        override suspend fun snapshot(): BackupCatalogSnapshot = state
        override suspend fun replaceAll(snapshot: BackupCatalogSnapshot) { state = snapshot }
    }

    private class MemoryDocumentGateway : BackupDocumentGateway {
        var bytes = ByteArray(0)
        override fun openForRead(sourceUri: String): InputStream = ByteArrayInputStream(bytes)
        override fun querySize(sourceUri: String): Long = bytes.size.toLong()
        override fun openForWrite(destinationUri: String): OutputStream {
            val target = ByteArrayOutputStream()
            return object : FilterOutputStream(target) {
                override fun close() {
                    super.close()
                    bytes = target.toByteArray()
                }
            }
        }
        override fun invalidate(destinationUri: String): Boolean { bytes = ByteArray(0); return true }
    }
}
