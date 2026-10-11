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
import com.xinyue.reader.core.domain.model.RestoreResult
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestorePublisherTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `path only database changes are compensated immediately and on startup`() = runTest {
        for (failRollback in listOf(false, true)) {
            val fixture = fixture("path-only-$failRollback", RestoreFaultPoint.AFTER_DATABASE_COMMIT, pathOnlyChange = true)
            fixture.database.failRollbackOnce = failRollback
            assertThat(BackupCatalogDigest.sha256(fixture.oldCatalog)).isEqualTo(BackupCatalogDigest.sha256(fixture.newCatalog))
            fixture.publisher.publish(fixture.prepared, fixture.request)
            if (failRollback) {
                RestoreRecovery(
                    fixture.root, fixture.database, RestorePlanResolver { _, _ -> error("must roll back") },
                    fixture.journals, RestoreSnapshotStore(fixture.root), StagedRestorePlanRegistry(),
                ).reconcile()
            }
            assertThat(fixture.database.state.bookSources).isEqualTo(fixture.oldCatalog.bookSources)
            assertThat(fixture.journals.list()).isEmpty()
        }
    }

    @Test
    fun `every pre-verification failure converges to exact old database and file state`() = runTest {
        val points = listOf(
            RestoreFaultPoint.AFTER_PLAN_CONFIRMED,
            RestoreFaultPoint.AFTER_SNAPSHOT_CATALOG,
            RestoreFaultPoint.AFTER_EACH_SNAPSHOT_FILE,
            RestoreFaultPoint.AFTER_JOURNAL_PREPARED,
            RestoreFaultPoint.AFTER_FIRST_FILE_PUBLISHED,
            RestoreFaultPoint.AFTER_ALL_FILES_PUBLISHED,
            RestoreFaultPoint.BEFORE_DATABASE_TRANSACTION,
            RestoreFaultPoint.DURING_DATABASE_TRANSACTION,
            RestoreFaultPoint.AFTER_DATABASE_COMMIT,
            RestoreFaultPoint.DURING_VERIFICATION,
        )

        points.forEach { point ->
            val fixture = fixture(point.name.lowercase(), point)
            val result = fixture.publisher.publish(fixture.prepared, fixture.request)

            assertThat(result).isInstanceOf(RestoreResult.Failure::class.java)
            assertThat(fixture.database.state).isEqualTo(fixture.oldCatalog)
            assertThat(fixture.target.readText()).isEqualTo("old-public-content")
            assertThat(fixture.journals.list()).isEmpty()
            assertThat(File(fixture.root, "restore-snapshots").listFiles().orEmpty()).isEmpty()
            assertThat(fixture.prepared.staged.stagingRoot.exists()).isFalse()
        }
    }

    @Test
    fun `failed database compensation retains evidence and startup retries the rollback`() = runTest {
        val fixture = fixture("rollback-retry", RestoreFaultPoint.AFTER_DATABASE_COMMIT)
        fixture.database.failRollbackOnce = true

        val result = fixture.publisher.publish(fixture.prepared, fixture.request)

        assertThat(result).isInstanceOf(RestoreResult.Failure::class.java)
        assertThat((result as RestoreResult.Failure).error.message).contains("回滚资料已保留")
        assertThat(fixture.database.state).isEqualTo(fixture.newCatalog)
        assertThat(fixture.target.readText()).isEqualTo("new-public-content")
        assertThat(fixture.journals.read("operation-1")!!.phase).isEqualTo(RestorePhase.ROLLBACK_PENDING)
        assertThat(fixture.prepared.staged.stagingRoot.exists()).isTrue()
        assertThat(File(fixture.root, "restore-snapshots/operation-1/snapshot.json").isFile).isTrue()

        val recovery = RestoreRecovery(
            fixture.root, fixture.database, RestorePlanResolver { _, _ -> error("must roll back") },
            fixture.journals, RestoreSnapshotStore(fixture.root), StagedRestorePlanRegistry(),
        )
        recovery.reconcile()
        recovery.reconcile()

        assertThat(fixture.database.state).isEqualTo(fixture.oldCatalog)
        assertThat(fixture.target.readText()).isEqualTo("old-public-content")
        assertThat(fixture.journals.list()).isEmpty()
    }

    @Test
    fun `cancellation after database commit compensates in a non cancellable context`() = runTest {
        lateinit var publishing: Job
        val fixture = fixture("cancel-commit", onFault = { point ->
            if (point == RestoreFaultPoint.AFTER_DATABASE_COMMIT) {
                publishing.cancel()
                throw CancellationException("cancelled restore")
            }
        })
        publishing = launch { fixture.publisher.publish(fixture.prepared, fixture.request) }
        publishing.join()

        assertThat(fixture.database.state).isEqualTo(fixture.oldCatalog)
        assertThat(fixture.target.readText()).isEqualTo("old-public-content")
        assertThat(fixture.journals.list()).isEmpty()
    }

    @Test
    fun `success commits exact new database and file then removes operational residue`() = runTest {
        val fixture = fixture("success")

        val result = fixture.publisher.publish(fixture.prepared, fixture.request)

        assertThat(result).isInstanceOf(RestoreResult.Success::class.java)
        assertThat(fixture.database.state).isEqualTo(fixture.newCatalog)
        assertThat(fixture.target.readText()).isEqualTo("new-public-content")
        assertThat(fixture.journals.list()).isEmpty()
        assertThat(File(fixture.root, "restore-snapshots").listFiles().orEmpty()).isEmpty()
        assertThat(fixture.prepared.staged.stagingRoot.exists()).isFalse()
    }

    @Test
    fun `cleanup interruption keeps complete new state and durable cleanup-pending journal`() = runTest {
        val fixture = fixture("cleanup", RestoreFaultPoint.DURING_CLEANUP)

        val result = fixture.publisher.publish(fixture.prepared, fixture.request)

        assertThat(result).isInstanceOf(RestoreResult.Success::class.java)
        assertThat(fixture.database.state).isEqualTo(fixture.newCatalog)
        assertThat(fixture.target.readText()).isEqualTo("new-public-content")
        assertThat(fixture.journals.read("operation-1")!!.phase).isEqualTo(RestorePhase.CLEANUP_PENDING)
    }

    @Test
    fun `overwrite deletion is snapshotted removed on success and restored on failure`() = runTest {
        val success = fixture("delete-success", deleteOldAsset = true)
        assertThat(success.oldOnlyTarget.exists()).isTrue()
        assertThat(success.publisher.publish(success.prepared, success.request)).isInstanceOf(RestoreResult.Success::class.java)
        assertThat(success.oldOnlyTarget.exists()).isFalse()

        val failed = fixture(
            "delete-failure",
            fault = RestoreFaultPoint.AFTER_ALL_FILES_PUBLISHED,
            deleteOldAsset = true,
        )
        assertThat(failed.publisher.publish(failed.prepared, failed.request)).isInstanceOf(RestoreResult.Failure::class.java)
        assertThat(failed.oldOnlyTarget.readText()).isEqualTo("old-only-public-content")
    }

    private fun fixture(
        name: String,
        fault: RestoreFaultPoint? = null,
        deleteOldAsset: Boolean = false,
        onFault: (RestoreFaultPoint) -> Unit = {},
        pathOnlyChange: Boolean = false,
    ): Fixture {
        val root = temporaryFolder.newFolder("private-$name")
        val target = File(root, "books/book-1/content.txt").also { it.parentFile!!.mkdirs(); it.writeText("old-public-content") }
        val oldOnlyTarget = File(root, "books/old-only/content.txt").also {
            it.parentFile!!.mkdirs(); it.writeText("old-only-public-content")
        }
        val stagingRoot = File(root, "backup-staging/import-operation-1").also { it.mkdirs() }
        val extraction = File(stagingRoot, "extracted").also { it.mkdirs() }
        val sourcePath = "assets/books/book-1/content.txt"
        val source = File(extraction, sourcePath).also { it.parentFile!!.mkdirs(); it.writeText("new-public-content") }
        val hash = sha256(source.readBytes())
        val manifest = BackupManifest(
            appVersion = "1.2-test", createdAtEpochMillis = 1, options = BackupOptions(),
            entries = listOf(BackupManifestEntry(sourcePath, BackupEntryKind.NORMALIZED_TEXT, source.length(), hash)),
        )
        val archive = File(stagingRoot, "archive.xinyuebackup").also { it.writeText("archive") }
        val oldCatalog = BackupCatalogSnapshot(globalSettings = BackupGlobalSettingsRecord("{\"fontSizeSp\":20}", "{}", 1),
            bookSources = if (pathOnlyChange) listOf(BackupBookSource("book-1", "books/old-generation/original.txt", "books/old-generation/content.txt", "books/old-generation/offsets.xidx", null)) else emptyList())
        val newCatalog = if (pathOnlyChange) oldCatalog.copy(bookSources = listOf(BackupBookSource("book-1", "books/book-1/original.txt", "books/book-1/content.txt", "books/book-1/offsets.xidx", null)))
            else BackupCatalogSnapshot(globalSettings = BackupGlobalSettingsRecord("{\"fontSizeSp\":30}", "{}", 2))
        val action = RestoreAssetAction(sourcePath, "books/book-1/content.txt", BackupEntryKind.NORMALIZED_TEXT, source.length(), hash)
        val plan = RestorePlan(
            stagedPlanToken = "operation-1",
            bookIdRemap = emptyMap(), groupIdRemap = emptyMap(), themeIdRemap = emptyMap(), fontIdRemap = emptyMap(),
            entityActions = emptyList(), assetActions = listOf(action), conflicts = emptyList(),
            preview = RestorePreview("operation-1", 1, 1, BackupOptions(), 1, 0, 0, 100, source.length(), 100, 20_000_000, emptyList()),
        )
        val staged = StagedBackup(stagingRoot, archive, extraction, manifest, mapOf(sourcePath to source), archive.length(), source.length())
        val prepared = PreparedRestorePlan(plan, staged, newCatalog, oldCatalog)
        val resolved = ResolvedRestore(
            newCatalog,
            listOf(action),
            1,
            0,
            0,
            deleteTargetRelativePaths = if (deleteOldAsset) listOf("books/old-only/content.txt") else emptyList(),
        )
        val database = FakeRestoreDatabaseGateway(oldCatalog, fault)
        val injector = RestoreFaultInjector { point ->
            onFault(point)
            if (point == fault && point != RestoreFaultPoint.DURING_DATABASE_TRANSACTION) throw IOException("injected $point")
        }
        val journals = RestoreJournalStore(root)
        val publisher = RestorePublisher(
            privateRoot = root,
            database = database,
            resolver = RestorePlanResolver { _, _ -> resolved },
            journalStore = journals,
            snapshotStore = RestoreSnapshotStore(root),
            faultInjector = injector,
            freeBytes = { Long.MAX_VALUE },
        )
        return Fixture(root, target, oldOnlyTarget, oldCatalog, newCatalog, prepared, RestoreRequest("operation-1", RestoreMode.MERGE, emptyList()), database, journals, publisher)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class FakeRestoreDatabaseGateway(
        private val initial: BackupCatalogSnapshot,
        private val fault: RestoreFaultPoint?,
    ) : RestoreDatabaseGateway {
        var state = initial
        var failRollbackOnce = false
        override suspend fun snapshot(): BackupCatalogSnapshot = state
        override suspend fun replaceAll(snapshot: BackupCatalogSnapshot) {
            currentCoroutineContext().ensureActive()
            if (snapshot == initial && failRollbackOnce) {
                failRollbackOnce = false
                throw IOException("rollback temporarily unavailable")
            }
            if (fault == RestoreFaultPoint.DURING_DATABASE_TRANSACTION) throw IOException("transaction rolled back")
            state = snapshot
        }
    }

    private data class Fixture(
        val root: File,
        val target: File,
        val oldOnlyTarget: File,
        val oldCatalog: BackupCatalogSnapshot,
        val newCatalog: BackupCatalogSnapshot,
        val prepared: PreparedRestorePlan,
        val request: RestoreRequest,
        val database: FakeRestoreDatabaseGateway,
        val journals: RestoreJournalStore,
        val publisher: RestorePublisher,
    )
}
