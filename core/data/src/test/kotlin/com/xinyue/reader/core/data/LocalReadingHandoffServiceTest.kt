package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupArchiveType
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportResult
import com.xinyue.reader.core.domain.model.HandoffImportRequest
import com.xinyue.reader.core.domain.model.HandoffImportResult
import com.xinyue.reader.core.domain.model.HandoffInspectResult
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.RestoreConflictResolution
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalReadingHandoffServiceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `export writes a distinctly marked one-book whitelist and cleans temporary files`() = runTest {
        val root = temporary.newFolder("export-private")
        write(root, "fonts/private-font/font.bin", "private font fixture")
        val source = completeSnapshot(root, "book-1", "a").copy(
            books = listOf(book(root, "book-1", "a"), book(root, "book-2", "b")),
            bookSettings = listOf(
                BackupBookSettingsRecord(
                    "book-1",
                    readerDataJson.encodeToString(
                        ReaderSettingsOverrides(
                            font = ReaderFontRef.Imported("private-font"),
                            fontSizeSp = 25f,
                        ),
                    ),
                    1,
                ),
            ),
            globalSettings = BackupGlobalSettingsRecord("{}", "{}", 1),
            themes = listOf(BackupThemeRecord("theme-1", "公开主题", "{}", false, 1)),
            fonts = listOf(
                BackupFontRecord(
                    "private-font",
                    "私有字体",
                    "f".repeat(64),
                    File(root, "fonts/private-font/font.bin").length(),
                    1,
                    "assets/fonts/private-font/font.bin",
                ),
            ),
            fontSources = listOf(BackupFontSource("private-font", "fonts/private-font/font.bin")),
            sessions = listOf(BackupSessionRecord("session-1", "book-1", 1, 2, 3, 1)),
        )
        val gateway = MemoryGateway()
        val service = service(root, { source }, gateway, ids = ArrayDeque(listOf("export-token")))

        val result = service.export(
            "content://public/handoff",
            HandoffExportRequest("book-1", includeBookText = false),
        )

        assertThat(result).isInstanceOf(HandoffExportResult.Success::class.java)
        val staged = SecureBackupArchive().inspectAndExtract(
            ByteArrayInputStream(gateway.bytes),
            temporary.newFolder("export-inspect"),
            "inspect-token",
            gateway.bytes.size.toLong(),
            Long.MAX_VALUE,
        )
        try {
            assertThat(staged.manifest.archiveType).isEqualTo(BackupArchiveType.HANDOFF)
            assertThat(staged.manifest.rootBookId).isEqualTo("book-1")
            assertThat(staged.manifest.options).isEqualTo(BackupOptions(false, false))
            val decoded = BackupCatalogCodec.decode(staged.files, 2)
            assertThat(decoded.books.map { it.id }).containsExactly("book-1")
            assertThat(decoded.globalSettings).isNull()
            assertThat(decoded.themes).isEmpty()
            assertThat(decoded.fonts).isEmpty()
            assertThat(decoded.sessions).isEmpty()
            assertThat(decoded.groups.map { it.id }).containsExactly("group-1")
            assertThat(decoded.memberships).containsExactly(BackupBookMembershipRecord("book-1", "group-1"))
            val overrides = readerDataJson.decodeFromString<ReaderSettingsOverrides>(
                decoded.bookSettings.single().overridesJson,
            )
            assertThat(overrides.font).isNull()
            assertThat(overrides.fontSizeSp).isEqualTo(25f)
        } finally {
            staged.cleanup()
        }
        assertThat(File(root, "handoff-staging").listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `inspect rejects no-text hash mismatch and a full backup renamed as handoff`() = runTest {
        val sourceRoot = temporary.newFolder("mismatch-source")
        val archive = handoffArchive(sourceRoot, completeSnapshot(sourceRoot, "source", "a"), includeText = false)
        val currentRoot = temporary.newFolder("mismatch-current")
        val current = completeSnapshot(currentRoot, "target", "b")
        val mismatch = service(
            currentRoot,
            { current },
            MemoryGateway(archive.readBytes()),
            ids = ArrayDeque(listOf("mismatch-token")),
        ).inspect("content://public/source")

        assertThat(mismatch).isInstanceOf(HandoffInspectResult.Failure::class.java)

        val backupFile = temporary.newFile("renamed.xinyuehandoff")
        val inventory = BackupAssetInventory(sourceRoot)
        SecureBackupWriter(inventory).write(
            backupFile,
            BackupCatalogSnapshot(),
            emptyList(),
            BackupOptions(false, false),
            "test",
            1,
        )
        val renamed = service(
            currentRoot,
            { current },
            MemoryGateway(backupFile.readBytes()),
            ids = ArrayDeque(listOf("renamed-token")),
        ).inspect("content://public/renamed")
        assertThat(renamed).isInstanceOf(HandoffInspectResult.Failure::class.java)
    }

    @Test
    fun `same conflicting annotation is kept once and repeated import is idempotent`() = runTest {
        val sourceRoot = temporary.newFolder("repeat-source")
        val incoming = completeSnapshot(sourceRoot, "source", "a").copy(
            annotations = listOf(annotation("note-1", "source", "接力内容")),
        )
        val archiveBytes = handoffArchive(sourceRoot, incoming, includeText = false).readBytes()
        val currentRoot = temporary.newFolder("repeat-current")
        val database = FakeDatabase(
            completeSnapshot(currentRoot, "target", "a").copy(
                annotations = listOf(annotation("note-1", "target", "本机内容")),
            ),
        )
        val gateway = MemoryGateway(archiveBytes)
        val registry = StagedRestorePlanRegistry()
        val publisher = RestorePublisher(
            currentRoot,
            database,
            DefaultRestorePlanResolver(),
            RestoreJournalStore(currentRoot),
            RestoreSnapshotStore(currentRoot),
            freeBytes = { Long.MAX_VALUE },
        )
        val service = service(
            currentRoot,
            { database.state },
            gateway,
            ids = ArrayDeque(listOf("first-token", "second-token")),
            registry = registry,
            publisher = publisher,
        )

        repeat(2) {
            val inspected = service.inspect("content://public/source") as HandoffInspectResult.Success
            val resolutions = inspected.preview.conflicts.map {
                RestoreConflictResolution(it.id, it.suggestedChoice)
            }
            val imported = service.import(HandoffImportRequest(inspected.preview.stagedPlanToken, resolutions))
            assertThat(imported).isInstanceOf(HandoffImportResult.Success::class.java)
        }

        assertThat(database.state.annotations).hasSize(2)
        assertThat(database.state.annotations.map { it.note }).containsExactly("本机内容", "接力内容")
        assertThat(database.state.annotations.count { it.id.startsWith("handoff-") }).isEqualTo(1)
        assertThat(registry.sizeForTest()).isEqualTo(0)
    }

    @Test
    fun `handoff with text imports a new book into an empty library`() = runTest {
        val sourceRoot = temporary.newFolder("new-book-source")
        val incoming = completeSnapshot(sourceRoot, "source", "a").copy(
            annotations = listOf(annotation("note-1", "source", "公开接力批注")),
        )
        val archiveBytes = handoffArchive(sourceRoot, incoming, includeText = true).readBytes()
        val targetRoot = temporary.newFolder("new-book-target")
        val database = FakeDatabase(BackupCatalogSnapshot())
        val gateway = MemoryGateway(archiveBytes)
        val registry = StagedRestorePlanRegistry()
        val resolver = DefaultRestorePlanResolver()
        val publisher = RestorePublisher(
            targetRoot,
            database,
            resolver,
            RestoreJournalStore(targetRoot),
            RestoreSnapshotStore(targetRoot),
            freeBytes = { Long.MAX_VALUE },
        )
        val service = service(
            targetRoot,
            { database.state },
            gateway,
            ids = ArrayDeque(listOf("new-book-token")),
            registry = registry,
            publisher = publisher,
        )
        val preview = (service.inspect("content://public/new-book") as HandoffInspectResult.Success).preview
        val request = HandoffImportRequest(
            preview.stagedPlanToken,
            preview.conflicts.map { RestoreConflictResolution(it.id, it.suggestedChoice) },
        )

        val prepared = requireNotNull(registry.peek(preview.stagedPlanToken))
        val resolved = resolver.resolve(
            prepared,
            com.xinyue.reader.core.domain.model.RestoreRequest(
                preview.stagedPlanToken,
                com.xinyue.reader.core.domain.model.RestoreMode.MERGE,
                request.resolutions,
            ),
        )
        assertThat(resolved.desiredCatalog.books).hasSize(1)

        val result = service.import(request)

        assertThat(result).isInstanceOf(HandoffImportResult.Success::class.java)
        assertThat(database.state.books.map { it.title }).containsExactly("公开书名-source")
        assertThat(database.state.progress.single().offset).isEqualTo(10)
        assertThat(database.state.annotations.map { it.note }).containsExactly("公开接力批注")
        assertThat(File(targetRoot, "books/source/content.txt").isFile).isTrue()
        assertThat(File(targetRoot, "backup-staging").listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `handoff publish failure rolls database and private files back without residue`() = runTest {
        val sourceRoot = temporary.newFolder("rollback-source")
        val incoming = completeSnapshot(sourceRoot, "source", "a").copy(
            progress = listOf(BackupProgressRecord("source", 80, "new", "before", "after", 100, 20)),
        )
        val archiveBytes = handoffArchive(sourceRoot, incoming, includeText = false).readBytes()
        val currentRoot = temporary.newFolder("rollback-current")
        val before = completeSnapshot(currentRoot, "target", "a")
        val database = FakeDatabase(before)
        val registry = StagedRestorePlanRegistry()
        val publisher = RestorePublisher(
            currentRoot,
            database,
            DefaultRestorePlanResolver(),
            RestoreJournalStore(currentRoot),
            RestoreSnapshotStore(currentRoot),
            faultInjector = RestoreFaultInjector { point ->
                if (point == RestoreFaultPoint.AFTER_DATABASE_COMMIT) error("injected")
            },
            freeBytes = { Long.MAX_VALUE },
        )
        val service = service(
            currentRoot,
            { database.state },
            MemoryGateway(archiveBytes),
            ids = ArrayDeque(listOf("rollback-token")),
            registry = registry,
            publisher = publisher,
        )
        val preview = (service.inspect("content://public/rollback") as HandoffInspectResult.Success).preview

        val result = service.import(
            HandoffImportRequest(
                preview.stagedPlanToken,
                preview.conflicts.map { RestoreConflictResolution(it.id, it.suggestedChoice) },
            ),
        )

        assertThat(result).isInstanceOf(HandoffImportResult.Failure::class.java)
        assertThat(database.state.canonicalForRestore()).isEqualTo(before.canonicalForRestore())
        assertThat(File(currentRoot, "restore-journals").listFiles().orEmpty()).isEmpty()
        assertThat(File(currentRoot, "restore-snapshots").listFiles().orEmpty()).isEmpty()
        assertThat(File(currentRoot, "backup-staging").listFiles().orEmpty()).isEmpty()
    }

    private fun service(
        root: File,
        snapshot: suspend () -> BackupCatalogSnapshot,
        gateway: BackupDocumentGateway,
        ids: ArrayDeque<String>,
        registry: StagedRestorePlanRegistry = StagedRestorePlanRegistry(),
        publisher: RestorePublisher? = null,
    ) = LocalReadingHandoffService(
        catalogDataSource = BackupCatalogDataSource { snapshot() },
        assetInventory = BackupAssetInventory(root),
        archiveWriter = SecureBackupWriter(BackupAssetInventory(root)),
        documentGateway = gateway,
        privateRoot = root,
        appVersion = "test",
        nowEpochMillis = { 1 },
        idFactory = { ids.removeFirst() },
        planner = HandoffPlanner(),
        stagedPlans = registry,
        restorePublisher = publisher,
    )

    private fun handoffArchive(root: File, snapshot: BackupCatalogSnapshot, includeText: Boolean): File {
        val options = BackupOptions(includeText, false)
        val inventory = BackupAssetInventory(root)
        val collected = inventory.collect(snapshot, options)
        return temporary.newFile("handoff-${System.nanoTime()}.xinyuehandoff").also { file ->
            runBlocking {
                SecureBackupWriter(inventory).writeHandoff(
                    file,
                    collected.catalog,
                    collected.assets,
                    options,
                    "test",
                    1,
                    snapshot.books.single().id,
                )
            }
        }
    }

    private fun completeSnapshot(root: File, id: String, hashSeed: String): BackupCatalogSnapshot =
        BackupCatalogSnapshot(
            books = listOf(book(root, id, hashSeed)),
            groups = listOf(BackupGroupRecord("group-1", "公开集合", 0, 1, 1)),
            memberships = listOf(BackupBookMembershipRecord(id, "group-1")),
            progress = listOf(BackupProgressRecord(id, 10, "anchor", "before", "after", 100, 2)),
            bookSettings = listOf(BackupBookSettingsRecord(id, "{}", 1)),
            bookSources = listOf(
                BackupBookSource(
                    id,
                    "books/$id/original.txt",
                    "books/$id/content.txt",
                    "books/$id/offsets.xidx",
                    null,
                ),
            ),
        )

    private fun book(root: File, id: String, hashSeed: String): BackupBookRecord {
        write(root, "books/$id/original.txt", "public original $id")
        write(root, "books/$id/content.txt", "public content $id")
        write(root, "books/$id/offsets.xidx", "public offsets $id")
        return BackupBookRecord(
            id = id,
            title = "公开书名-$id",
            originalFileName = "public.txt",
            charsetName = "UTF-8",
            contentSha256 = hashSeed.repeat(64),
            contentLength = 100,
            createdAtEpochMillis = 1,
            seriesName = "公开系列",
            seriesOrder = 1,
            originalAssetPath = "assets/books/$id/original.txt",
            normalizedAssetPath = "assets/books/$id/content.txt",
            offsetIndexAssetPath = "assets/books/$id/offsets.xidx",
        )
    }

    private fun annotation(id: String, bookId: String, note: String) = BackupAnnotationRecord(
        id, bookId, "NOTE", 1, 2, "before", "after", "c".repeat(64), "yellow", note, 1, 2,
    )

    private fun write(root: File, path: String, value: String) {
        File(root, path).also { it.parentFile?.mkdirs(); it.writeText(value) }
    }

    private class FakeDatabase(initial: BackupCatalogSnapshot) : RestoreDatabaseGateway {
        var state = initial
        override suspend fun snapshot(): BackupCatalogSnapshot = state
        override suspend fun replaceAll(snapshot: BackupCatalogSnapshot) {
            state = snapshot
        }
    }

    private class MemoryGateway(initial: ByteArray = ByteArray(0)) : BackupDocumentGateway {
        private var stored = initial
        val bytes: ByteArray get() = stored
        override fun openForWrite(destinationUri: String): OutputStream = object : ByteArrayOutputStream() {
            override fun close() {
                stored = toByteArray()
                super.close()
            }
        }
        override fun openForRead(sourceUri: String): InputStream = ByteArrayInputStream(stored)
        override fun querySize(sourceUri: String): Long = stored.size.toLong()
        override fun invalidate(destinationUri: String): Boolean {
            stored = ByteArray(0)
            return true
        }
    }
}
