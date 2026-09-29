package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.test.runTest

class LocalBackupServiceInspectTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `inspect validates catalogs plans against current snapshot and exposes only opaque token`() = runTest {
        val backup = BackupCatalogSnapshot(
            books = listOf(metadataBook("backup-book")),
            progress = listOf(BackupProgressRecord("backup-book", 7, "context", "p", "s", 10, 2)),
        )
        val archive = metadataArchive("valid-inspect", backup)
        val gateway = ReadGateway(archive.readBytes())
        val registry = StagedRestorePlanRegistry()
        val root = temporaryFolder.newFolder("inspect-private")
        var snapshotReads = 0
        val service = inspectService(gateway, registry, root) { snapshotReads++; BackupCatalogSnapshot() }

        val result = service.inspect("content://must-not-escape")

        assertThat(result).isInstanceOf(BackupInspectResult.Success::class.java)
        val preview = (result as BackupInspectResult.Success).preview
        assertThat(preview.stagedPlanToken).isEqualTo("opaque-token-1")
        assertThat(preview.stagedPlanToken).doesNotContain("content://")
        assertThat(preview.newBookCount).isEqualTo(0)
        assertThat(snapshotReads).isEqualTo(1)
        val prepared = registry.takeForTest(preview.stagedPlanToken)
        assertThat(prepared.plan.stagedPlanToken).isEqualTo(preview.stagedPlanToken)
        assertThat(prepared.staged.files.keys).containsExactlyElementsIn(BackupManifestCodec.CATALOG_PATHS)
        assertThat(prepared.toString()).doesNotContain("content://must-not-escape")
        val reloaded = StagedRestorePlanRegistry().loadForTest(preview.stagedPlanToken, File(root, "backup-staging"))
        assertThat(reloaded.plan).isEqualTo(prepared.plan)
        assertThat(reloaded.backupCatalog).isEqualTo(prepared.backupCatalog)
    }

    @Test
    fun `tamper or dangling catalog reference fails before plan retention and cleans staging`() = runTest {
        val invalidCatalog = BackupCatalogSnapshot(
            progress = listOf(BackupProgressRecord("missing-book", 7, "context", "p", "s", 10, 2)),
        )
        val invalidArchive = metadataArchive("invalid-relation", invalidCatalog)
        val tamperedBytes = invalidArchive.readBytes().also { bytes -> bytes[bytes.size / 3] = (bytes[bytes.size / 3].toInt() xor 1).toByte() }

        for ((name, bytes) in listOf("relation" to invalidArchive.readBytes(), "tamper" to tamperedBytes)) {
            val root = temporaryFolder.newFolder("private-$name")
            val registry = StagedRestorePlanRegistry()
            val result = inspectService(ReadGateway(bytes), registry, root) { BackupCatalogSnapshot() }
                .inspect("content://redacted")

            assertThat(result).isInstanceOf(BackupInspectResult.Failure::class.java)
            assertThat(registry.sizeForTest()).isEqualTo(0)
            assertThat(File(root, "backup-staging").listFiles().orEmpty()).isEmpty()
        }
    }

    private fun inspectService(
        gateway: BackupDocumentGateway,
        registry: StagedRestorePlanRegistry,
        root: File = temporaryFolder.newFolder("inspect-private"),
        current: () -> BackupCatalogSnapshot,
    ) = LocalBackupService(
        catalogDataSource = BackupCatalogDataSource { current() },
        assetInventory = BackupAssetInventory(root),
        archiveWriter = SecureBackupWriter(BackupAssetInventory(root)),
        documentGateway = gateway,
        privateRoot = root,
        appVersion = "1.2-test",
        nowEpochMillis = { 123 },
        idFactory = { "opaque-token-1" },
        archiveReader = SecureBackupArchive(),
        restorePlanner = RestorePlanner { "generated-id" },
        stagedPlans = registry,
    )

    private fun metadataArchive(name: String, snapshot: BackupCatalogSnapshot): File {
        val root = temporaryFolder.newFolder("source-$name")
        val inventory = BackupAssetInventory(root)
        val collected = inventory.collect(snapshot, BackupOptions(false, false))
        return temporaryFolder.newFile("$name.xinyuebackup").also { archive ->
            kotlinx.coroutines.runBlocking {
                SecureBackupWriter(inventory).write(archive, collected.catalog, collected.assets, BackupOptions(false, false), "1.2-test", 1)
            }
        }
    }

    private fun metadataBook(id: String) = BackupBookRecord(
        id = id,
        title = "公共书名",
        originalFileName = "public.txt",
        charsetName = "UTF-8",
        contentSha256 = "a".repeat(64),
        contentLength = 10,
        createdAtEpochMillis = 1,
    )

    private class ReadGateway(private val bytes: ByteArray) : BackupDocumentGateway {
        override fun openForRead(sourceUri: String): InputStream = ByteArrayInputStream(bytes)
        override fun querySize(sourceUri: String): Long = bytes.size.toLong()
        override fun openForWrite(destinationUri: String): OutputStream = ByteArrayOutputStream()
        override fun invalidate(destinationUri: String): Boolean = true
    }
}
