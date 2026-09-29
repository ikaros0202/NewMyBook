package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.RestoreAssetAction
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestorePlan
import com.xinyue.reader.core.domain.model.RestorePreview
import com.xinyue.reader.core.domain.model.RestoreRequest
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreRecoveryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `process death before durable commit phase rolls database and files back idempotently`() = runTest {
        for (point in listOf(RestoreFaultPoint.AFTER_FIRST_FILE_PUBLISHED, RestoreFaultPoint.AFTER_DATABASE_COMMIT)) {
            val fixture = fixture("rollback-${point.name}", point)
            assertThat(runCatching { fixture.publisher.publish(fixture.prepared, fixture.request) }.exceptionOrNull())
                .isInstanceOf(SimulatedProcessDeath::class.java)

            fixture.recovery.reconcile()
            fixture.recovery.reconcile()

            assertThat(fixture.database.state).isEqualTo(fixture.oldCatalog)
            assertThat(fixture.target.readText()).isEqualTo("old-public-content")
            assertThat(fixture.journals.list()).isEmpty()
            assertThat(File(fixture.root, "restore-snapshots").listFiles().orEmpty()).isEmpty()
        }
    }

    @Test
    fun `committed unverified process death verifies and finishes exact new state`() = runTest {
        val fixture = fixture("committed", RestoreFaultPoint.DURING_VERIFICATION)
        assertThat(runCatching { fixture.publisher.publish(fixture.prepared, fixture.request) }.exceptionOrNull())
            .isInstanceOf(SimulatedProcessDeath::class.java)

        fixture.recovery.reconcile()
        fixture.recovery.reconcile()

        assertThat(fixture.database.state).isEqualTo(fixture.newCatalog)
        assertThat(fixture.target.readText()).isEqualTo("new-public-content")
        assertThat(fixture.journals.list()).isEmpty()
        assertThat(fixture.prepared.staged.stagingRoot.exists()).isFalse()
    }

    @Test
    fun `cleanup-pending process death preserves new state and finishes cleanup`() = runTest {
        val fixture = fixture("cleanup", RestoreFaultPoint.DURING_CLEANUP)
        assertThat(runCatching { fixture.publisher.publish(fixture.prepared, fixture.request) }.exceptionOrNull())
            .isInstanceOf(SimulatedProcessDeath::class.java)

        fixture.recovery.reconcile()
        fixture.recovery.reconcile()

        assertThat(fixture.database.state).isEqualTo(fixture.newCatalog)
        assertThat(fixture.target.readText()).isEqualTo("new-public-content")
        assertThat(fixture.journals.list()).isEmpty()
    }

    private fun fixture(name: String, deathPoint: RestoreFaultPoint): Fixture {
        val root = temporaryFolder.newFolder("private-$name")
        val target = File(root, "books/book-1/content.txt").also { it.parentFile!!.mkdirs(); it.writeText("old-public-content") }
        val stagingRoot = File(root, "backup-staging/import-operation-1").also { it.mkdirs() }
        val extraction = File(stagingRoot, "extracted").also { it.mkdirs() }
        val sourcePath = "assets/books/book-1/content.txt"
        val source = File(extraction, sourcePath).also { it.parentFile!!.mkdirs(); it.writeText("new-public-content") }
        val hash = sha256(source)
        val manifest = BackupManifest(
            appVersion = "1.2-test", createdAtEpochMillis = 1, options = BackupOptions(),
            entries = listOf(BackupManifestEntry(sourcePath, BackupEntryKind.NORMALIZED_TEXT, source.length(), hash)),
        )
        val archive = File(stagingRoot, "archive.xinyuebackup").also { it.writeText("archive") }
        val oldCatalog = BackupCatalogSnapshot(globalSettings = BackupGlobalSettingsRecord("{\"fontSizeSp\":20}", "{}", 1))
        val newCatalog = BackupCatalogSnapshot(globalSettings = BackupGlobalSettingsRecord("{\"fontSizeSp\":30}", "{}", 2))
        val action = RestoreAssetAction(sourcePath, "books/book-1/content.txt", BackupEntryKind.NORMALIZED_TEXT, source.length(), hash)
        val plan = RestorePlan(
            "operation-1", emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), listOf(action), emptyList(),
            RestorePreview("operation-1", 1, 1, BackupOptions(), 1, 0, 0, 100, source.length(), 100, 20_000_000, emptyList()),
        )
        val staged = StagedBackup(stagingRoot, archive, extraction, manifest, mapOf(sourcePath to source), archive.length(), source.length())
        val prepared = PreparedRestorePlan(plan, staged, newCatalog, oldCatalog)
        StagedRestorePlanRegistry().retain(prepared)
        val resolved = ResolvedRestore(newCatalog, listOf(action), 1, 0, 0)
        val resolver = RestorePlanResolver { _, _ -> resolved }
        val database = RecoveryDatabase(oldCatalog)
        val journals = RestoreJournalStore(root)
        val snapshots = RestoreSnapshotStore(root)
        val publisher = RestorePublisher(
            root, database, resolver, journals, snapshots,
            RestoreFaultInjector { if (it == deathPoint) throw SimulatedProcessDeath() },
            freeBytes = { Long.MAX_VALUE },
        )
        val recovery = RestoreRecovery(root, database, resolver, journals, snapshots, StagedRestorePlanRegistry())
        return Fixture(root, target, oldCatalog, newCatalog, prepared, RestoreRequest("operation-1", RestoreMode.MERGE, emptyList()), database, journals, publisher, recovery)
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private class RecoveryDatabase(initial: BackupCatalogSnapshot) : RestoreDatabaseGateway {
        var state = initial
        override suspend fun snapshot() = state
        override suspend fun replaceAll(snapshot: BackupCatalogSnapshot) { state = snapshot }
    }

    private class SimulatedProcessDeath : Error()

    private data class Fixture(
        val root: File,
        val target: File,
        val oldCatalog: BackupCatalogSnapshot,
        val newCatalog: BackupCatalogSnapshot,
        val prepared: PreparedRestorePlan,
        val request: RestoreRequest,
        val database: RecoveryDatabase,
        val journals: RestoreJournalStore,
        val publisher: RestorePublisher,
        val recovery: RestoreRecovery,
    )
}
