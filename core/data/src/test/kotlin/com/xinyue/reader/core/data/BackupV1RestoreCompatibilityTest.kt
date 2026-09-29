package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupV1RestoreCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `v1 backup remains inspectable and restores legacy group as collection membership`() = runTest {
        val legacyBook = record("legacy-book", "a").copy(
            groupId = "legacy-group",
            originalAssetPath = null,
            normalizedAssetPath = null,
            offsetIndexAssetPath = null,
        )
        val legacy = BackupCatalogSnapshot(
            books = listOf(legacyBook),
            groups = listOf(BackupGroupRecord("legacy-group", "旧分组", 0, 1, 1)),
            progress = listOf(BackupProgressRecord("legacy-book", 20, "anchor", "before", "after", 100, 5)),
        )
        val archive = v1Archive(legacy)
        val root = temporary.newFolder("current")
        writeBookFiles(root, "target-book")
        val database = FakeDatabase(
            BackupCatalogSnapshot(
                books = listOf(record("target-book", "a")),
                bookSources = listOf(source("target-book")),
            ),
        )
        val registry = StagedRestorePlanRegistry()
        val resolver = DefaultRestorePlanResolver()
        val publisher = RestorePublisher(
            root,
            database,
            resolver,
            RestoreJournalStore(root),
            RestoreSnapshotStore(root),
            freeBytes = { Long.MAX_VALUE },
        )
        val service = LocalBackupService(
            catalogDataSource = BackupCatalogDataSource { database.state },
            assetInventory = BackupAssetInventory(root),
            archiveWriter = SecureBackupWriter(BackupAssetInventory(root)),
            documentGateway = ReadGateway(archive),
            privateRoot = root,
            appVersion = "test",
            nowEpochMillis = { 1 },
            idFactory = { "v1-token" },
            stagedPlans = registry,
            restorePublisher = publisher,
        )

        val inspectResult = service.inspect("content://public/v1")
        assertThat(inspectResult).isInstanceOf(BackupInspectResult.Success::class.java)
        val inspected = inspectResult as BackupInspectResult.Success
        val restored = service.restore(
            RestoreRequest(inspected.preview.stagedPlanToken, RestoreMode.MERGE, emptyList()),
        )

        assertThat(restored).isInstanceOf(RestoreResult.Success::class.java)
        assertThat(database.state.memberships)
            .containsExactly(BackupBookMembershipRecord("target-book", "legacy-group"))
        assertThat(database.state.groups.single().name).isEqualTo("旧分组")
        assertThat(database.state.progress.single().bookId).isEqualTo("target-book")
    }

    private fun v1Archive(snapshot: BackupCatalogSnapshot): ByteArray {
        val catalogs = BackupCatalogCodec.encode(snapshot)
            .filterKeys { it != BackupCatalogCodec.MEMBERSHIPS_PATH }
            .toMutableMap()
        catalogs[BackupCatalogCodec.BOOKS_PATH] =
            Json { encodeDefaults = true; explicitNulls = false }
                .encodeToString(snapshot.books)
                .encodeToByteArray()
        val entries = catalogs.map { (path, bytes) ->
            BackupManifestEntry(path, BackupEntryKind.CATALOG, bytes.size.toLong(), sha(bytes))
        }
        val manifest = BackupManifest(
            formatVersion = 1,
            appVersion = "legacy",
            createdAtEpochMillis = 1,
            options = BackupOptions(false, false),
            entries = entries,
        )
        return ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                fun add(path: String, payload: ByteArray) {
                    zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                    zip.write(payload)
                    zip.closeEntry()
                }
                add(BackupManifestCodec.MANIFEST_PATH, BackupManifestCodec.encode(manifest))
                catalogs.toSortedMap().forEach { (path, payload) -> add(path, payload) }
            }
        }.toByteArray()
    }

    private fun record(id: String, hashSeed: String) = BackupBookRecord(
        id = id,
        title = "公开书名",
        originalFileName = "public.txt",
        charsetName = "UTF-8",
        contentSha256 = hashSeed.repeat(64),
        contentLength = 100,
        createdAtEpochMillis = 1,
        originalAssetPath = "assets/books/$id/original.txt",
        normalizedAssetPath = "assets/books/$id/content.txt",
        offsetIndexAssetPath = "assets/books/$id/offsets.xidx",
    )

    private fun source(id: String) = BackupBookSource(
        id,
        "books/$id/original.txt",
        "books/$id/content.txt",
        "books/$id/offsets.xidx",
        null,
    )

    private fun writeBookFiles(root: File, id: String) {
        listOf("original.txt", "content.txt", "offsets.xidx").forEach { name ->
            File(root, "books/$id/$name").also { it.parentFile?.mkdirs(); it.writeText("public-$name") }
        }
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class FakeDatabase(initial: BackupCatalogSnapshot) : RestoreDatabaseGateway {
        var state = initial
        override suspend fun snapshot(): BackupCatalogSnapshot = state
        override suspend fun replaceAll(snapshot: BackupCatalogSnapshot) {
            state = snapshot
        }
    }

    private class ReadGateway(private val bytes: ByteArray) : BackupDocumentGateway {
        override fun openForRead(sourceUri: String): InputStream = ByteArrayInputStream(bytes)
        override fun querySize(sourceUri: String): Long = bytes.size.toLong()
        override fun openForWrite(destinationUri: String): OutputStream = ByteArrayOutputStream()
        override fun invalidate(destinationUri: String): Boolean = true
    }
}
