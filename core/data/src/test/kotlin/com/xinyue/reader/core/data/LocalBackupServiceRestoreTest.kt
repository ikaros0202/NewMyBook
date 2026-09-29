package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreConflictResolution
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalBackupServiceRestoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `confirmed opaque plan is published and consumed`() = runTest {
        val root = temporary.newFolder("private")
        val current = BackupCatalogSnapshot(
            globalSettings = BackupGlobalSettingsRecord(readerDataJson.encodeToString(ReaderSettings(fontSizeSp = 18f)), "{}", 1),
        )
        val backup = BackupCatalogSnapshot(
            globalSettings = BackupGlobalSettingsRecord(readerDataJson.encodeToString(ReaderSettings(fontSizeSp = 26f)), "{}", 2),
        )
        val archive = metadataArchive(backup)
        val registry = StagedRestorePlanRegistry()
        val database = FakeDatabase(current)
        val resolver = DefaultRestorePlanResolver()
        val journals = RestoreJournalStore(root)
        val snapshots = RestoreSnapshotStore(root)
        val publisher = RestorePublisher(root, database, resolver, journals, snapshots, freeBytes = { Long.MAX_VALUE })
        val recovery = RestoreRecovery(root, database, resolver, journals, snapshots, registry)
        val service = LocalBackupService(
            catalogDataSource = BackupCatalogDataSource { database.state },
            assetInventory = BackupAssetInventory(root),
            archiveWriter = SecureBackupWriter(BackupAssetInventory(root)),
            documentGateway = ReadGateway(archive.readBytes()),
            privateRoot = root,
            appVersion = "test",
            nowEpochMillis = { 1 },
            idFactory = { "restore-token" },
            stagedPlans = registry,
            restorePublisher = publisher,
            restoreRecovery = recovery,
        )
        val inspected = service.inspect("content://redacted") as BackupInspectResult.Success
        val conflict = inspected.preview.conflicts.single()

        val result = service.restore(
            RestoreRequest(
                inspected.preview.stagedPlanToken,
                RestoreMode.MERGE,
                listOf(RestoreConflictResolution(conflict.id, RestoreConflictChoice.USE_BACKUP)),
            ),
        )

        assertThat(result).isInstanceOf(RestoreResult.Success::class.java)
        assertThat(database.state.globalSettings?.updatedAtEpochMillis).isEqualTo(2)
        assertThat(registry.sizeForTest()).isEqualTo(0)
        assertThat(File(root, "backup-staging").listFiles().orEmpty()).isEmpty()
    }

    private fun metadataArchive(snapshot: BackupCatalogSnapshot): File {
        val sourceRoot = temporary.newFolder("source")
        val inventory = BackupAssetInventory(sourceRoot)
        val collected = inventory.collect(snapshot, BackupOptions(false, false))
        return temporary.newFile("restore.xinyuebackup").also { archive ->
            kotlinx.coroutines.runBlocking {
                SecureBackupWriter(inventory).write(
                    archive, collected.catalog, collected.assets, BackupOptions(false, false), "test", 1,
                )
            }
        }
    }

    private class FakeDatabase(initial: BackupCatalogSnapshot) : RestoreDatabaseGateway {
        var state = initial
        override suspend fun snapshot(): BackupCatalogSnapshot = state
        override suspend fun replaceAll(snapshot: BackupCatalogSnapshot) { state = snapshot }
    }

    private class ReadGateway(private val bytes: ByteArray) : BackupDocumentGateway {
        override fun openForRead(sourceUri: String): InputStream = ByteArrayInputStream(bytes)
        override fun querySize(sourceUri: String): Long = bytes.size.toLong()
        override fun openForWrite(destinationUri: String): OutputStream = ByteArrayOutputStream()
        override fun invalidate(destinationUri: String): Boolean = true
    }
}
